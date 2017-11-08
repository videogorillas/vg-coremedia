package com.vg.live;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.primitives.Ints.checkedCast;
import static com.vg.live.MP4Helper.getStss;
import static com.vg.live.MP4Helper.getStsz;
import static com.vg.live.MP4Helper.getStts;
import static java.util.Arrays.asList;
import static org.jcodec.containers.mp4.boxes.MovieFragmentBox.createMovieFragmentBox;
import static org.jcodec.containers.mp4.boxes.SegmentTypeBox.createSegmentTypeBox;
import static org.jcodec.containers.mp4.boxes.TrackFragmentBaseMediaDecodeTimeBox.createTrackFragmentBaseMediaDecodeTimeBox;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.containers.mp4.MP4Util;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.MovieFragmentBox;
import org.jcodec.containers.mp4.boxes.MovieFragmentHeaderBox;
import org.jcodec.containers.mp4.boxes.NodeBox;
import org.jcodec.containers.mp4.boxes.SampleSizesBox;
import org.jcodec.containers.mp4.boxes.SegmentIndexBox;
import org.jcodec.containers.mp4.boxes.SegmentIndexBox.Reference;
import org.jcodec.containers.mp4.boxes.SegmentTypeBox;
import org.jcodec.containers.mp4.boxes.TrackFragmentBaseMediaDecodeTimeBox;
import org.jcodec.containers.mp4.boxes.TrackFragmentBox;
import org.jcodec.containers.mp4.boxes.TrackFragmentHeaderBox;
import org.jcodec.containers.mp4.boxes.TrakBox;
import org.jcodec.containers.mp4.boxes.TrunBox;
import org.jcodec.containers.mp4.boxes.TrunBox.Factory;

public class MP4Segment {
    public SegmentTypeBox styp;
    public SegmentIndexBox sidx;
    public MovieFragmentBox moof;
    public TrunBox vtrun;
    public TrunBox atrun;

    public TrunBox getTrun(int trackId) {
        TrackFragmentBox traf = getTraf(trackId);
        return NodeBox.findFirst(traf, TrunBox.class, "trun");
    }

    public TrackFragmentBox getTraf(int trackId) {
        return findByTrackId(moof.getTracks(), trackId);
    }

    public long getDuration(int trackId, int defaultSampleDuration) {
        TrunBox trun = this.getTrun(trackId);
        int[] sampleDurations = trun.getSampleDurations();
        long duration;

        if (sampleDurations != null) {
            duration = sum(sampleDurations);
        } else {
            duration = defaultSampleDuration * trun.getSampleCount();
        }
        return duration;
    }

    TrackFragmentBox traf(int trackId) {
        TrackFragmentBox[] tracks = moof.getTracks();
        return findByTrackId(tracks, trackId);
    }

    private static TrackFragmentBox findByTrackId(TrackFragmentBox[] tracks, int trackId) {
        for (TrackFragmentBox t : tracks) {
            if (t.getTrackId() == trackId) {
                return t;
            }
        }
        return null;
    }

    public long getBaseMediaDecodeTime(int trackId) {
        TrackFragmentBox traf = getTraf(trackId);
        TrackFragmentBaseMediaDecodeTimeBox tfdt = NodeBox.findFirst(traf,
                TrackFragmentBaseMediaDecodeTimeBox.class, "tfdt");
        long trackStartTv = tfdt.getBaseMediaDecodeTime();
        return trackStartTv;

    }

    public long getVideoDataSize() {
        return vtrun == null ? 0 : sum(vtrun.getSampleSizes());
    }

    public long getAudioDataSize() {
        return atrun == null ? 0 : sum(atrun.getSampleSizes());
    }

    public boolean hasAudio() {
        return atrun != null;
    }

    public long getMdatSize() {
        long videoDataSize = this.getVideoDataSize();
        long audioDataSize = this.getAudioDataSize();

        long mdatSize = videoDataSize + audioDataSize + 8;
        return mdatSize;
    }

    public int getTrackId(int trackIndex) {
        return moof.getTracks()[trackIndex].getTrackId();
    }

    public static MP4Segment convertToDash(MovieBox moov, long videoStartTime, long audioStartTime,
                                           int sequenceNumber) {
        MovieFragmentBox moof = createMovieFragmentBox();

        moof.add(mfhd(sequenceNumber));
        for(TrakBox track : moov.getTracks()) {
            moof.add(createTrackFragment(track, track.isVideo() ? videoStartTime : audioStartTime));
        }

        MP4Segment m4s = new MP4Segment();
        m4s.styp = styp();
        m4s.sidx = sidx(videoStartTime, moov.getTimescale(), moov.getDuration());
        m4s.moof = moof;
        m4s.vtrun = m4s.getTrun(moov.getVideoTrack().getTrackHeader().getTrackId());

        if (moov.getAudioTracks().size() > 0) {
            TrakBox audioTrack = moov.getAudioTracks().get(0);
            m4s.atrun = m4s.getTrun(audioTrack.getTrackHeader().getTrackId());
        }

        return m4s;
    }

    public static SegmentTypeBox styp() {
        return createSegmentTypeBox("msdh", 0, asList("msdh", "msix"));
    }

    public static MovieFragmentHeaderBox mfhd(int sequenceNumber) {
        MovieFragmentHeaderBox mfhd = MovieFragmentHeaderBox.createMovieFragmentHeaderBox();
        mfhd.setSequenceNumber(sequenceNumber);
        return mfhd;
    }

    public static SegmentIndexBox sidx(long earliestPresentationTime, int timescale, long duration) {
        SegmentIndexBox sidx = SegmentIndexBox.createSegmentIndexBox();
        sidx.reference_ID = 1;
        sidx.timescale = timescale;
        sidx.earliest_presentation_time = earliestPresentationTime;
        sidx.first_offset = 0;
        sidx.reserved = 0;
        sidx.reference_count = 1;
        sidx.references = new Reference[] { new Reference() };
        Reference ref = sidx.references[0];
        ref.reference_type = false;
        ref.referenced_size = 100500; //TODO: resulting file size -0x44;
        ref.subsegment_duration = duration;
        ref.starts_with_SAP = true;
        ref.SAP_type = 1;
        ref.SAP_delta_time = 0;
        return sidx;
    }

    static int[] fillArrayIfNull(int[] array, int size, int fill) {
        if (array == null) {
            array = new int[size];
            Arrays.fill(array, fill);
        }
        return array;
    }

    static TrackFragmentBox createTrackFragment(TrakBox track, long baseMediaDecodeTime) {
        int trackId = track.getTrackHeader().getTrackId();
        SampleSizesBox stsz = getStsz(track);
        int sampleCount = checkedCast(track.getSampleCount());
        int[] sampleFlags = DashUtil.getSampleFlags(sampleCount, getStss(track));
        int[] sampleSizes = fillArrayIfNull(stsz.getSizes(), sampleCount, stsz.getDefaultSize());
        int[] sampleDurations = MP4Helper.getSampleDurations(getStts(track));
        checkState(sampleCount == sampleDurations.length, "expected sampleDurations %s actual %s", sampleCount, sampleDurations.length);
        checkState(sampleCount == sampleSizes.length, "expected sampleSizes %s actual %s", sampleCount, sampleSizes.length);
        checkState(sampleCount == sampleFlags.length, "expected sampleFlags %s actual %s", sampleCount, sampleFlags.length);

        TrackFragmentBox traf = TrackFragmentBox.createTrackFragmentBox();
        TrackFragmentHeaderBox tfhd = new TrackFragmentHeaderBox.Factory(TrackFragmentHeaderBox.createTrackFragmentHeaderBox())
                .defaultSampleDuration(sampleDurations[0])
                .defaultSampleFlags(sampleFlags[0])
                .defaultSampleSize(sampleSizes[0])
                .create();
        tfhd.setFlags(tfhd.getFlags() | 0x20000); // TFHD_FLAG_DEFAULT_BASE_IS_MOOF
        tfhd.setTrackId(trackId);
        traf.add(tfhd);
        traf.add(createTrackFragmentBaseMediaDecodeTimeBox(baseMediaDecodeTime));
        Factory afactory = TrunBox.create(sampleCount).dataOffset(100500).sampleFlags(sampleFlags).sampleSize(sampleSizes).sampleDuration(sampleDurations);
        TrunBox atrun = afactory.create();
        traf.add(atrun);

        return traf;

    }

    public static MP4Segment parseM4S(SeekableByteChannel input) throws IOException {
        MP4Segment m4s = new MP4Segment();
        for (MP4Util.Atom atom : MP4Util.getRootAtoms(input)) {
            String fourcc = atom.getHeader().getFourcc();
            if ("sidx".equals(fourcc)) {
                m4s.sidx = (SegmentIndexBox) atom.parseBox(input);
            } else if ("moof".equals(fourcc)) {
                m4s.moof = (MovieFragmentBox) atom.parseBox(input);
            } else if ("styp".equals(fourcc)) {
                m4s.styp = (SegmentTypeBox) atom.parseBox(input);
            }
        }
        return m4s;
    }

    public static MP4Segment parseM4S(File file) throws IOException {
        try (FileChannelWrapper input = new FileChannelWrapper(new FileInputStream(file).getChannel())) {
            return parseM4S(input);
        }
    }

    public static long sum(int[] sizes) {
        long sum = 0;
        for (int i = 0; i < sizes.length; i++) {
            sum += sizes[i];
        }
        return sum;
    }

    public long getDataSize() {
        return getVideoDataSize() + getAudioDataSize();
    }
}