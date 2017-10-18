package com.vg.live.worker;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.primitives.Ints;
import com.vg.live.video.AVFrame;
import com.vg.live.video.PESPacket;
import com.vg.live.video.TSPkt;
import com.vg.util.MappedByteBufferPool;

public class FramePool {
    static MappedByteBufferPool pool = new MappedByteBufferPool(1024);
    public static ByteBuffer acquire(int size) {
        ByteBuffer acquire = pool.acquire(nextPowerOfTwo(size), false);
        acquire.position(0);
        acquire.limit(size);
        stats.acquired.addAndGet(acquire.capacity());
        return acquire;
    }

    public static void release(ByteBuffer buf) {
        pool.release(buf);
        stats.released.addAndGet(buf.capacity());
    }

    public static ByteBuffer copy(ByteBuffer payload) {
        payload.mark();
        ByteBuffer acquire = acquire(payload.remaining());
        acquire.put(payload);
        payload.reset();
        acquire.position(0);
        return acquire;
    }

    public static ByteBuffer copy(byte[] data, int off, int len) {
        ByteBuffer acquire = acquire(len);
        acquire.put(data, off, len);
        acquire.position(0);
        return acquire;
    }

    public static class Stats {
        public final AtomicLong acquired = new AtomicLong();
        public final AtomicLong released = new AtomicLong();

        @Override
        public String toString() {
            return "Stats [acquired=" + acquired + ", released=" + released + "]";
        }

    }

    static Stats stats = new Stats();

    public static Stats stats() {
        return stats;
    }

    public static ByteBuffer readFile(File file) throws IOException {
        checkNotNull(file);
        checkArgument(file.isFile() && file.canRead() && file.exists(), "cant read file %s", file);
        checkArgument(file.length() != 0, "BUG: zero length file %s", file);
        int size = Ints.checkedCast(file.length());
        ByteBuffer byteBuffer = acquire(size);

        try (FileInputStream in = new FileInputStream(file); FileChannel ch = in.getChannel();) {
            while (-1 != ch.read(byteBuffer)) {
                byteBuffer.limit(byteBuffer.capacity());
            }
        }
        byteBuffer.flip();
        return byteBuffer;
    }

    public static void releaseData(PESPacket pespkt) {
        release(pespkt._payload);
        pespkt._payload = Allocator.RELEASED;
    }

    public static void releaseData(AVFrame f) {
        if (f._data != Allocator.RELEASED) {
            release(f._data);
            f._data = Allocator.RELEASED;
        }
    }

    public static void releaseData(TSPkt pkt) {
        release(pkt._data);
        pkt._data = Allocator.RELEASED;
    }
    
    public static int nextPowerOfTwo(int n) {
        n = n - 1;
        n = n | (n >> 1);
        n = n | (n >> 2);
        n = n | (n >> 4);
        n = n | (n >> 8);
        n = n | (n >> 16);
        n = n + 1;
        return n;
    }
}
