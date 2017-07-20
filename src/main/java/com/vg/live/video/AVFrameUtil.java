package com.vg.live.video;

import static com.github.davidmoten.rx.Transformers.orderedMergeWith;
import static com.github.davidmoten.rx.Transformers.toListWhile;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.concat;
import static com.github.davidmoten.rx.Transformers.bufferUntil;

import static com.vg.util.TimeUtil.convertTimescale;
import static com.vg.util.TimeUtil.hmsms;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jcodec.codecs.h264.H264Utils;

import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.Subject;
import rx.subjects.UnicastSubject;

public class AVFrameUtil {

    public static AVFrame annexb2mp4(AVFrame f) {
        ByteBuffer data = f.data();
        if (f.isVideo() && data.getInt(data.position()) == 0x00000001) {
            H264Utils.encodeMOVPacket(data);
        }
        return f;
    }

    public static Observable<Boolean> splitByIframe(Observable<AVFrame> v, long time, TimeUnit timeunit) {
        return v.filter(new Func1<AVFrame, Boolean>() {
            final long maxDurationMsec = timeunit.toMillis(time);
            long durationMsec = 0;

            @Override
            public Boolean call(AVFrame cur) {
                checkArgument(cur.timescale > 0, "timescale == 0 %s", cur);
                long curDurationMsec = convertTimescale(cur.duration, cur.timescale, 1000);
                durationMsec += curDurationMsec;
                if (cur.iframe && (durationMsec + curDurationMsec) >= maxDurationMsec) {
                    durationMsec = 0;
                    return true;
                }

                return false;
            }
        }).map(x -> true);
    }

    public static Observable<Observable<AVFrame>> segmentsByIframe(Observable<AVFrame> frames, long time, TimeUnit unit) {
        Observable<AVFrame> av = frames.publish().autoConnect(2);
        Observable<Boolean> split = splitByIframe(av.filter(f -> f.isVideo()), time, unit);
        return av.window(split).onBackpressureBuffer();
    }

    public static Observable<Observable<AVFrame>> segmentsByTime(Observable<AVFrame> frames, long time, TimeUnit unit) {
        return AVFrameUtil.splitBy2(frames, new Func2<AVFrame, AVFrame, Boolean>() {
            final long maxDurationMsec = unit.toMillis(time);
            long durationMsec = 0;

            @Override
            public Boolean call(AVFrame prev, AVFrame cur) {
                checkArgument(prev.timescale > 0);
                checkArgument(cur.timescale > 0);
                long prevDurationMsec = convertTimescale(prev.duration, prev.timescale, 1000);
                long curDurationMsec = convertTimescale(cur.duration, cur.timescale, 1000);
                durationMsec += prevDurationMsec;
                if (durationMsec + curDurationMsec >= maxDurationMsec) {
                    durationMsec = 0;
                    return true;
                }
                return false;
            }
        });
    }

    public static Transformer<AVFrame, AVFrame> interleaveAudioVideo(Observable<AVFrame> v) {
        return orderedMergeWith(v, (f1, f2) -> {
            long ts = Math.max(f1.timescale, f2.timescale);
            long pts1 = convertTimescale(f1.pts, f1.timescale, ts);
            long pts2 = convertTimescale(f2.pts, f2.timescale, ts);
            return Long.compare(pts1, pts2);
        });
    }

    public static Observable<Observable<AVFrame>> segments(Observable<AVFrame> g, boolean video) {
        Observable<Observable<AVFrame>> segments;
        if (video) {
            segments = segmentsByIframe(g, 2, SECONDS);
        } else {
            segments = segmentsByTime(g, 2, SECONDS);
        }
        return segments;
    }

    public static Observable<AVFrame> stripAAC(Observable<AVFrame> framesFromMp4, int firstFrames) {
        return framesFromMp4.groupBy(_f -> _f.isAudio()).flatMap(g -> {
            return g.getKey() ? g.skip(firstFrames) : g;
        });
    }

    public static Observable<AVFrame> hackPts(Observable<AVFrame> frames) {
        return frames.doOnNext(new Action1<AVFrame>() {
            long firstPts = -1;
            @Override
            public void call(AVFrame f) {
                if (firstPts == -1) {
                    firstPts = f.pts;
                }
                f.pts -= firstPts;
            }
        });
    }

    public static Observable<AVFrame> ptsFromDuration(Observable<AVFrame> av) {
        return av.groupBy(f -> f.isVideo()).flatMap(g -> {
            return g.scan((prev, cur) -> {
                cur.pts = prev.pts + prev.duration;
                return cur;
            });
        });
    }

    public static Observable<AVFrame> populateSpsPps(Observable<AVFrame> video) {
        return video.scan((prevFrame, curFrame) -> {
            if (prevFrame != null && curFrame.sps == null) {
                curFrame.sps = prevFrame.sps;
                curFrame.spsBuf = prevFrame.spsBuf;
            }
            if (prevFrame != null && curFrame.pps == null) {
                curFrame.pps = prevFrame.pps;
                curFrame.ppsBuf = prevFrame.ppsBuf;
            }
            return curFrame;
        });
    }

    public static Observable<AVFrame> skipUntilIframe(Observable<AVFrame> frames) {
        return AVFrameUtil.skipUntilIframe(frames, f -> System.err.println("release non I-FRAME " + f));
    }

    public static Observable<AVFrame> skipUntilIframe(Observable<AVFrame> frames, Action1<AVFrame> action) {
        return frames.skipWhile(f -> {
            boolean iframe = f.isIFrame();
            if (!iframe && action != null) {
                action.call(f);
            }
            return !iframe;
        });
    }

    public static Observable<AVFrame> filterNonAV(Observable<AVFrame> frames, Action1<AVFrame> action) {
        return frames.filter(f -> {
            boolean isav = f.isVideo() || f.adtsHeader != null;
            if (!isav && action != null) {
                action.call(f);
            }
            return isav;
        });
    }

    public static Observable<AVFrame> filterNonAV(Observable<AVFrame> frames) {
        return filterNonAV(frames, f -> System.err.println("non AV frame " + f));
    }

    public static Observable<AVFrame> populateDuration(Observable<AVFrame> frames) {
        return frames.buffer(2, 1).map(list -> {
            if (list.size() == 2) {
                AVFrame f1 = list.get(0);
                AVFrame f2 = list.get(1);
                long dts1 = f1.dts != null ? f1.dts : f1.pts;
                long dts2 = f2.dts != null ? f2.dts : f2.pts;
                f1.duration = Math.max(0, dts2 - dts1);
                f2.duration = f1.duration;
            }
            return list.get(0);
        });
    }

    public static Observable<List<AVFrame>> segments(Observable<AVFrame> frames, int minVideoFrames, int minAudioFrames) {
        return frames.compose(toListWhile((list, item) -> {
            int v = 0;
            int a = 0;
            for (AVFrame f : list) {
                v += f.isVideo() ? 1 : 0;
                a += f.isAudio() ? 1 : 0;
            }
    
            return v < minVideoFrames || a < minAudioFrames;
        }));
    }

    public static Observable<AVFrame> realtimeAV(Observable<AVFrame> frames) {
        return frames.groupBy(g -> g.isVideo()).flatMap(g -> {
            return g.concatMap(f -> {
                long usec = convertTimescale(f.duration, f.timescale, 1000000L);
                usec = (long) (usec - (usec * 0.161803398875 ));
                return Observable.just(f).delay(usec, TimeUnit.MICROSECONDS);
            });
        });
    }

    public static String printFrameTypes(List<AVFrame> x) {
        StringBuilder sb = new StringBuilder();
        for(AVFrame f : x) {
            if (f.isVideo()) {
                sb.append(f.iframe ? "I" : "V");
            }
            sb.append(f.type);
        }
        return sb.toString();
    }
    
    public static boolean hasVideo(List<AVFrame> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        for(AVFrame f : list) {
            if (f.isVideo()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Buffers frames for bufferTimeMsec OR until video frame is available
     * 
     * @param frames
     * @param bufferTimeMsec
     * @return
     */
    public static Observable<List<AVFrame>> bufferUntilHasVideo(Observable<AVFrame> frames, long bufferTimeMsec) {
        Observable<List<AVFrame>> buffer = frames.buffer(bufferTimeMsec, MILLISECONDS);
        Observable<List<List<AVFrame>>> compose = buffer.compose(bufferUntil(list -> {
            return hasVideo(list);
        }));
        buffer = compose.map(listlist -> {
            if (listlist.size() == 1) {
                return listlist.get(0);
            }
            return list(concat(listlist));
        }).filter(list -> !list.isEmpty());
        return buffer;
    }

    public static String framesToString(List<AVFrame> x) {
        String collect = printFrameTypes(x);
    
        long vduration = 0;
        long aduration = 0;
        for(AVFrame f : x) {
            if (f.isVideo()) {
                vduration += convertTimescale(f.duration, f.timescale, 1000L);
            } else if (f.isAudio()) {
                aduration += convertTimescale(f.duration, f.timescale, 1000L);
            }
        }
    
        return String.format("A: %s V: %s %s", hmsms(aduration), hmsms(vduration), collect);
    }
    
    static <T> List<T> list(Iterable<T> iterable) {
        if (iterable == null)
            return Collections.emptyList();
        if (iterable instanceof List)
            return (List<T>) iterable;
        List<T> list = new ArrayList<>();
        if (iterable != null) {
            for (T item : iterable) {
                list.add(item);
            }
        }
        return list;
    }

    static <T> Observable<Observable<T>> splitBy(Observable<T> interval, Func1<T, Boolean> predicate) {
        Subject[] cur = new Subject[1];
        Observable<Observable<T>> flatMap = interval.doOnCompleted(() -> {
            if (cur[0] != null) {
                cur[0].onCompleted();
            }
        }).concatMap(x -> {
            boolean output = false;
            if (cur[0] == null) {
                cur[0] = UnicastSubject.create();
                output = true;
            }
            if (predicate.call(x)) {
                cur[0].onNext(x);
                cur[0].onCompleted();
                cur[0] = UnicastSubject.create();
                output = true;
            } else {
                cur[0].onNext(x);
            }
            if (output) {
                return Observable.just((Observable<T>) cur[0]);
            } else {
                return Observable.empty();
            }
        });
        return flatMap;
    }

    static <T> Observable<Observable<T>> splitBy2(Observable<T> interval, Func2<T, T, Boolean> predicate) {
        Observable<List<T>> buffer = interval.buffer(2, 1);
        Observable<Observable<List<T>>> splitBy = splitBy(buffer, list -> {
            return list.size() == 2 && predicate.call(list.get(0), list.get(1));
        });
        Observable<Observable<T>> flatMap2 = splitBy.concatMap(o -> {
            Observable<T> map = o.map(list -> list.get(0));
            return Observable.just(map);
        });
        return flatMap2;
    }
}
