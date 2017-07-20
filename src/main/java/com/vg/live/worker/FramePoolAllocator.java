package com.vg.live.worker;

import java.nio.ByteBuffer;

import com.vg.live.video.AVFrame;
import com.vg.live.video.PESPacket;
import com.vg.live.video.TSPkt;

public class FramePoolAllocator implements Allocator {

    @Override
    public ByteBuffer copy(ByteBuffer b) {
        return FramePool.copy(b);
    }

    @Override
    public ByteBuffer acquire(int size) {
        return FramePool.acquire(size);
    }

    @Override
    public void release(ByteBuffer b) {
        FramePool.release(b);
    }

    @Override
    public void releaseData(AVFrame f) {
        FramePool.releaseData(f);
    }

    @Override
    public void releaseData(TSPkt pkt) {
        FramePool.releaseData(pkt);
    }

    @Override
    public void releaseData(PESPacket pespkt) {
        FramePool.releaseData(pespkt);
    }

    @Override
    public ByteBuffer copy(byte[] data, int offset, int length) {
        return FramePool.copy(data, offset, length);
    }

}
