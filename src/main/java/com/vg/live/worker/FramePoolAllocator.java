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
        int rc = f.refCount.decrementAndGet();
        if (rc == 0) {
            FramePool.releaseData(f);
        } else if (rc < 0) {
            throw new IllegalStateException("over releasing (refcnt=" + rc + ") frame " + f);
        }
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
