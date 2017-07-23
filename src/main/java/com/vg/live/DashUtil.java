package com.vg.live;

import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.common.model.Packet;
import org.jcodec.containers.mp4.MP4Util;
import org.jcodec.containers.mp4.boxes.Box;
import org.jcodec.containers.mp4.boxes.ChunkOffsetsBox;
import org.jcodec.containers.mp4.boxes.Edit;
import org.jcodec.containers.mp4.boxes.FileTypeBox;
import org.jcodec.containers.mp4.boxes.MediaBox;
import org.jcodec.containers.mp4.boxes.MediaHeaderBox;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.MovieExtendsBox;
import org.jcodec.containers.mp4.boxes.MovieFragmentBox;
import org.jcodec.containers.mp4.boxes.MovieFragmentHeaderBox;
import org.jcodec.containers.mp4.boxes.MovieHeaderBox;
import org.jcodec.containers.mp4.boxes.NodeBox;
import org.jcodec.containers.mp4.boxes.SampleSizesBox;
import org.jcodec.containers.mp4.boxes.SampleToChunkBox;
import org.jcodec.containers.mp4.boxes.SampleToChunkBox.SampleToChunkEntry;
import org.jcodec.containers.mp4.boxes.SegmentIndexBox;
import org.jcodec.containers.mp4.boxes.SyncSamplesBox;
import org.jcodec.containers.mp4.boxes.TimeToSampleBox;
import org.jcodec.containers.mp4.boxes.TimeToSampleBox.TimeToSampleEntry;
import org.jcodec.containers.mp4.boxes.TrackExtendsBox;
import org.jcodec.containers.mp4.boxes.TrackFragmentBaseMediaDecodeTimeBox;
import org.jcodec.containers.mp4.boxes.TrackHeaderBox;
import org.jcodec.containers.mp4.boxes.TrakBox;
import org.jcodec.containers.mp4.demuxer.AbstractMP4DemuxerTrack;

import com.vg.util.HexDump;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Map;

import static com.vg.live.MP4Helper.getStts;
import static com.vg.live.MP4Helper.isEqualSampleDurations;
import static com.vg.live.MP4Segment.createTrackFragment;
import static com.vg.live.MP4Segment.mfhd;
import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;
import static org.jcodec.containers.mp4.boxes.MovieHeaderBox.createMovieHeaderBox;

public class DashUtil {
    static TrakBox dashTrack(TrakBox realTrack) {
        TrackHeaderBox trackHeader = realTrack.getTrackHeader();
        trackHeader.setDuration(0);
        realTrack.setEdits(asList(new Edit(0, 0, 1)));
        MediaBox mdia = realTrack.getMdia();
        NodeBox.findFirst(mdia, MediaHeaderBox.class, "mdhd").setDuration(0);
        NodeBox stbl = mdia.getMinf().getStbl();
        emptyStbl(stbl);
        stbl.removeChildren("co64", "stss", "ctts");
        return realTrack;
    }

    public static MovieBox dashinit(MovieBox realMoov) {
        MovieBox moov = dashMoov(realMoov);
        MovieExtendsBox mvex = MovieExtendsBox.createMovieExtendsBox();
        moov.add(mvex);
        for (TrakBox track : realMoov.getTracks()) {
            moov.add(dashTrack(track));
            mvex.add(createTrex(track));
        }
        return moov;
    }

    private static MovieBox dashMoov(MovieBox realMovie) {
        MovieBox moov = MovieBox.createMovieBox();
        moov.addFirst(createMovieHeader(realMovie.getTimescale(), 3));
        return moov;
    }

    public final static int[] default_matrix = new int[] { 0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000 };

    public static MovieHeaderBox createMovieHeader(int timescale, int nextTrackId) {
        long now = currentTimeMillis();
        MovieHeaderBox mvhd = createMovieHeaderBox(timescale, 0, 1.0f, 1.0f, now, now, default_matrix, nextTrackId);
        return mvhd;
    }

    private static TrackExtendsBox createTrex(TrakBox track) {
        TrackExtendsBox trex1 = TrackExtendsBox.createTrackExtendsBox();
        trex1.setTrackId(track.getTrackHeader().getTrackId());
        trex1.setDefaultSampleDescriptionIndex(1);
        if (isEqualSampleDurations(getStts(track))) {
            trex1.setDefaultSampleDuration(getStts(track).getEntries()[0].getSampleDuration());
        }
        trex1.setDefaultSampleFlags(0x00010000);
        return trex1;
    }


    public static byte[] makeDashinit(File src) throws IOException {
        return makeDashinit(MP4Util.parseMovie(src));
    }

    public static byte[] makeDashinit(MovieBox realMovie) {
        MovieBox moov = dashinit(realMovie);
        FileTypeBox ftyp = FileTypeBox.createFileTypeBox("iso5", 1, Arrays.asList("avc1", "iso5", "dash"));

        ByteBuffer buf = ByteBuffer.allocate(2048);
        ftyp.write(buf);
        moov.write(buf);

        buf.flip();
        byte[] arr = new byte[buf.remaining()];
        buf.get(arr);
        return arr;
    }

    public static int[] getSampleFlags(int sampleCount, SyncSamplesBox stss) {
        int[] sampleFlags = new int[sampleCount];
        if (stss != null) {
            Arrays.fill(sampleFlags, 0x00010000);
            int[] syncSamples = stss.getSyncSamples();
            for (int i = 0; i < syncSamples.length; i++) {
                int idx = syncSamples[i] - 1;
                sampleFlags[idx] = 0x02000000; //I-frame here
            }
        } else {
            //no stss found. assuming i-frame only video
            Arrays.fill(sampleFlags, 0x02000000);
            if (sampleCount != 1) {
                //more than one i-frame in the video. all i-frames?
            }
        }
        return sampleFlags;
    }

    public static MP4Segment convertToDashBoxes(MovieBox realMovie, long startTime, int sequenceNumber) {
        TrakBox track = realMovie.getVideoTrack();
        if (track == null) {
            track = realMovie.getAudioTracks().get(0);
        }

        MediaHeaderBox mdhd = NodeBox.findFirstPath(track, MediaHeaderBox.class, Box.path("mdia.mdhd"));

        int timescale = mdhd.getTimescale();
        long duration = mdhd.getDuration();

        MovieFragmentBox moof = MovieFragmentBox.createMovieFragmentBox();

        moof.add(mfhd(sequenceNumber));
        moof.add(createTrackFragment(track, startTime));

        MP4Segment m4s = new MP4Segment();
        m4s.styp = MP4Segment.styp();
        m4s.sidx = MP4Segment.sidx(startTime, timescale, duration);
        m4s.moof = moof;
        m4s.vtrun = m4s.getTrun(track.getTrackHeader().getTrackId());
        return m4s;
    }

    private static void convertAnnexBtoAVCc(ByteBuffer data) {
        ByteBuffer buf = data.duplicate();
        int prevPosition = -1;
        int currPosition;

        if (buf.remaining() <= 4 || buf.getInt(buf.position()) != 0x00000001) {
            return;
        }
        while (buf.hasRemaining()) {
            H264Utils.skipToNALUnit(buf);
            currPosition = buf.position();
            if (prevPosition != -1) {
                int len = buf.hasRemaining() ? currPosition - prevPosition - 4 : currPosition - prevPosition;
                buf.putInt(prevPosition - 4, len);
            }
            prevPosition = currPosition;
        }
    }

    public static void writeTrackData(FileChannel channel, AbstractMP4DemuxerTrack track) throws IOException {
        boolean avc1 = "avc1".equals(track.getFourcc());
        Packet packet;
        while (null != (packet = track.nextFrame())) {
            ByteBuffer data = packet.getData();
            if (avc1) {
                convertAnnexBtoAVCc(data);
            }
            while (data.hasRemaining()) {
                channel.write(data);
            }
        }
    }

    public static void patchM4S(File m4s, long baseMediaDecodeTime, int sequenceNumber) throws IOException {
        Map<String, Box> rootBoxes = MP4Helper.rootBoxes(m4s);
        MovieFragmentBox moof = (MovieFragmentBox) rootBoxes.get("moof");
        MovieFragmentHeaderBox mfhd = NodeBox.findFirst(moof, MovieFragmentHeaderBox.class, "mfhd");
        mfhd.setSequenceNumber(sequenceNumber);
        TrackFragmentBaseMediaDecodeTimeBox tfdt = NodeBox.findFirstPath(moof, TrackFragmentBaseMediaDecodeTimeBox.class, Box.path("traf.tfdt"));
        tfdt.setBaseMediaDecodeTime(baseMediaDecodeTime);
        SegmentIndexBox sidx = (SegmentIndexBox) rootBoxes.get("sidx");
        sidx.earliest_presentation_time = baseMediaDecodeTime;

        MP4Helper.replaceBoxes(m4s, moof, sidx);
    }

    public static MovieBox avdashinit(MovieBox realMovie) {
        return dashinit(realMovie);
    }

    public static void emptyStbl(NodeBox stbl) {
        stbl.replace("stts", TimeToSampleBox.createTimeToSampleBox(new TimeToSampleEntry[0]));
        stbl.replace("stsc", SampleToChunkBox.createSampleToChunkBox(new SampleToChunkEntry[0]));
        stbl.replace("stsz", SampleSizesBox.createSampleSizesBox2(new int[0]));
        stbl.replace("stco", ChunkOffsetsBox.createChunkOffsetsBox(new long[0]));
    }

}
