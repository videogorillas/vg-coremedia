package com.vg.live.video;

import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;

import java.nio.Buffer;
import java.nio.ByteBuffer;

public class NAL {
    public NALUnitType type;
    public ByteBuffer nalData;

    public byte[] toByteArray() {
        nalData.clear();
        byte[] buf = new byte[nalData.remaining()];
        nalData.get(buf);
        return buf;
    }


    public SeqParameterSet readSps() {
        nalData.clear();
        nalData.position(4 + 1);
        SeqParameterSet sps = SeqParameterSet.read(nalData);
        return sps;
    }

    @Override
    public String toString() {
        return type + " " + nalData.remaining();
    }


    public static NALUnitType readNal(int nalu) {
        int nal_ref_idc = (nalu >> 5) & 0x3;
        int nb = nalu & 0x1f;
        NALUnitType type = NALUnitType.fromValue(nb);
        return type;
    }
}
