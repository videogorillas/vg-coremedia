package com.vg.live.video;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.primitives.Ints.checkedCast;
import static com.vg.live.DashUtil.createMovieHeader;
import static com.vg.live.DashUtil.default_matrix;
import static com.vg.live.DashUtil.emptyStbl;
import static com.vg.live.MP4Segment.mfhd;
import static com.vg.live.MP4Segment.sidx;
import static com.vg.live.MP4Segment.styp;
import static com.vg.live.video.MP4MuxerUtils.audioSampleEntry;
import static com.vg.live.video.MP4MuxerUtils.videoSampleEntry;
import static java.util.Arrays.asList;
import static org.jcodec.containers.mp4.boxes.Box.createLeafBox;
import static org.jcodec.containers.mp4.boxes.Header.createHeader;
import static org.jcodec.containers.mp4.boxes.MediaInfoBox.createMediaInfoBox;
import static org.jcodec.containers.mp4.boxes.MovieExtendsBox.createMovieExtendsBox;
import static org.jcodec.containers.mp4.boxes.MovieExtendsHeaderBox.createMovieExtendsHeaderBox;
import static org.jcodec.containers.mp4.boxes.MovieFragmentBox.createMovieFragmentBox;
import static org.jcodec.containers.mp4.boxes.SampleDescriptionBox.createSampleDescriptionBox;
import static org.jcodec.containers.mp4.boxes.TrackFragmentBaseMediaDecodeTimeBox.createTrackFragmentBaseMediaDecodeTimeBox;
import static org.jcodec.containers.mp4.boxes.TrackFragmentHeaderBox.createTrackFragmentHeaderBox;
import static org.jcodec.containers.mp4.boxes.TrackHeaderBox.createTrackHeaderBox;
import static org.jcodec.containers.mp4.boxes.VideoMediaHeaderBox.createVideoMediaHeaderBox;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jcodec.containers.mp4.TrackType;
import org.jcodec.containers.mp4.boxes.DataInfoBox;
import org.jcodec.containers.mp4.boxes.DataRefBox;
import org.jcodec.containers.mp4.boxes.Edit;
import org.jcodec.containers.mp4.boxes.FileTypeBox;
import org.jcodec.containers.mp4.boxes.HandlerBox;
import org.jcodec.containers.mp4.boxes.Header;
import org.jcodec.containers.mp4.boxes.MediaBox;
import org.jcodec.containers.mp4.boxes.MediaHeaderBox;
import org.jcodec.containers.mp4.boxes.MediaInfoBox;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.MovieExtendsBox;
import org.jcodec.containers.mp4.boxes.MovieFragmentBox;
import org.jcodec.containers.mp4.boxes.NodeBox;
import org.jcodec.containers.mp4.boxes.SampleDescriptionBox;
import org.jcodec.containers.mp4.boxes.SampleEntry;
import org.jcodec.containers.mp4.boxes.TrackExtendsBox;
import org.jcodec.containers.mp4.boxes.TrackFragmentBox;
import org.jcodec.containers.mp4.boxes.TrackFragmentHeaderBox;
import org.jcodec.containers.mp4.boxes.TrackHeaderBox;
import org.jcodec.containers.mp4.boxes.TrakBox;
import org.jcodec.containers.mp4.boxes.TrunBox;
import org.jcodec.containers.mp4.boxes.TrunBox.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vg.live.MP4Segment;
import com.vg.live.worker.Allocator;

public class RxDash {
    private final static Logger log = LoggerFactory.getLogger(RxDash.class);

    public static ByteBuffer dashinitFromFrame(AVFrame f) {
        return dashinit(f.isVideo() ? f : null, f.isAudio() ? f : null);
    }

    public static ByteBuffer dashinit(List<AVFrame> frames) {
        AVFrame video=null;
        AVFrame audio=null;
        for (AVFrame f : frames) {
            if (f.isVideo()) {
                video = f;
            } else if (f.isAudio()) {
                audio = f;
            }
            if (audio != null && video != null) {
                break;
            }
        }
        return dashinit(video, audio);
    }

    public static ByteBuffer dashinit(AVFrame video, AVFrame audio) {
        log.trace("dashinit() v: {} a: {}", video, audio);
        MovieBox moov = dashinitMoov(video, audio);
        FileTypeBox ftyp = dashFtyp();
        ByteBuffer buf = ByteBuffer.allocate(4096);
        ftyp.write(buf);
        moov.write(buf);
        buf.flip();
        return buf;
    }

    private static FileTypeBox dashFtyp() {
        return FileTypeBox.createFileTypeBox("iso5", 1, Arrays.asList("avc1", "iso5", "mpd"));
    }

    public static int iframeIndex(int[] sampleFlags) {
        if (sampleFlags == null) {
            return -1;
        }
        for (int i = 0; i < sampleFlags.length; i++) {
            int flags = sampleFlags[i];
            if ((flags & 0x02000000) != 0) {
                return i;
            }
        }
        return -1;
    }

    static long duration(List<AVFrame> list) {
        long duration = 0;
        for (AVFrame f : list) {
            duration += f.duration;
        }
        return duration;
    }

    public static TrunBox trun(List<AVFrame> list) {
        int sampleCount = list.size();
        int[] sampleSizes = new int[list.size()];
        int[] sampleFlags = new int[list.size()];
        int[] sampleDurations = new int[list.size()];
        String type = null;
        for (int i = 0; i < list.size(); i++) {
            AVFrame p = list.get(i);
            checkArgument(type == null || type.equals(p.type), "different frame types in list");
            type = p.type;
            sampleSizes[i] = p.data().remaining();
            sampleFlags[i] = p.isAudio() || p.isIFrame() ? 0x02000000 : 0x00010000;
            sampleDurations[i] = checkedCast(p.duration);
        }
        Factory factory = TrunBox
                .create(sampleCount)
                .dataOffset(100500)
                .sampleSize(sampleSizes)
                .sampleFlags(sampleFlags)
                .sampleDuration(sampleDurations);
        TrunBox trun = factory.create();
        return trun;
    }

    static TrackFragmentBox createTrackFragment(List<AVFrame> list, int trackId) {
        long baseMediaDecodeTime = list.get(0).pts;
        TrackFragmentBox traf = TrackFragmentBox.createTrackFragmentBox();
        TrackFragmentHeaderBox tfhd = createTrackFragmentHeaderBox();
        tfhd.setFlags(tfhd.getFlags() | 0x20000); // TFHD_FLAG_DEFAULT_BASE_IS_MOOF
        tfhd.setTrackId(trackId);
        traf.add(tfhd);
        traf.add(createTrackFragmentBaseMediaDecodeTimeBox(baseMediaDecodeTime));
        traf.add(trun(list));
        return traf;

    }

    public static MP4Segment m4sHeader(List<AVFrame> list, int sequenceNumber, Allocator mem) {
        List<AVFrame> video = video(list);
        List<AVFrame> audio = audio(list);
        List<AVFrame> main = !video.isEmpty() ? video : audio;

        long earliestPts = main.get(0).pts;
        int timescale = checkedCast(main.get(0).timescale);
        long duration = duration(main);

        MovieFragmentBox moof = createMovieFragmentBox();
        moof.add(mfhd(sequenceNumber));
        MP4Segment m4s = new MP4Segment();
        m4s.styp = styp();
        m4s.sidx = sidx(earliestPts, timescale, duration);
        m4s.moof = moof;
        int trackId = 1;
        if (!video.isEmpty()) {
            moof.add(createTrackFragment(video, trackId));
            m4s.vtrun = m4s.getTrun(trackId);
            trackId++;
        }
        if (!audio.isEmpty()) {
            moof.add(createTrackFragment(audio, trackId));
            m4s.atrun = m4s.getTrun(trackId);
            trackId++;
        }

        ByteBuffer tmp = mem.acquire(4096);
        m4s.styp.write(tmp);
        m4s.sidx.write(tmp);
        int sidxEndPosition = tmp.position();
        m4s.moof.write(tmp);
        int mdatPosition = tmp.position();
        mem.release(tmp);

        int dataOffset = mdatPosition + 8;
        int dataSize = 0;


        if (!video.isEmpty()) {
            m4s.vtrun.setDataOffset(dataOffset - sidxEndPosition);
            dataOffset += m4s.getVideoDataSize();
            dataSize += m4s.getVideoDataSize();
        }

        if (!audio.isEmpty()) {
            m4s.atrun.setDataOffset(dataOffset - sidxEndPosition);
            dataOffset += m4s.getAudioDataSize();
            dataSize += m4s.getAudioDataSize();
        }

        m4s.sidx.references[0].referenced_size = dataOffset + dataSize - sidxEndPosition;


        return m4s;
    }

    private static List<AVFrame> video(List<AVFrame> list) {
        List<AVFrame> video = new ArrayList<>();
        for (AVFrame f : list) {
            if (f.isVideo()) {
                video.add(f);
            }
        }
        return video;
    }

    private static List<AVFrame> audio(List<AVFrame> list) {
        List<AVFrame> audio = new ArrayList<>();
        for (AVFrame f : list) {
            if (f.isAudio()) {
                audio.add(f);
            }
        }
        return audio;
    }

    public static ByteBuffer m4sFromFrames(List<AVFrame> list, int mseq, Allocator mem) {
        MP4Segment m4s = m4sHeader(list, mseq, mem);
        int approxBufSize = Math.max((int) (m4s.getMdatSize() * 2), 4096);
        ByteBuffer buf = mem.acquire(approxBufSize);
        m4s.styp.write(buf);
        m4s.sidx.write(buf);
        m4s.moof.write(buf);
        createHeader("mdat", m4s.getMdatSize()).write(buf);
        List<AVFrame> video = video(list);
        List<AVFrame> audio = audio(list);

        for (AVFrame frame : video) {
            buf.put(AVFrameUtil.annexb2mp4(frame).data());
        }

        for (AVFrame frame : audio) {
            buf.put(frame.data());
        }

        buf.flip();
        return buf;
    }

    public static MovieBox dashinitMoov(AVFrame video, AVFrame audio) {
        int nextTrackId = 1;
        int timescale = 0;
        if (video != null) {
            nextTrackId++;
            timescale = checkedCast(video.timescale);
        }
        if (audio != null) {
            nextTrackId++;
            if (timescale == 0) {
                timescale = checkedCast(audio.timescale);
            }
        }

        MovieBox moov = MovieBox.createMovieBox();
        moov.add(createMovieHeader(timescale, nextTrackId));
        MovieExtendsBox mvex = createMovieExtendsBox();
        mvex.add(createMovieExtendsHeaderBox());
        int trackId = 1;
        if (video != null) {
            mvex.add(trex(trackId));
            moov.add(createTrack(trackId, video));
            trackId++;
        }
        if (audio != null) {
            mvex.add(trex(trackId));
            moov.add(createTrack(trackId, audio));
            trackId++;
        }
        checkState(nextTrackId == trackId);
        moov.add(mvex);
        return moov;
    }

    public static TrakBox createTrack(int trackId, AVFrame frame) {
        Dimension dim = frame.isVideo() ? frame.getVideoDimension() : new Dimension(0, 0);
        TrackType type = frame.isVideo() ? TrackType.VIDEO : TrackType.SOUND;
        SampleEntry sampleEntry = frame.isVideo() ? videoSampleEntry(frame) : audioSampleEntry(frame.adtsHeader);
        int timescale = checkedCast(frame.timescale);

        TrakBox trak = TrakBox.createTrakBox();
        TrackHeaderBox tkhd = createTrackHeader(trackId, dim);
        MediaBox mdia = MediaBox.createMediaBox();
        MediaHeaderBox mdhd = createMediaHeader(timescale);
        HandlerBox hdlr = createHandler(type);
        MediaInfoBox minf = createMediaInfoBox();
        SampleDescriptionBox stsd = createSampleDescriptionBox(sampleEntry);
        NodeBox stbl = new NodeBox(new Header("stbl"));
        emptyStbl(stbl);

        trak.add(tkhd);
        trak.setEdits(asList(new Edit(0, 0, 1)));
        trak.add(mdia);

        mdia.add(mdhd);
        mdia.add(hdlr);
        mdia.add(minf);

        minf.add(createVideoMediaHeaderBox(0, 0, 0, 0));
        minf.add(createDataInfo());
        minf.add(stbl);

        stbl.add(stsd);

        return trak;
    }

    public static DataInfoBox createDataInfo() {
        DataInfoBox dinf = DataInfoBox.createDataInfoBox();
        DataRefBox dref = DataRefBox.createDataRefBox();
        dref.add(createLeafBox(Header.createHeader("url ", 0), ByteBuffer.wrap(new byte[] { 0, 0, 0, 1 })));
        dinf.add(dref);
        return dinf;
    }

    public static HandlerBox createHandler(TrackType type) {
        return HandlerBox.createHandlerBox("mhlr", type.getHandler(), "appl", 0, 0);
    }

    public static MediaHeaderBox createMediaHeader(int timescale) {
        long now = System.currentTimeMillis();
        return MediaHeaderBox.createMediaHeaderBox(timescale, 0, 0, now, now, 0);
    }

    public static TrackHeaderBox createTrackHeader(int trackId, Dimension dim) {
        long now = System.currentTimeMillis();
        return createTrackHeaderBox(trackId, 0, dim.width, dim.height, now, now, 1.0f, (short) 0, 0, default_matrix);
    }

    public static TrackExtendsBox trex(int trackId) {
        TrackExtendsBox trex = TrackExtendsBox.createTrackExtendsBox();
        trex.setTrackId(trackId);
        trex.setDefaultSampleDescriptionIndex(1);
        return trex;
    }

}
