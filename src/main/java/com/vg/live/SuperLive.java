package com.vg.live;

import static com.github.davidmoten.rx.Checked.f1;
import static com.github.davidmoten.rx.Checked.f2;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.primitives.Ints.checkedCast;
import static com.vg.live.DashUtil.createMovieHeader;
import static com.vg.live.MP4Segment.fillArrayIfNull;
import static com.vg.live.video.ADTSHeader.adtsFromAudioSampleEntry;
import static com.vg.live.video.MP4MuxerUtils.concatMapOnFirst;
import static com.vg.live.video.MP4MuxerUtils.populateSpsPps;
import static com.vg.live.video.RxDash.createTrack;
import static com.vg.live.video.RxDash.m4sFromFrames;
import static com.vg.live.video.RxDash.trex;
import static com.vg.util.MediaSeq.mediaSeq;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.toHexString;
import static java.lang.System.identityHashCode;
import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jcodec.common.io.ByteBufferSeekableByteChannel.readFromByteBuffer;
import static org.jcodec.containers.mp4.boxes.MovieExtendsBox.createMovieExtendsBox;
import static org.jcodec.containers.mp4.boxes.MovieExtendsHeaderBox.createMovieExtendsHeaderBox;
import static rx.Observable.just;
import static rx.Observable.range;
import static rx.schedulers.Schedulers.newThread;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.containers.mp4.MP4Util;
import org.jcodec.containers.mp4.boxes.AudioSampleEntry;
import org.jcodec.containers.mp4.boxes.Box;
import org.jcodec.containers.mp4.boxes.FileTypeBox;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.MovieExtendsBox;
import org.jcodec.containers.mp4.boxes.NodeBox;
import org.jcodec.containers.mp4.boxes.TrackExtendsBox;
import org.jcodec.containers.mp4.boxes.TrakBox;
import org.jcodec.containers.mp4.boxes.TrunBox;
import org.jcodec.containers.mp4.boxes.VideoSampleEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.ws.WebSocket;
import com.vg.live.RxWebSocket.WebSocketEvent;
import com.vg.live.video.AVFrame;
import com.vg.live.video.RxDash;
import com.vg.live.worker.Allocator;
import com.vg.live.worker.SimpleAllocator;
import com.vg.util.MediaSeq;

import rx.Observable;
import rx.Subscription;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;

public class SuperLive {
    private static final int VIDEO_TRACKID = 1;
    private static final int AUDIO_TRACKID = 2;
    //max number of dash chunks buffered in memory
    private static final int MAX_DASH_BUFFERS = 128;
    private static final int DEADBEEF = 0xdeadbeef;
    static final Logger log = LoggerFactory.getLogger(SuperLive.class);
    public static final int FTYP = fourccToInt("ftyp");

    static void debug(String string) {
        log.debug(string);
    }

    private static void error(Object msg) {
        log.error("{}", msg);
    }

    public static ByteBuffer endOfStream() {
        ByteBuffer allocate = ByteBuffer.allocate(42);
        allocate.putInt(DEADBEEF);
        allocate.putInt(DEADBEEF);
        allocate.putInt(DEADBEEF);
        allocate.clear();
        return allocate;
    }

    public static boolean isEOF(ByteBuffer buf) {
        int remaining = buf.remaining();
        if (remaining == 42) {
            int pos = buf.position();
            int i0 = buf.getInt(pos);
            int i1 = buf.getInt(pos + 4);
            int i2 = buf.getInt(pos + 8);
            if (i0 == i1 && i1 == i2 && i2 == DEADBEEF) {
                return true;
            }
        }
        return false;
    }

    public static Observable<AVFrame> framesFromDashBuffers(Observable<ByteBuffer> dashBuffers) {
        Observable<ByteBuffer> autoConnect = dashBuffers.replay(1).autoConnect(2);

        Observable<MovieBox> _moov = autoConnect.filter(b -> isDashinit(b)).map(f1(bb -> {
            return MP4Util.parseMovieChannel(readFromByteBuffer(bb.duplicate()));
        }));

        Observable<Pair<MP4Segment, ByteBuffer>> _m4s = autoConnect.filter(b -> !isDashinit(b)).map(f1(bb -> {
            MP4Segment m4s = MP4Segment.parseM4S(readFromByteBuffer(bb.duplicate()));
            return Pair.of(m4s, bb);
        }));
        _moov = _moov.replay(1).autoConnect();

        Observable<List<AVFrame>> combineLatest = _m4s.zipWith(_moov.take(1).repeat(), f2((pair, moov) -> {
            log.debug("{} moov@{}", pair, toHexString(identityHashCode(moov)));
            TrackExtendsBox[] trexs = NodeBox.findAllPath(moov, TrackExtendsBox.class, Box.path("mvex.trex"));

            MP4Segment m4s = pair.getKey();

            ByteBuffer data = pair.getValue();

            List<AVFrame> frames = new ArrayList<>();
            for (TrakBox track : moov.getTracks()) {
                VideoSampleEntry vse = null;
                AudioSampleEntry ase = null;
                if (track.isVideo()) {
                    vse = (VideoSampleEntry) track.getSampleEntries()[0];
                } else if (track.isAudio()) {
                    ase = (AudioSampleEntry) track.getSampleEntries()[0];
                }

                int trackId = track.getTrackHeader().getTrackId();
                TrunBox trun = m4s.getTrun(trackId);
                TrackExtendsBox trex = trex(trexs, trackId);

                if (trun == null) {
                    log.error("Ignore frames: trackId={}", trackId);
                    continue;
                }

                long pts = m4s.getBaseMediaDecodeTime(trackId);
                List<AVFrame> _frames = fromTrun(track.isVideo(), trun, trex, data);
                for (AVFrame f : _frames) {
                    f.pts = pts;
                    f.timescale = track.getTimescale();
                    if (track.isVideo()) {
                        populateSpsPps(vse, f);
                    } else if (track.isAudio()) {
                        f.adtsHeader = adtsFromAudioSampleEntry(ase);
                    }
                    pts += f.duration;
                }
                frames.addAll(_frames);
            }

            return frames;
        }));
        Observable<AVFrame> concatMap = combineLatest.concatMap(list -> Observable.from(list));
        return concatMap;
    }

    static List<AVFrame> fromTrun(boolean video, TrunBox trun, TrackExtendsBox trex, ByteBuffer data) {
        int sampleCount = checkedCast(trun.getSampleCount());

        // 68: styp + sidx
        int voff = trun.getDataOffset() + 68;
        List<AVFrame> _frames = new ArrayList<>();
        int[] sampleDurations = fillArrayIfNull(trun.getSampleDurations(), sampleCount, trex.getDefaultSampleDuration());
        int[] samplesFlags = fillArrayIfNull(trun.getSamplesFlags(), sampleCount, trex.getDefaultSampleFlags());

        for (int i = 0; i < sampleCount; i++) {
            int sampleSize = checkedCast(trun.getSampleSize(i));
            int sampleDuration = sampleDurations[i];
            int sampleFlags = samplesFlags[i];
            boolean iframe = (sampleFlags & 0x02000000) != 0;
            AVFrame f = video ? AVFrame.video(voff, sampleSize, iframe) : AVFrame.audio(voff, sampleSize);
            f.duration = sampleDuration;

            if (data.capacity() < voff + sampleSize) {
                log.error("Ignore frames: bb capacity={} position={}", data.capacity(), (voff + sampleSize));
                continue;
            }

            data.limit(voff + sampleSize);
            data.position(voff);
            f._data = data.slice();
            _frames.add(f);
            voff += sampleSize;
        }
        return _frames;
    }

    private static TrackExtendsBox trex(TrackExtendsBox[] trex, int trackId) {
        for (TrackExtendsBox t : trex) {
            if (t.getTrackId() == trackId) {
                return t;
            }
        }
        return null;
    }

    public static boolean isDashinit(ByteBuffer bb) {
        ByteBuffer _bb = bb.duplicate();
        return _bb.remaining() > 8 && FTYP == _bb.order(BIG_ENDIAN).getInt(_bb.position() + 4);
    }

    public static Observable<ByteBuffer> videoDashBuffers(Observable<AVFrame> frames, Allocator alloc) {
        return dashBuffers(frames.buffer(5), alloc);
    }

    public static Observable<ByteBuffer> sendSuperLiveFrames(OkHttpClient c, String url, Observable<AVFrame> frames, Allocator alloc) {
        Observable<WebSocketEvent> ws = RxWebSocket
                .webSocket(c, GET(url))
                .doOnSubscribe(() -> log.debug("ws subscribe"))
                .doOnNext(x -> {
                    log.debug("from ws {} {}", x.type, x.message);
                })
                .retryWhen(e -> e.doOnNext(err -> log.error("retry {}", err.toString())).delay(1, SECONDS))
                .repeatWhen(e -> e.doOnNext(err -> log.error("repeat {}", err.toString())).delay(1, SECONDS));

        Observable<WebSocket> _ws = ws.map(e -> e.ws).distinctUntilChanged().replay(1).autoConnect();

        Observable<ByteBuffer> dashbufs = videoDashBuffers(frames, alloc)
                .concatWith(Observable.just(endOfStream()))
                .subscribeOn(newThread());

        dashbufs = dashbufs.concatMap(buf -> {
            log.trace("buf2 {}", buf);
            Observable<ByteBuffer> sendBuf = RxWebSocket.sendBuf(_ws, buf);
            sendBuf = sendBuf.timeout(1, SECONDS);
            sendBuf = sendBuf.retryWhen(errors -> errors.concatMap(err -> {
                log.error("retry send because {}", err.toString());
                return Observable.just("x").delay(1, SECONDS);
            }));
            return sendBuf.subscribeOn(newThread());
        });

        return dashbufs;
    }

    public static Observable<ByteBuffer> split64k(Observable<ByteBuffer> bufs, Allocator alloc) {
        return bufs.concatMap(x -> {
            if (x.remaining() <= 64 * 1024) {
                return Observable.just(x);
            }
            List<ByteBuffer> buffers = new ArrayList<>();
            int limit = x.limit();
            for (int pos = x.position(); pos < limit; pos += 64 * 1024) {
                int lim = Math.min(pos + 64 * 1024, limit);
                x.limit(lim);
                x.position(pos);
                buffers.add(alloc.copy(x.slice()));
            }
            alloc.release(x);
            return Observable.from(buffers);
        });
    }

    public static Observable<ByteBuffer> sendSuperLive(OkHttpClient c, String url, Observable<ByteBuffer> dashBuffers) {
        return sendSuperLive(c, url, dashBuffers, SimpleAllocator.DEFAULT_ALLOCATOR);
    }

    public static Observable<ByteBuffer> sendSuperLive(OkHttpClient c, String url, Observable<ByteBuffer> dashBuffers, Allocator allocator) {
        BehaviorSubject<WebSocket> lastKnownGoodWebSocket = BehaviorSubject.create();

        Subscription reconnectSubscription = RxWebSocket.reconnectWebSocket(c, url, 1000)
                .map(e -> e.ws)
                .distinctUntilChanged()
                .subscribeOn(newThread())
                .subscribe(ws -> {
                    log.debug("new ws {}", ws);
                    lastKnownGoodWebSocket.onNext(ws);
                }, err -> log.error("reconnectWebSocket {}", err.toString(), err));

        Observable<ByteBuffer> dashbufs = split64k(dashBuffers, allocator).concatWith(just(endOfStream())).subscribeOn(newThread());

        dashbufs = dashbufs.doOnUnsubscribe(() -> unsubscribe(reconnectSubscription));

        Observable<ByteBuffer> sent = dashbufs.concatMap(buf -> RxWebSocket.sendBuf(lastKnownGoodWebSocket, buf, 2000).subscribeOn(newThread()));

        return sent;
    }

    public static Observable<ByteBuffer> dashBuffers(Observable<List<AVFrame>> lists) {
        return dashBuffers(lists, SimpleAllocator.DEFAULT_ALLOCATOR);
    }
    
    //dashinit + dashchunk[0-9].m4s
    public static Observable<ByteBuffer> dashBuffers(Observable<List<AVFrame>> lists, Allocator allocator) {
        Observable<MediaSeq<List<AVFrame>>> _mseq = lists.filter(list -> list != null && !list.isEmpty()).zipWith(range(1, MAX_VALUE), (s, mseq) -> mediaSeq(mseq, s));
        return _mseq.concatMap(new Func1<MediaSeq<List<AVFrame>>, Observable<? extends ByteBuffer>>() {
            private MovieBox moov;
            private MovieExtendsBox mvex;

            @Override
            public Observable<? extends ByteBuffer> call(MediaSeq<List<AVFrame>> x) {
                int mseq = x.mseq;
                List<AVFrame> frameList = x.value;

                if (moov == null) {
                    moov = MovieBox.createMovieBox();
                    int timescale = frameList.stream().mapToInt(f -> checkedCast(f.timescale)).findFirst().orElse(0);
                    checkState(timescale > 0, "BUG: no timescale for frame list %s", frameList);
                    moov.add(createMovieHeader(timescale, 3));
                    mvex = createMovieExtendsBox();
                    mvex.add(createMovieExtendsHeaderBox());
                    moov.add(mvex);
                }

                boolean hasAudio = frameList.stream().anyMatch(f -> f.isAudio());
                boolean hasVideo = frameList.stream().anyMatch(f -> f.isVideo());
                boolean updateVideo = hasVideo && moov.getVideoTrack() == null;
                boolean updateAudio = hasAudio && moov.getAudioTracks().isEmpty();

                if (updateVideo) {
                    AVFrame video = frameList.stream().filter(f -> f.isVideo()).findFirst().orElse(null);
                    checkNotNull(video, "BUG: video frame not found in frame list %s", frameList);
                    mvex.add(RxDash.trex(VIDEO_TRACKID));
                    moov.add(createTrack(VIDEO_TRACKID, video));
                }

                if (updateAudio) {
                    AVFrame audio = frameList.stream().filter(f -> f.isAudio()).findFirst().orElse(null);
                    checkNotNull(audio, "BUG: audio frame not found in frame list %s", frameList);
                    mvex.add(RxDash.trex(AUDIO_TRACKID));
                    moov.add(createTrack(AUDIO_TRACKID, audio));
                }

                if (updateVideo || updateAudio) {
                    FileTypeBox ftyp = RxDash.dashFtyp();
                    ByteBuffer dashinit = allocator.acquire(4096);
                    ftyp.write(dashinit);
                    moov.write(dashinit);
                    dashinit.flip();
                    return just(dashinit, m4sFromFrames(frameList, mseq, allocator));
                }
                return just(m4sFromFrames(frameList, mseq, allocator));
            }
        });
    }

    //dashinit not returned
    public static Observable<ByteBuffer> dashChunks(Observable<List<AVFrame>> lists, Allocator allocator) {
        Observable<MediaSeq<List<AVFrame>>> _mseq = lists.filter(list -> list != null && !list.isEmpty()).zipWith(range(1, MAX_VALUE), (s, mseq) -> mediaSeq(mseq, s));
        return _mseq.concatMap(mediaSeq -> {
            ByteBuffer m4s = m4sFromFrames(mediaSeq.value, mediaSeq.mseq, allocator);
            for(AVFrame f : mediaSeq.value) {
                allocator.releaseData(f);
            }
            return Observable.just(m4s);
        });
    }


    private static void unsubscribe(Subscription sub) {
        if (sub != null && !sub.isUnsubscribed()) {
            sub.unsubscribe();
        }
    }

    public static Request GET(String url) {
        return new Request.Builder().url(url).build();
    }

    private static int fourccToInt(String string) {
        byte[] bytes = string.getBytes();
        return bytes[0] << 24 | bytes[1] << 16 | bytes[2] << 8 | bytes[3];
    }
}
