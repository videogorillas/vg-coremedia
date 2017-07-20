package com.vg.live.video;

import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;

import java.nio.ByteBuffer;

public class AVFrame {
    public final String type;
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
    private Dimension dim;
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
        return "AVFrame{" +
                "type='" + type + '\'' +
                ", pts=" + pts +
                ", duration=" + duration +
                ", timescale=" + timescale +
                ", streamSize=" + streamSize +
                ", dts=" + dts +
                ", iframe=" + iframe +
                ", streamOffset=" + streamOffset +
                ", _data=" + _data +
                ", dataSize=" + dataSize +
                ", dataOffset=" + dataOffset +
                ", sps=" + sps +
                ", pps=" + pps +
                ", adtsHeader=" + adtsHeader +
                ", spsBuf=" + spsBuf +
                ", ppsBuf=" + ppsBuf +
                ", dim=" + dim +
                '}';
    }

    public static AVFrame audio(long offset, int size) {
        return new AVFrame("A", offset, size);
    }

    public static AVFrame video(long offset, int size, boolean iframe) {
        return new AVFrame("V", offset, size, iframe);
    }

    public boolean isVideo() {
        return "V".equals(type);
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
        return "A".equals(type);
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
            int w = (sps.pic_width_in_mbs_minus1 + 1) << 4;
            int h = (sps.pic_height_in_map_units_minus1 + 1) << 4;
            d = new Dimension(w, h);
        }
        return d;
    }
}