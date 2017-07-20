package com.vg.live.worker;

import java.nio.ByteBuffer;

import com.vg.live.video.AVFrame;
import com.vg.live.video.PESPacket;
import com.vg.live.video.TSPkt;

public interface Allocator {
    ByteBuffer RELEASED = ByteBuffer.allocate(0);

    ByteBuffer copy(ByteBuffer b);

    ByteBuffer acquire(int size);

    void release(ByteBuffer b);
    
    void releaseData(AVFrame f);

    void releaseData(TSPkt pkt);

    void releaseData(PESPacket pespkt);

    ByteBuffer copy(byte[] data, int offset, int length);
}
