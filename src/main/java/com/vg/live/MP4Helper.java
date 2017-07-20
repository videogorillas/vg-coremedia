package com.vg.live;

import com.vg.util.TimeUtil;

import org.apache.commons.io.IOUtils;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.common.AutoFileChannelWrapper;
import org.jcodec.common.IntArrayList;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Rational;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.MP4Util;
import org.jcodec.containers.mp4.MP4Util.Atom;
import org.jcodec.containers.mp4.boxes.AudioSampleEntry;
import org.jcodec.containers.mp4.boxes.Box;
import org.jcodec.containers.mp4.boxes.ChunkOffsetsBox;
import org.jcodec.containers.mp4.boxes.CompositionOffsetsBox;
import org.jcodec.containers.mp4.boxes.Edit;
import org.jcodec.containers.mp4.boxes.MediaBox;
import org.jcodec.containers.mp4.boxes.MediaHeaderBox;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.NodeBox;
import org.jcodec.containers.mp4.boxes.SampleEntry;
import org.jcodec.containers.mp4.boxes.SampleSizesBox;
import org.jcodec.containers.mp4.boxes.SampleToChunkBox;
import org.jcodec.containers.mp4.boxes.SampleToChunkBox.SampleToChunkEntry;
import org.jcodec.containers.mp4.boxes.SyncSamplesBox;
import org.jcodec.containers.mp4.boxes.TimeToSampleBox;
import org.jcodec.containers.mp4.boxes.TimeToSampleBox.TimeToSampleEntry;
import org.jcodec.containers.mp4.boxes.TrakBox;
import org.jcodec.containers.mp4.boxes.VideoSampleEntry;
import org.jcodec.containers.mp4.demuxer.AbstractMP4DemuxerTrack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import rx.Observable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.primitives.Ints.checkedCast;
import static java.util.Collections.emptyMap;
import static org.jcodec.common.IntArrayList.createIntArrayList;
import static org.jcodec.containers.mp4.boxes.Box.path;
import static rx.Observable.from;
import static rx.observables.SyncOnSubscribe.createStateless;

public class MP4Helper {
    MovieBox movieBox;

    public MP4Helper(MovieBox movieBox) {
        this.movieBox = movieBox;
    }

    public boolean isEqualFrameDurations() throws IOException {
        TrakBox videoTrack = movieBox.getVideoTrack();
        TimeToSampleBox stts = getStts(videoTrack);
        return isEqualSampleDurations(stts);
    }

    public static boolean isEqualSampleDurations(TimeToSampleBox stts) {
        TimeToSampleEntry[] entries = stts.getEntries();
        if (entries.length == 1)
            return true;
        Histo<Integer> histo = new Histo<>();
        for (TimeToSampleEntry timeToSampleEntry : entries) {
            histo.add(timeToSampleEntry.getSampleCount(), timeToSampleEntry.getSampleDuration());
        }
        return histo.getBins() == 1;
    }

    public static MP4Helper parseFile(File file) throws IOException {
        return new MP4Helper(MP4Util.parseMovie(file));
    }

    public void setDefaultEdits() {
        int movieTimescale = movieBox.getTimescale();
        TrakBox videoTrack = movieBox.getVideoTrack();
        long videoMediaTimescale = videoTrack.getTimescale();
        long videoMediaDuration = videoTrack.getMediaDuration();
        long videoDuration = TimeUtil.convertTimescale(videoMediaDuration, videoMediaTimescale, movieTimescale);
        videoTrack.setEdits(Arrays.asList(new Edit(videoDuration, 0, 1)));

        for (TrakBox audioTrack : movieBox.getAudioTracks()) {
            long mediaDuration = audioTrack.getMediaDuration();
            int mediaTimescale = audioTrack.getTimescale();
            Rational r = new Rational(checkedCast(mediaDuration), mediaTimescale);
            long audioDuration = TimeUtil.convertTimescale(r.getNum(), r.getDen(), movieTimescale);
            audioTrack.setEdits(Arrays.asList(new Edit(audioDuration, 0, 1)));
        }
    }

    public void setEqualFrameDurations() {
        TrakBox videoTrack = movieBox.getVideoTrack();
        MediaBox mdia = videoTrack.getMdia();
        NodeBox stbl = mdia.getMinf().getStbl();

        TimeToSampleBox stts = NodeBox.findFirst(stbl, TimeToSampleBox.class, "stts");
        TimeToSampleEntry[] entries = stts.getEntries();

        Histo<Integer> histo = new Histo<>();
        for (TimeToSampleEntry timeToSampleEntry : entries) {
            histo.add(timeToSampleEntry.getSampleCount(), timeToSampleEntry.getSampleDuration());
        }
        for (TimeToSampleEntry timeToSampleEntry : entries) {
            timeToSampleEntry.setSampleDuration(histo.getMostFrequentValue());
        }
        CompositionOffsetsBox ctts = NodeBox.findFirst(stbl, CompositionOffsetsBox.class, "ctts");
        if (ctts != null) {
            for (CompositionOffsetsBox.Entry entry : ctts.getEntries()) {
                entry.offset = histo.getMostFrequentValue();
            }
        }
    }

    public void replaceHeader(File mp4) throws IOException {
        replaceHeader(mp4, movieBox);
    }
    
    public static MovieBox parseMoov(ByteBuffer bb) throws IOException {
        try (SeekableByteChannel sbc = new ByteBufferSeekableByteChannel(bb)) {
            return MP4Util.parseMovieChannel(sbc);
        }
    }

    public static boolean hasAudio(MovieBox moov) {
        return moov != null && !moov.getAudioTracks().isEmpty();
    }

    public static VideoSampleEntry getVideoSampleEntry(MovieBox moov) {
        return (VideoSampleEntry) getSampleEntry(moov.getVideoTrack());
    }

    public static SampleEntry getSampleEntry(TrakBox track) {
        if (track == null) {
            return null;
        }
        SampleEntry[] sampleEntries = track.getSampleEntries();
        if (sampleEntries == null || sampleEntries.length == 0) {
            return null;
        }
        SampleEntry sampleEntry = sampleEntries[0];
        if (sampleEntry == null) {
            return null;
        }
        return sampleEntry;
    }

    public static AudioSampleEntry getAudioSampleEntry(MovieBox moov) {
        return (AudioSampleEntry) MP4Helper.getSampleEntry(firstElement(moov.getAudioTracks()));
    }

    private static <T> T firstElement(List<T> list) {
        return list != null && list.size() > 0 ? list.get(0) : null;
    }

    public static Observable<MP4Packet> packets(AbstractMP4DemuxerTrack track) {
        return Observable.create(createStateless(o -> {
            try {
                MP4Packet nextFrame = (MP4Packet) track.nextFrame();
                if (nextFrame != null) {
                    o.onNext(nextFrame);
                } else {
                    o.onCompleted();
                }
            } catch (IOException e) {
                o.onError(e);
            }
        }));
    }

    public static void writeTrackData(WritableByteChannel channel, Iterable<? extends Packet> videoTrack2) throws IOException {
        for (Packet packet : videoTrack2) {
            ByteBuffer data = packet.getData();
            if (data.getInt(0) == 0x00000001) {
                H264Utils.encodeMOVPacket(data);
            }
            while (data.hasRemaining()) {
                channel.write(data);
            }
        }
    }

    public static void write(File dstFile, Box... boxes) throws FileNotFoundException, IOException {
        write(dstFile, Arrays.asList(boxes));
    }

    public static void write(File dstFile, Iterable<Box> boxes) throws FileNotFoundException, IOException {
        FileOutputStream out = new FileOutputStream(dstFile);
        FileChannel channel = out.getChannel();
        ByteBuffer buf = ByteBuffer.allocate(512 * 1024);
        try {
            for (Box box : boxes) {
                buf.clear();
                box.write(buf);
                buf.flip();
                while (buf.hasRemaining()) {
                    channel.write(buf);
                }
            }
        } finally {
            channel.close();
            out.close();
        }
    }

    public static String videoResolution(MovieBox moov) {
        if (!hasVideo(moov)) {
            return null;
        }
        try {
            int w = (int) moov.getVideoTrack().getTrackHeader().getWidth();
            int h = (int) moov.getVideoTrack().getTrackHeader().getHeight();
            return w + "x" + h;
        } catch (Exception e) {
        }
        return null;
    }

    public static boolean hasVideo(MovieBox moov) {
        return moov != null && moov.getVideoTrack() != null;
    }

    public static MovieBox moov(File input) {
        try {
            return MP4Util.parseMovie(input);
        } catch (IOException e) {
            throw new RuntimeException("moov " + input, e);
        }
    }

    public static int firstTrackTimescale(MovieBox moov) {
        return moov.getTracks()[0].getTimescale();
    }

    public static long firstTrackDuration(MovieBox moov) {
        return moov.getTracks()[0].getMediaDuration();
    }

    public static FileChannel openReadOnly(File file) throws FileNotFoundException {
        return new FileInputStream(file).getChannel();
    }

    public static FileChannel openReadWrite(File file) throws FileNotFoundException {
        return new RandomAccessFile(file, "rw").getChannel();
    }

    public static Map<String, Box> rootBoxes(File srcFile) throws IOException {
        try (FileChannel channel = openReadOnly(srcFile);
             FileChannelWrapper ch = new FileChannelWrapper(channel)) {
            List<Atom> rootAtoms = MP4Util.getRootAtoms(ch);
            Map<String, Box> boxes = from(rootAtoms)
                    .filter(a -> !a.getHeader().getFourcc().equals("mdat"))
                    .toMap(a -> a.getHeader().getFourcc(), a -> {
                        try {
                            return a.parseBox(ch);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return null;
                    }).toBlocking().firstOrDefault(emptyMap());
            return boxes;
        }

    }

    public static void replaceBoxes(File dstFile, Box... boxes) throws IOException {
        Map<String, Atom> atoms = rootAtomsMap(dstFile);
        try (FileChannel channel = openReadWrite(dstFile)) {
            ByteBuffer buf = ByteBuffer.allocate(64 * 1024);
            for (Box box : boxes) {
                Atom atom = atoms.get(box.getHeader().getFourcc());
                if (atom == null) {
                    throw new NoSuchElementException(box.getHeader().getFourcc());
                }
                buf.clear();
                box.write(buf);
                buf.flip();
                if (atom.getHeader().getSize() != buf.limit()) {
                    throw new IllegalArgumentException("cant replace box " + box.getHeader().getFourcc());
                }
                channel.position(atom.getOffset());
                while (buf.hasRemaining()) {
                    channel.write(buf);
                }
            }
        }
    }

    public static Map<String, Atom> rootAtomsMap(File dstFile) throws IOException {
        try (FileChannelWrapper ch = new FileChannelWrapper(openReadOnly(dstFile))) {
            return from(MP4Util.getRootAtoms(ch)).toMap(a -> a.getHeader().getFourcc(), a -> a).toBlocking().firstOrDefault(emptyMap());
        }
    }

    public static void cutAudioFrames(int cutFirstFrames, MovieBox moov) {
        TrakBox trak = moov.getAudioTracks().get(0);
        MediaBox mdia = trak.getMdia();
        MediaHeaderBox mdhd = NodeBox.findFirst(mdia, MediaHeaderBox.class, "mdhd");
        TimeToSampleBox stts = getStts(trak);
        SampleToChunkBox stsc = getStsc(trak);
        SampleSizesBox stsz = getStsz(trak);
        ChunkOffsetsBox stco = getStco(trak);

        TimeToSampleEntry timeToSampleEntry = stts.getEntries()[0];
        int count = timeToSampleEntry.getSampleCount() - cutFirstFrames;
        int duration = count * 1024;
        trak.setDuration(duration);
        mdhd.setDuration(duration);
        timeToSampleEntry.setSampleCount(count);

        SampleToChunkEntry sampleToChunkEntry = stsc.getSampleToChunk()[0];
        sampleToChunkEntry.setCount(sampleToChunkEntry.getCount() - cutFirstFrames);

        int[] sizes = stsz.getSizes();

        long offset = stco.getChunkOffsets()[0];
        for (int i = 0; i < cutFirstFrames; i++) {
            offset += sizes[i];
        }
        stco.getChunkOffsets()[0] = offset;

        int[] copyOfRange = Arrays.copyOfRange(sizes, cutFirstFrames, sizes.length);
        stsz.setSizes(copyOfRange);
    }

    public static void replaceHeader(File mp4, MovieBox moov) throws IOException {
        FileChannel output = null;
        try {
            List<Atom> rootAtoms = getRootAtoms(mp4);
            checkState(rootAtoms != null && !rootAtoms.isEmpty(), "mp4 " + mp4.getAbsolutePath() + " has no atoms");
            Atom moovAtom = checkNotNull(moovAtom(rootAtoms), "mp4 " + mp4.getAbsolutePath() + " has no moov atom");

            boolean moovAtEnd = from(rootAtoms)
                    .filter(a -> !"free".equals(a.getHeader().getFourcc()))
                    .reduce((acc, cur) -> acc.getOffset() > cur.getOffset() ? acc : cur)
                    .map(x -> "moov".equals(x.getHeader().getFourcc())).toBlocking().firstOrDefault(false);

            long origsize = moovAtom.getHeader().getSize();
            ByteBuffer buf = ByteBuffer.allocate((int) (origsize * 2));
            moov.write(buf);
            buf.flip();
            int newsize = buf.limit();
            if (origsize >= newsize || moovAtEnd) {
                long freeSize = origsize - newsize;
                output = openReadWrite(mp4);
                output.position(moovAtom.getOffset());
                while (buf.hasRemaining()) {
                    output.write(buf);
                }
                if (freeSize >= 8) {
                    ByteBuffer freeBuf = ByteBuffer.allocate(8);
                    freeBuf.putInt((int) freeSize);
                    freeBuf.put("free".getBytes());
                    freeBuf.flip();
                    output.write(freeBuf);
                } else if (freeSize > 0 && moovAtEnd) {
                    output.truncate(output.position());
                } else if (freeSize > 0) {
                    throw new RuntimeException("TODO: rewrite mp4: freeSize > 0 and moov not at end of file");
                }
            } else {
                throw new RuntimeException("TODO: rewrite mp4");
            }
        } finally {
            IOUtils.closeQuietly(output);
        }
    }

    private static Atom moovAtom(List<Atom> rootAtoms) {
        for (Atom atom : rootAtoms) {
            if ("moov".equals(atom.getHeader().getFourcc())) {
                return atom;
            }
        }
        return null;
    }

    private static List<Atom> getRootAtoms(File mp4) throws IOException {
        try (AutoFileChannelWrapper input = new AutoFileChannelWrapper(mp4);) {
            List<Atom> rootAtoms = MP4Util.getRootAtoms(input);
            return rootAtoms;
        }
    }

    public static int[] getSampleDurations(TimeToSampleBox stts) {
        IntArrayList sampleDurations = createIntArrayList();
        for (TimeToSampleEntry ttse : stts.getEntries()) {
            int sampleCount = ttse.getSampleCount();
            int sampleDuration = ttse.getSampleDuration();
            for (int i = 0; i < sampleCount; i++) {
                sampleDurations.add(sampleDuration);
            }
        }
        return sampleDurations.toArray();
    }

    public static SampleSizesBox getStsz(TrakBox videoTrack) {
        return NodeBox.findFirstPath(videoTrack, SampleSizesBox.class, path("mdia.minf.stbl.stsz"));
    }

    public static SyncSamplesBox getStss(TrakBox videoTrack) {
        return NodeBox.findFirstPath(videoTrack, SyncSamplesBox.class, path("mdia.minf.stbl.stss"));
    }

    public static TimeToSampleBox getStts(TrakBox track) {
        return NodeBox.findFirstPath(track, TimeToSampleBox.class, path("mdia.minf.stbl.stts"));
    }

    public static SampleToChunkBox getStsc(TrakBox track) {
        return NodeBox.findFirstPath(track, SampleToChunkBox.class, path("mdia.minf.stbl.stsc"));
    }

    public static ChunkOffsetsBox getStco(TrakBox track) {
        return NodeBox.findFirstPath(track, ChunkOffsetsBox.class, path("mdia.minf.stbl.stco"));
    }

    public static Atom findMoov(File mp4) throws IOException {
        return MP4Util.findFirstAtomInFile("moov", mp4);
    }

}
