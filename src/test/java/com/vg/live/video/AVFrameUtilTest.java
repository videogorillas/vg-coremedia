package com.vg.live.video;

import static com.vg.util.BufferUtil.toArray;
import static okio.ByteString.decodeHex;
import static org.junit.Assert.assertArrayEquals;

import java.nio.ByteBuffer;

import org.junit.Test;

public class AVFrameUtilTest {
    @Test
    public void testAnnexb2Mp4() throws Exception {
        byte[] annexb = decodeHex("000000010209").toByteArray();
        byte[] expectedMP4 = decodeHex("000000020209").toByteArray();

        AVFrame f = AVFrame.video(0, annexb.length, false);
        f._data = ByteBuffer.wrap(annexb);
        AVFrameUtil.annexb2mp4(f);
        assertArrayEquals(expectedMP4, toArray(f.data()));
    }

}
