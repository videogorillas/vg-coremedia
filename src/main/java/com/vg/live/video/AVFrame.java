package com.vg.live.video;

import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;

import java.nio.ByteBuffer;

public class AVFrame {
    public static final String VIDEO_FRAME = "V";
    public static final String AUDIO_FRAME = "A";
    public static final String DATA_FRAME = "F";
    
    public final String type;
    public int trackId;
    public long timescale;
    public long pts;
    public Long dts;
    public long streamOffset;
    public int streamSize;
    public transient ByteBuffer _data;
    public int dataSize;
    public int dataOffset;
    public transient SeqParameterSet sps;
    public transient PictureParameterSet pps;
    public transient ADTSHeader adtsHeader;
    public transient ByteBuffer spsBuf;
    public transient ByteBuffer ppsBuf;
    public Dimension dim;
    public long duration;
    public boolean iframe;

    public AVFrame(String type, long streamOffset, int size) {
        this(type, streamOffset, size, false);
    }

    public AVFrame(String type, long streamOffset, int size, boolean iframe) {
        this.type = type;
        this.streamOffset = streamOffset;
        this.dataSize = size;
        this.streamSize = size;
        this.iframe = iframe;
    }

    public boolean isIFrame() {
        return iframe;
    }

    @Override
    public String toString() {
        return String.format("%s[%d] pts/dts: %d%s @%d", type, trackId, pts, dts == null ? "" : "/" + dts, timescale);
    }

    public static AVFrame audio(long offset, int size) {
        return new AVFrame(AUDIO_FRAME, offset, size);
    }

    public static AVFrame video(long offset, int size, boolean iframe) {
        return new AVFrame(VIDEO_FRAME, offset, size, iframe);
    }

    public boolean isVideo() {
        return VIDEO_FRAME.equals(type);
    }

    public ByteBuffer data() {
        if (_data.capacity() == 0) {
            throw new RuntimeException("accessing released payload");
        }
        _data.limit(dataOffset + dataSize);
        _data.position(dataOffset);
        return _data;
    }

    public Dimension getVideoDimension() {
        if (dim != null) {
            return dim;
        }
        return dim = dim(sps);
    }

    public boolean isAudio() {
        return AUDIO_FRAME.equals(type);
    }

    public SeqParameterSet getSps() {
        return sps;
    }

    public void setSps(SeqParameterSet sps) {
        this.sps = sps;
        dim(sps);
    }

    private static Dimension dim(SeqParameterSet sps) {
        Dimension d = null;
        if (sps != null) {
            int w = (sps.picWidthInMbsMinus1 + 1) << 4;
            int h = (sps.picHeightInMapUnitsMinus1 + 1) << 4;
            d = new Dimension(w, h);
        }
        return d;
    }
}