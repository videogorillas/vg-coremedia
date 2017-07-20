package com.vg.live.video;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.common.LongArrayList;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.tools.MD5;
import org.junit.Assert;
import org.junit.Test;

import com.vg.live.video.H264Utils;
import com.vg.live.video.NAL;
import com.vg.live.video.TSParser;

public class TSParserTest {
    static FileFilter endsWithTs = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            return pathname.getName().endsWith(".ts");
        }
    };

    @Test
    public void testRaw264() throws Exception {
        File dir = new File("testdata/25fps");
        File[] listFiles = dir.listFiles(endsWithTs);
        Arrays.sort(listFiles, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return Integer.parseInt(o1.getName().split("\\.")[0]) - Integer.parseInt(o2.getName().split("\\.")[0]);
            }
        });
        Assert.assertTrue(listFiles.length > 0);

        long expectedFirstPts[] = new long[] { 7305792, 7334592, 7363392, 7392192, 7420992, 7449792, 7478592, 7507392,
                7536192, 7564992, 7593792, 7622592, 7651392, 7680192, 7708992, 7737792, 7766592, 7795392, 0, 7852992, };
        int expectedRemaining[] = new int[] { 23924, 23770, 25461, 23231, 23841, 23288, 25364, 33954, 23235, 21803,
                29928, 31016, 22711, 18136, 18314, 18488, 26724, 21940, 0, 29487 };
        String expectedMD5[] = { "08c978f5e1274cc42f3bef107f0c24c7", "1744e2924d418efdcfec017a55e32a71",
                "bd392c6a5c6a5016f33196754d4c4b4e", "858513d1e09856eaab20888750929a0a",
                "559c7bd2928932ddd07567e0a04424d3", "b2b00c68793bcd0bca530a4c44a77b6e",
                "5677941a0e5b56eb080a97fb7cf5071b", "324114bec6a58e8a429aaa1bf2838d55",
                "3d6b41413e0a55a472e45a366a0d39ed", "bcf81f40f6aaf74461baed0dbed57411",
                "63e5622ff2a76f39f6b4258b5d3c99a6", "8ce72bda9d6463c86fb90535e6b95a56",
                "1844f0c3af36e73c3ac8326c76dd9516", "ece37cf5c230195d4d81401550368f35",
                "242177a0899ccc81e575ef1dfee62830", "2a25c8d898cd88422dde38325cd19f2c",
                "a27e4d66dce5e6526cf9b92dad3e8512", "a8876a81f1e071c03be48a932bfa3302",
                "d41d8cd98f00b204e9800998ecf8427e", "5f57939aed77ea339b075212912f6ab5", };

        TSParser parser = new TSParser();
        for (int i = 0; i < listFiles.length; i++) {
            File file = listFiles[i];
            ByteBuffer mmap = mmap(file);
            ByteBuffer raw264 = ByteBuffer.allocate(mmap.capacity());
            long firstPts = parser.raw264(mmap, raw264);
            assertEquals(expectedFirstPts[i], firstPts);
            assertEquals(expectedRemaining[i], raw264.remaining());
            assertEquals(expectedMD5[i], MD5.md5sum(raw264));
        }
    }

    private ByteBuffer mmap(File file) throws FileNotFoundException, IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        ByteBuffer mmap = fileInputStream.getChannel().map(MapMode.READ_ONLY, 0, file.length());
        fileInputStream.close();
        return mmap;
    }

    @Test
    public void testRaw264Timestamps() throws Exception {
        File tsFile = new File("testdata/25fps/1010.ts");
        TSParser p = new TSParser();
        ByteBuffer raw264 = ByteBuffer.allocate((int) tsFile.length());
        LongArrayList timestamps = new LongArrayList(128);
        p.raw264(NIOUtils.mapFile(tsFile), raw264, timestamps);
        System.out.println(raw264);
        long[] expectedTimestamps = new long[] { 7305792, 7309392, 7312992, 7316592, 7320192, 7323792, 7327392,
                7330992 };
        Assert.assertArrayEquals(expectedTimestamps, timestamps.toArray());
        List<NAL> splitNalUnits = H264Utils.splitNalUnits(raw264);
        int pictures = 0;
        for (NAL nal : splitNalUnits) {
            System.out.println(nal.type + " " + nal.nalData);
            pictures += nal.type == NALUnitType.IDR_SLICE || nal.type == NALUnitType.NON_IDR_SLICE ? 1 : 0;
        }
        assertEquals(expectedTimestamps.length, pictures);
    }

    @Test
    public void testVideoFrames() throws Exception {
        File tsFile = new File("testdata/25fps/1010.ts");
        TSParser p = new TSParser();
        ByteBuffer ts = NIOUtils.mapFile(tsFile);
        LongArrayList timestamps = new LongArrayList(128);
        List<ByteBuffer> videoFramePackets = p.getVideoFramePackets(ts, timestamps);
        long[] expectedTimestamps = new long[] { 7305792, 7309392, 7312992, 7316592, 7320192, 7323792, 7327392,
                7330992 };
        Assert.assertArrayEquals(expectedTimestamps, timestamps.toArray());
        assertEquals(expectedTimestamps.length, videoFramePackets.size());
        for (ByteBuffer raw264 : videoFramePackets) {
            System.out.println(raw264);
            List<NAL> splitNalUnits = H264Utils.splitNalUnits(raw264);
            for (NAL nal : splitNalUnits) {
                System.out.println("    " + nal.type + " " + nal.nalData);
            }
        }
    }

}
