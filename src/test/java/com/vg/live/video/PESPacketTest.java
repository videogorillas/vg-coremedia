package com.vg.live.video;

import static com.vg.live.video.PESPacket.nextPsMarkerPosition;
import static com.vg.live.video.PESPacket.psMarker;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;

import org.junit.Test;

public class PESPacketTest {
    @Test
    public void testNextPsMarkerPosition() {
        ByteBuffer buf = ByteBuffer.allocate(20);
        buf.putInt(0, PESPacket.VIDEO_MAX);
        buf.putInt(7, PESPacket.VIDEO_MAX);
        buf.putInt(14, PESPacket.VIDEO_MAX);
        assertTrue(psMarker(buf.getInt(0)));
        assertEquals(0, nextPsMarkerPosition(buf));
        buf.position(4);
        assertEquals(7, nextPsMarkerPosition(buf));
        buf.position(8);
        assertEquals(14, nextPsMarkerPosition(buf));
    }

}
