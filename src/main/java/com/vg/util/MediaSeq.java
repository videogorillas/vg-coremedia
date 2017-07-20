package com.vg.util;

public class MediaSeq<T> {
    public final static int MEDIASEQ_UNKNOWN = -1;
    public int mseq;
    public T value;

    public static <T> MediaSeq<T> mediaSeq(int mseq, T value) {
        MediaSeq<T> m = new MediaSeq<>();
        m.mseq = mseq;
        m.value = value;
        return m;
    }

}
