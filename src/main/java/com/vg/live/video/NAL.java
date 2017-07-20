package com.vg.live.video;

import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    public static final int find(ByteBuffer haystack, int pos, int needle) {
        if (!haystack.hasRemaining())
            return haystack.limit();

        int val = 0xffffffff;

        for (int i = pos; i < haystack.limit(); i++) {
            val <<= 8;
            val |= (haystack.get(i) & 0xff);
            if ((val & 0xffffff) == needle) {
                return i - 3;
            }
        }
        return haystack.limit();
    }

    public static List<ByteBuffer> splitNal(ByteBuffer x) {
        List<Integer> list = new ArrayList<>();
        int find = -1;
        int pos = x.position();
        int limit = x.limit();
        do {
            find = find(x, pos, 1);
            if (find != limit) {
                list.add(find);
            }
            pos = find + 4;
        } while (find != limit);
        if (list.isEmpty()) {
            return Collections.singletonList(x);
        }
        List<ByteBuffer> output = new ArrayList<>();

        int prev = x.position();
        for (int idx : list) {
            ByteBuffer duplicate = x.duplicate();
            duplicate.position(prev);
            duplicate.limit(idx);
            if (duplicate.hasRemaining()) {
                output.add(duplicate);
            }
            prev = idx;
        }
        ByteBuffer duplicate = x.duplicate();
        duplicate.position(prev);
        duplicate.limit(limit);
        if (duplicate.hasRemaining()) {
            output.add(duplicate);
        }
        return output;
    }
}
