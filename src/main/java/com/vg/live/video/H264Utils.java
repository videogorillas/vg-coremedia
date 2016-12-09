package com.vg.live.video;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;

public class H264Utils {

    public static List<NAL> splitNalUnits(ByteBuffer raw264) {
        List<NAL> bufs = new ArrayList<NAL>();
        int pos = raw264.position();
        int lim = raw264.limit();
        int prevPos = -1;
        for (int i = pos; i < lim - 4; i++) {
            raw264.position(pos);
            int header = raw264.getInt();
            if (0x00000001 == header) {
                if (prevPos != -1) {
                    raw264.position(prevPos);
                    raw264.limit(pos);
                    ByteBuffer nalBuf = raw264.slice();
                    nalBuf.position(4);
                    NALUnit nal = NALUnit.read(nalBuf);
                    nalBuf.clear();
                    NAL nal_ = new NAL();
                    nal_.type = nal.type;
                    nal_.nalData = nalBuf;
                    bufs.add(nal_);
                    raw264.clear();
                }
                prevPos = pos;
            }
            pos++;
        }
        if (prevPos != -1) {
            raw264.position(prevPos);
            raw264.limit(lim);
            ByteBuffer nalBuf = raw264.slice();
            nalBuf.position(4);
            NALUnit nal = NALUnit.read(nalBuf);
            nalBuf.clear();
            NAL nal_ = new NAL();
            nal_.type = nal.type;
            nal_.nalData = nalBuf;
            bufs.add(nal_);
            raw264.clear();
        }
        return bufs;
    }

    public static NAL findFirst(List<NAL> nals, NALUnitType type) {
        for (NAL n : nals) {
            if (n.type.equals(type)) {
                return n;
            }
        }
        return null;
    }

    public static boolean hasIDR(ByteBuffer raw264) {
        if (raw264.hasRemaining()) {
            List<NAL> nals = splitNalUnits(raw264);
            return findFirst(nals, NALUnitType.IDR_SLICE) != null;
        }
        return false;
    }
}
