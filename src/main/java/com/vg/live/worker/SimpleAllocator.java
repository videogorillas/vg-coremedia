package com.vg.live.worker;

import java.nio.ByteBuffer;

import com.vg.live.video.AVFrame;
import com.vg.live.video.PESPacket;
import com.vg.live.video.TSPkt;

public class SimpleAllocator implements Allocator {
    public static final Allocator DEFAULT_ALLOCATOR = new SimpleAllocator();

    @Override
    public ByteBuffer copy(ByteBuffer b) {
        b.mark();
        ByteBuffer acquire = acquire(b.remaining());
        acquire.put(b);
        b.reset();
        acquire.position(0);
        return acquire;
    }

    @Override
    public ByteBuffer acquire(int size) {
        return ByteBuffer.allocate(size);
    }

    @Override
    public void release(ByteBuffer b) {
    }

    @Override
    public void releaseData(AVFrame f) {
    }

    @Override
    public void releaseData(TSPkt pkt) {
    }

    @Override
    public void releaseData(PESPacket pespkt) {
    }

    @Override
    public ByteBuffer copy(byte[] data, int offset, int length) {
        ByteBuffer acquire = acquire(length);
        acquire.put(data, offset, length);
        acquire.position(0);
        return acquire;
    }

}
