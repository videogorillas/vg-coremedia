package com.vg.live.video;

import static com.vg.live.video.PESPacket.nextPsMarkerPosition;
import static com.vg.live.video.PESPacket.psMarker;
import static com.vg.live.video.PESPacket.readPESHeader;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.common.LongArrayList;

public class TSParser {

    private ByteBuffer tmp = ByteBuffer.allocate(64 * 1024);
    
    public static PESPacket pesPacket(TSPkt pkt) {
        if (pkt.payloadStart) {
            ByteBuffer payload = pkt.payload();
            int marker = payload.getInt(payload.position());
            if (psMarker(marker)) {
                PESPacket pespkt = readPESHeader(payload, 0);
                return pespkt;
            }
        }
        return null;
    }
    
    public static boolean isVideo(TSPkt pkt) {
        PESPacket pesHeader = pesPacket(pkt);
        if (pesHeader != null) {
            return PESPacket.isVideo(pesHeader.streamId);
        }
        return false;
    }

    public long raw264(ByteBuffer ts, ByteBuffer raw264) {
        int limit = ts.limit();

        ByteBuffer pes;
        tmp.clear();
        if (tmp.capacity() >= limit) {
            pes = (ByteBuffer) tmp.limit(limit);
        } else {
            pes = tmp = ByteBuffer.allocate(limit);
        }
        int videoPid = -1;
        long firstPts = 0;
        TSPkt pkt = new TSPkt();
        for (int pos = ts.position(); pos < limit; pos += 188) {
            ts.clear();
            ts.position(pos);
            ts.limit(pos + 188);
            TSPkt.parsePacket(pkt, ts);
            if (videoPid == -1) {
                PESPacket pesPkt = pesPacket(pkt);
                if (pesPkt != null) {
                    if (PESPacket.isVideo(pesPkt.streamId)) {
                        videoPid = pkt.pid;
                        firstPts = pesPkt.pts;
                    }
                }
            }
            ByteBuffer payload = pkt.payload();
            if (pkt.pid == videoPid && payload != null) {
                pes.put(payload);
            }
        }
        pes.flip();

        int pesLimit = pes.limit();
        PESPacket pespkt = null;
        for (int i = pes.position(); i < pesLimit - 4;) {
            int marker = pes.getInt(i);
            if (psMarker(marker)) {
                pespkt = readPESHeader(pes, 0);
                int newLimit;
                if (pespkt.payloadSize == 0) {
                    // If the PES packet length is set to zero, the PES packet
                    // can be of any length.
                    // A value of zero for the PES packet length can be used
                    // only when the PES packet payload is a video elementary
                    // stream.
                    newLimit = nextPsMarkerPosition(pes);
                } else {
                    newLimit = i + PESPacket.PES_HEADER_SIZE + pespkt.payloadSize;
                }
                pes.limit(newLimit);

                raw264.put(pes.slice());
                pes.position(pes.limit());
                pes.limit(pesLimit);
                int nextMarkerPosition = nextPsMarkerPosition(pes);
                i = nextMarkerPosition;
                pes.position(i);
                pes.limit(pesLimit);
            } else {
                i++;
            }
        }
        raw264.flip();
        return firstPts;
    }

    public List<ByteBuffer> getVideoFramePackets(ByteBuffer ts, LongArrayList timestamps) {
        int limit = ts.limit();
        List<ByteBuffer> output = new ArrayList<ByteBuffer>();

        ByteBuffer pes;
        tmp.clear();
        if (tmp.capacity() >= limit) {
            pes = (ByteBuffer) tmp.limit(limit);
        } else {
            pes = tmp = ByteBuffer.allocate(limit);
        }
        int videoPid = -1;
        long firstPts = -1;
        TSPkt pkt = new TSPkt();
        for (int pos = ts.position(); pos < limit; pos += 188) {
            ts.clear();
            ts.position(pos);
            ts.limit(pos + 188);
            TSPkt.parsePacket(pkt, ts);
            PESPacket pesPkt = pesPacket(pkt);
            if (pesPkt != null && PESPacket.isVideo(pesPkt.streamId)) {
                if (videoPid == -1) {
                    videoPid = pkt.pid;
                    firstPts = pesPkt.pts;
                }
                timestamps.add(pesPkt.pts);
            }
            ByteBuffer payload = pkt.payload();
            if (pkt.pid == videoPid && payload != null) {
                pes.put(payload);
            }
        }
        pes.flip();

        int pesLimit = pes.limit();
        PESPacket pespkt = null;
        for (int i = pes.position(); i < pesLimit - 4;) {
            int marker = pes.getInt(i);
            if (psMarker(marker)) {
                pespkt = readPESHeader(pes, 0);
                int newLimit;
                if (pespkt.payloadSize == 0) {
                    // If the PES packet length is set to zero, the PES packet
                    // can be of any length.
                    // A value of zero for the PES packet length can be used
                    // only when the PES packet payload is a video elementary
                    // stream.
                    newLimit = nextPsMarkerPosition(pes);
                } else {
                    newLimit = i + PESPacket.PES_HEADER_SIZE + pespkt.payloadSize;
                }
                pes.limit(newLimit);

                output.add(pes.slice());
                pes.position(pes.limit());
                pes.limit(pesLimit);
                int nextMarkerPosition = nextPsMarkerPosition(pes);
                i = nextMarkerPosition;
                pes.position(i);
                pes.limit(pesLimit);
            } else {
                i++;
            }
        }
        return output;
    }

    public long raw264(ByteBuffer ts, ByteBuffer raw264, LongArrayList timestamps) {
        int limit = ts.limit();

        ByteBuffer pes;
        tmp.clear();
        if (tmp.capacity() >= limit) {
            pes = (ByteBuffer) tmp.limit(limit);
        } else {
            pes = tmp = ByteBuffer.allocate(limit);
        }
        int videoPid = -1;
        long firstPts = -1;
        TSPkt pkt = new TSPkt();
        for (int pos = ts.position(); pos < limit; pos += 188) {
            ts.clear();
            ts.position(pos);
            ts.limit(pos + 188);
            TSPkt.parsePacket(pkt, ts);
            PESPacket pesPkt = pesPacket(pkt);
            if (pesPkt != null && PESPacket.isVideo(pesPkt.streamId)) {
                if (videoPid == -1) {
                    videoPid = pkt.pid;
                    firstPts = pesPkt.pts;
                }
                timestamps.add(pesPkt.pts);
            }
            ByteBuffer payload = pkt.payload();
            if (pkt.pid == videoPid && payload != null) {
                pes.put(payload);
            }
        }
        pes.flip();

        int pesLimit = pes.limit();
        PESPacket pespkt = null;
        for (int i = pes.position(); i < pesLimit - 4;) {
            int marker = pes.getInt(i);
            if (psMarker(marker)) {
                pespkt = readPESHeader(pes, 0);
                int newLimit;
                if (pespkt.payloadSize == 0) {
                    // If the PES packet length is set to zero, the PES packet
                    // can be of any length.
                    // A value of zero for the PES packet length can be used
                    // only when the PES packet payload is a video elementary
                    // stream.
                    newLimit = nextPsMarkerPosition(pes);
                } else {
                    newLimit = i + PESPacket.PES_HEADER_SIZE + pespkt.payloadSize;
                }
                pes.limit(newLimit);

                raw264.put(pes.slice());
                pes.position(pes.limit());
                pes.limit(pesLimit);
                int nextMarkerPosition = nextPsMarkerPosition(pes);
                i = nextMarkerPosition;
                pes.position(i);
                pes.limit(pesLimit);
            } else {
                i++;
            }
        }
        raw264.flip();
        return firstPts;
    }

    /**
     * 
     * @deprecated
     * @param ts
     * @param raw264
     * @return first pts
     */
    public static long demux264(ByteBuffer ts, ByteBuffer raw264) {
        return new TSParser().raw264(ts, raw264);
    }

    private final static boolean DEBUG = false;

    private static void d(Object string) {
        if (DEBUG) {
            System.out.println(string);
        }

    }
}
