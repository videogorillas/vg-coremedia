package com.vg.live.video;

import java.nio.ByteBuffer;

/**
 * https://en.wikipedia.org/wiki/Packetized_elementary_stream
 * 
 * @author zhukov
 *
 */
public class PESPacket {
    public transient ByteBuffer _payload;
    public int streamId;
    public long streamOffset;
    public int streamSize;
    public int payloadOffset;
    public int payloadSize;
    /**
     * <pre>
    decoding time stamp (DTS) and a presentation time stamp (PTS).
    CT - composition time (mp4 spec)
    DT - decode time
    
    PTS == CT
    DTS == DT
    
    DT(n+1) = DT(n) + STTS(n) 
    CT(n) = DT(n) + CTTS(n)
    
    DTS(n+1) = DTS(n) + STTS(n)
    PTS(n) = DTS(n) + CTTS(n)
    
    STTS(n) == DTS(n+1) - DTS(n)
    CTTS(n) == PTS(n) - DTS(n)
     * </pre>
     */
    public long pts;
    public long dts;
    public long duration;
    public static final int PES_HEADER_SIZE = 6;
    public static final int PRIVATE_1 = 0x1bd;
    public static final int VIDEO_MAX = 0x1EF;

    public ByteBuffer payload() {
        _payload.limit(payloadOffset + payloadSize);
        _payload.position(payloadOffset);
        return _payload;
    }

    static void mpeg1Pes(int b0, int len, int streamId, ByteBuffer is) {
        int c = b0;
        while (c == 0xff) {
            c = is.get() & 0xff;
        }

        if ((c & 0xc0) == 0x40) {
            is.get();
            c = is.get() & 0xff;
        }
        long pts = -1, dts = -1;
        if ((c & 0xf0) == 0x20) {
            pts = readTs1(is, c);
        } else if ((c & 0xf0) == 0x30) {
            pts = readTs1(is, c);
            dts = readTs(is);
        } else {
            if (c != 0x0f)
                throw new RuntimeException("Invalid data");
        }
    }

    //TODO: move this method to a utility class
    public static boolean isVideo(int streamId) {
        streamId &= 0xff;
        return streamId >= 0xe0 && streamId <= 0xef;
    }

    static void mpeg2Pes(int b0, int len, int streamId, ByteBuffer is) {
        int flags1 = b0;
        int flags2 = is.get() & 0xff;
        int header_len = is.get() & 0xff;

        long pts = -1, dts = -1;
        if ((flags2 & 0xc0) == 0x80) {
            pts = readTs(is);
            PESPacket.skip(is, header_len - 5);
        } else if ((flags2 & 0xc0) == 0xc0) {
            pts = readTs(is);
            dts = readTs(is);
            PESPacket.skip(is, header_len - 10);
        } else {
            PESPacket.skip(is, header_len);
        }
    }

    static void skipPESHeader(ByteBuffer iss) {
        int streamId = iss.getInt() & 0xff;
        int len = iss.getShort();
        if (streamId != 0xbf) {
            int b0 = iss.get() & 0xff;
            if ((b0 & 0xc0) == 0x80)
                PESPacket.mpeg2Pes(b0, len, streamId, iss);
            else
                PESPacket.mpeg1Pes(b0, len, streamId, iss);
        }
    }

    public static boolean isAudio(int streamId) {
        streamId &= 0xff;
        return streamId >= 0xbf && streamId <= 0xdf;
    }

    public static final boolean psMarker(int marker) {
        return (marker & 0xffffff00) == 0x100 && marker >= PRIVATE_1 && marker <= VIDEO_MAX;
    }

    public static int nextPsMarkerPosition(ByteBuffer buf) {
        int lim = buf.limit();
        int val = 0xffffffff;
        for (int i = buf.position(); i < lim; i++) {
            val <<= 8;
            val |= (buf.get(i) & 0xff);
            if (psMarker(val)) {
                return i - 3;
            }
        }
        return buf.limit();
    }

    public PESPacket(ByteBuffer data, long pts, int streamId, int length, long pos, long dts) {
        this._payload = data;
        this.pts = pts;
        this.streamId = streamId;
        this.payloadSize = length;
        this.streamOffset = pos;
        this.dts = dts;
    }

    public static PESPacket readPESHeader(ByteBuffer bb, long pos) {
        int streamId = bb.getInt();
        if (!psMarker(streamId)) {
            return null;
        }
        int len = bb.getShort() & 0xffff;
        int b0 = bb.get() & 0xff;
        if ((b0 & 0xc0) == 0x80)
            return PESPacket.mpeg2Pes(b0, len, streamId, bb, pos);
        else
            return PESPacket.mpeg1Pes(b0, len, streamId, bb, pos);
    }

    public static int skip(ByteBuffer buffer, int count) {
        int toSkip = Math.min(buffer.remaining(), count);
        buffer.position(buffer.position() + toSkip);
        return toSkip;
    }

    public static PESPacket mpeg2Pes(int b0, int len, int streamId, ByteBuffer is, long pos) {
        int flags1 = b0;
        int flags2 = is.get() & 0xff;
        int header_len = is.get() & 0xff;

        long pts = -1, dts = -1;
        if ((flags2 & 0xc0) == 0x80) {
            pts = PESPacket.readTs(is);
            skip(is, header_len - 5);
        } else if ((flags2 & 0xc0) == 0xc0) {
            pts = PESPacket.readTs(is);
            dts = PESPacket.readTs(is);
            skip(is, header_len - 10);
        } else {
            skip(is, header_len);
        }
        return new PESPacket(null, pts, streamId, len, pos, dts);
    }

    public static long readTs(ByteBuffer is) {
        long intOverflow = 536870912L * (is.get() & 0x0e);
        return intOverflow + (((is.get() & 0xff) << 22) | (((is.get() & 0xff) >> 1) << 15) | ((is.get() & 0xff) << 7)
                | ((is.get() & 0xff) >> 1));
    }

    public static PESPacket mpeg1Pes(int b0, int len, int streamId, ByteBuffer is, long pos) {
        int c = b0;
        while (c == 0xff) {
            c = is.get() & 0xff;
        }

        if ((c & 0xc0) == 0x40) {
            is.get();
            c = is.get() & 0xff;
        }
        long pts = -1, dts = -1;
        if ((c & 0xf0) == 0x20) {
            pts = PESPacket.readTs1(is, c);
        } else if ((c & 0xf0) == 0x30) {
            pts = PESPacket.readTs1(is, c);
            dts = readTs(is);
        } else {
            if (c != 0x0f)
                throw new RuntimeException("Invalid data");
        }

        return new PESPacket(null, pts, streamId, len, pos, dts);
    }

    public static long readTs1(ByteBuffer is, int c) {
        return (((long) c & 0x0e) << 29) | ((is.get() & 0xff) << 22) | (((is.get() & 0xff) >> 1) << 15)
                | ((is.get() & 0xff) << 7) | ((is.get() & 0xff) >> 1);
    }

    public String getType() {
        if (isAudio(streamId)) return "A";
        if (isVideo(streamId)) return "V";
        return "?";
    }

    public static PESPacket readPESPayload(ByteBuffer buf, long pos) {
        PESPacket ph = readPESHeader(buf, pos);
        ph._payload = buf;
        ph.payloadOffset = buf.position();
        ph.payloadSize = buf.remaining();
        return ph;
    }
}