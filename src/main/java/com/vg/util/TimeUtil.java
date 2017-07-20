package com.vg.util;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class TimeUtil {

    public final static long MAC_EPOCH_OFFSET;
    static {
        TimeZone timeZone = TimeZone.getTimeZone("GMT");
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.set(1904, 0, 1, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        MAC_EPOCH_OFFSET = calendar.getTimeInMillis();
    }

    public static String hms(long sec) {
        long s = sec % 60;
        long m = sec % 3600 / 60;
        long h = sec / 3600;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    public static Date macTimeToDate(long macTimeSec) {
        return new Date(macTimeToJavaTime(macTimeSec));
    }

    public static long macTimeToJavaTime(long macTimeSec) {
        return macTimeSec * 1000L + MAC_EPOCH_OFFSET;
    }

    public static long javaTimeToMacTime(long millis) {
        return (millis - MAC_EPOCH_OFFSET) / 1000L;
    }

    public static long parseTime(String hmsms) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.parse(hmsms).getTime();
    }

    private final static String[] zeroToNine = {"00", "01", "02", "03", "04", "05", "06", "07", "08", "09"};

    public static String hmsms(long msec) {
        long sec = msec / 1000;
        int ms = (int) (msec % 1000);
        int s = (int) (sec % 60);
        int m = (int) (sec % 3600 / 60);
        int h = (int) (sec / 3600);

        StringBuilder sb = new StringBuilder(12);
        sb  = h < 10 ? sb.append(zeroToNine[h]) : sb.append(h);
        sb.append(":");
        sb  = m < 10 ? sb.append(zeroToNine[m]) : sb.append(m);
        sb.append(":");
        sb  = s < 10 ? sb.append(zeroToNine[s]) : sb.append(s);
        sb.append(".");
        if (ms >= 100) {
            sb.append(ms);
        } else if (ms >= 10) {
            sb.append('0').append(ms);
        } else {
            sb.append("00").append(ms);
        }
        return sb.toString();
    }

    public static double pts(int m, int s) {
        return 60. * m + s;
    }

    /** Emulates ndftc */
    public static String ndftc(long msec) {
        return hmsms(msec).replace('.', ':').substring(0, 11);
    }

    public static int ndftc(String str, int fps) {
        String[] split = str.trim().split(":");
        int h = parseInt(split[0]) * 3600;
        int m = parseInt(split[1]) * 60;
        int s = parseInt(split[2]);
        int f = parseInt(split[3]);
        return (h + m + s) * fps + f;
    }

    public static String ndftc(int fn, int fps) {
        int f = fn % fps;
        int ss = (fn / fps);
        int s = ss % 60;
        int m = (ss / 60) % 60;
        int h = (ss / 3600) % 60;
        return String.format("%02d:%02d:%02d:%02d", h, m, s, f);
    }

    public static String hmsf(long msec, int fps) {
        long sec = msec / 1000;
        long ms = msec % 1000;
        long s = sec % 60;
        long m = sec % 3600 / 60;
        long h = sec / 3600;
        long f = ms * fps / 1000L;
        return String.format("%02d:%02d:%02d:%02d", h, m, s, f);
    }

    /**
     *
     * @param hms
     *            "01:02:03"
     * @return number of seconds
     */
    public static int fromHMS(String hms) {
        String[] split = hms.split(":", 3);
        int h = Integer.parseInt(split[0]);
        int m = Integer.parseInt(split[1]);
        int s = Integer.parseInt(split[2]);
        return h * 3600 + m * 60 + s;
    }

    public static List<String> createNdftcs(int offset, int fps, int count) {
        List<String> ndftcs = new ArrayList<String>();
        for (int fn = offset; fn <= offset + count; fn++) {
            ndftcs.add(ndftc(fn, fps));
        }
        return ndftcs;
    }

    public static String timecodeToString(long timeCode, boolean dropFrame, int timecodeRate) {
        if (dropFrame) {
            long D = timeCode / 17982;
            long M = timeCode % 17982;
            timeCode += 18 * D + 2 * ((M - 2) / 1798);
        }

        long frames = timeCode % timecodeRate;
        timeCode /= timecodeRate;
        long seconds = timeCode % 60;
        timeCode /= 60;
        long minutes = timeCode % 60;
        timeCode /= 60;
        long hours = timeCode % 24;
        String tcfmt = dropFrame ? "%02d:%02d:%02d;%02d" : "%02d:%02d:%02d:%02d";
        return format(tcfmt, hours, minutes, seconds, frames);
    }

    public static boolean isDropFrame(String tc) {
        return tc.indexOf(';') == 8;
    }

    public static String toDropFrame(String tc) {
        if (!isDropFrame(tc)) {
            char[] charArray = tc.toCharArray();
            charArray[8] = ';';
            tc = new String(charArray);
        }
        return tc;
    }

    public static String toNonDropFrame(String tc) {
        if (isDropFrame(tc)) {
            char[] charArray = tc.toCharArray();
            charArray[8] = ':';
            tc = new String(charArray);
        }
        return tc;
    }

    public static long parseTimecode(String tc, int timecodeRate) {
        return parseTimecode(tc, timecodeRate, isDropFrame(tc));
    }

    public static long parseTimecode(String tc, int timecodeRate, boolean dropFrame) {
        String[] split = tc.split("[:;]", 4);
        int h = parseInt(split[0]);
        int m = parseInt(split[1]);
        int s = parseInt(split[2]);
        int f = parseInt(split[3]);
        long timeCode = (h * 3600 + m * 60 + s) * timecodeRate + f;
        if (dropFrame) {
            long D = timeCode / 17982;
            long M = timeCode % 17982;
            timeCode -= 18 * D + 2 * ((M - 2) / 1798);
        }
        return timeCode;
    }

    /**
     *
     * @param srcTv
     *            audio time value
     * @param srcTs
     *            audio time scale
     * @param dstTs
     *            video time scale
     * @return
     */
    public static long convertTimescale(long srcTv, long srcTs, long dstTs) {
        return srcTv * dstTs / srcTs;
    }

}
