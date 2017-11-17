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

    @Test
    public void defaultSampleSize() {
        File init = new File("testdata/trun/stream-0.0.m4s");
        File m4s = new File("testdata/trun/stream-0.42.m4s");
        File file = new File("testdata/trun/stream-0.116.m4s");

        Observable<ByteBuffer> bufs = just(init, m4s, file).map(f1(f -> {
            System.out.println("f=" + f.getAbsolutePath());
            return wrap(readFileToByteArray(f));
        }));
        Observable<AVFrame> frames = framesFromDashBuffers(bufs);
        int first = frames.count().toBlocking().first();

        Assert.assertEquals(121, first);
    }
}