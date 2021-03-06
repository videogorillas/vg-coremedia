package com.vg.live;

import static com.github.davidmoten.rx.Checked.f1;
import static com.vg.live.SuperLive.framesFromDashBuffers;
import static java.nio.ByteBuffer.wrap;
import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static rx.Observable.just;

import java.io.File;
import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import com.vg.live.video.AVFrame;

import rx.Observable;

public class SuperLiveTest {

    public static final String M4S = "testdata/trun/stream-0.42.m4s";
    public static final String M4S_INIT = "testdata/trun/stream-0.0.m4s";

    @Test
    public void sampleCount() {
        File init = new File(M4S_INIT);
        File m4s = new File(M4S);
        File file = new File("testdata/trun/stream-0.116.m4s");

        Observable<ByteBuffer> bufs = just(init, m4s, file).map(f1(f -> {
            System.out.println("f=" + f.getAbsolutePath());
            return wrap(readFileToByteArray(f));
        }));
        Observable<AVFrame> frames = framesFromDashBuffers(bufs);
        int first = frames.count().toBlocking().first();

        Assert.assertEquals(121, first);
    }

    @Test
    public void duration() {
        File init = new File(M4S_INIT);
        File m4s = new File(M4S);

        Observable<ByteBuffer> bufs = just(init, m4s)
                .doOnNext(f -> System.out.println("f=" + f.getAbsolutePath()))
                .map(f1(f -> wrap(readFileToByteArray(f))));
        Observable<AVFrame> frames = framesFromDashBuffers(bufs);
        int framesCount = frames.count().toBlocking().first();

        long duration = frames.map(avFrame -> avFrame.duration).reduce(0L, (d1, d2) -> d1 + d2).toBlocking().first();
        System.out.println("total duration " + duration);

        Assert.assertEquals(120, framesCount);
        Assert.assertEquals(512 * 120, duration);
    }
}