package com.vg.live.video;

import static com.vg.live.video.NAL.splitNal;
import static java.nio.ByteBuffer.wrap;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.Test;

public class NALTest {
    @Test
    public void testSplit() throws Exception {
        List<ByteBuffer> splitNal = splitNal(wrap(new byte[] { 0, 0, 0, 1, 2, 9, 0x10, 0, 0, 0, 1, 2, 9, 0x10 }));
        assertEquals(2, splitNal.size());
        assertEquals(1, splitNal.get(0).getInt());
        assertEquals(1, splitNal.get(1).getInt());
        
    }

}
