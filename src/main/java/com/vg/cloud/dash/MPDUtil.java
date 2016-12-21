package com.vg.cloud.dash;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.BinaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vg.ListUtils;
import com.vg.Utils;

import org.junit.Test;
import org.simpleframework.xml.core.Persister;

public class MPDUtil {
    public static File writeXml(MPD mpd, File xml) {
        try {
            new Persister().write(mpd, xml);
            return xml;
        } catch (Exception e) {
            Utils.rethrow(e);
        }
        return null;
    }

    public static MPD staticDash(int width, int height, int timescale, int frameDuration, long totalDurationMsec,
            long durationMsec, int sampleRate, int bandwidth) {
        MPD dash = new MPD();
        dash.type = "static";
        dash.minBufferTime = "PT2S";
        dash.mediaPresentationDuration = ptFromMsec(totalDurationMsec);
        dash.profiles = "urn:mpeg:dash:profile:full:2011";
        dash.Period = new MPD.Period();
        dash.Period.duration = ptFromMsec(totalDurationMsec);
        MPD.AdaptationSet AdaptationSet = new MPD.AdaptationSet();
        dash.Period.AdaptationSet = singletonList(AdaptationSet);
        AdaptationSet.segmentAlignment = "true";
        AdaptationSet.maxWidth = "" + width;
        AdaptationSet.maxHeight = "" + height;
        AdaptationSet.maxFrameRate = timescale + "/" + frameDuration;
        AdaptationSet.par = width + ":" + height;
        AdaptationSet.ContentComponent = asList(new MPD.ContentComponent("1", "video"), new MPD.ContentComponent("2", "audio"));
        AdaptationSet.SegmentTemplate = new MPD.SegmentTemplate();
        AdaptationSet.SegmentTemplate.timescale = "1000";
        AdaptationSet.SegmentTemplate.media = "dash$Number$.m4s";
        AdaptationSet.SegmentTemplate.startNumber = "1";
        AdaptationSet.SegmentTemplate.duration = "" + durationMsec;
        AdaptationSet.SegmentTemplate.initialization = "dashinit.mp4";
        MPD.Representation rep = new MPD.Representation();
        rep.id = "1";
        rep.mimeType = "video/mp4";
        rep.codecs = "avc1.640015,mp4a.40.2";
        rep.width = "" + width;
        rep.height = "" + height;
        rep.frameRate = timescale + "/" + frameDuration;
        rep.sar = "1:1";
        rep.audioSamplingRate = "" + sampleRate;
        rep.startWithSAP = "1";
        rep.bandwidth = "" + bandwidth;
        MPD.AudioChannelConfiguration ac = new MPD.AudioChannelConfiguration();
        ac.schemeIdUri = "urn:mpeg:dash:23003:3:audio_channel_configuration:2011";
        ac.value = "2";
        rep.AudioChannelConfiguration = ac;
        AdaptationSet.Representation = singletonList(rep);
        return dash;
    }

    public static MPD staticVideoDash(int width, int height, int timescale, long duration) {
        long durationMsec = duration * 1000 / timescale;
        MPD dash = new MPD();
        dash.type = "static";
        dash.minBufferTime = "PT3S";
        dash.mediaPresentationDuration = ptFromMsec(durationMsec);
        dash.profiles = "urn:mpeg:dash:profile:full:2011";
        dash.Period = new MPD.Period();
        dash.Period.duration = ptFromMsec(durationMsec);
        MPD.AdaptationSet AdaptationSet = new MPD.AdaptationSet();
        dash.Period.AdaptationSet = singletonList(AdaptationSet);
        AdaptationSet.segmentAlignment = "true";
        AdaptationSet.contentType = "video";
        AdaptationSet.bitstreamSwitching = "true";
        MPD.Representation rep = new MPD.Representation();
        rep.id = "0";
        rep.mimeType = "video/mp4";
        rep.codecs = "avc1.640015";
        rep.width = "" + width;
        rep.height = "" + height;
        rep.SegmentTemplate = new MPD.SegmentTemplate();
        rep.SegmentTemplate.timescale = "" + timescale;
        rep.SegmentTemplate.media = "chunk.m4s";
        rep.SegmentTemplate.startNumber = "1";
        rep.SegmentTemplate.initialization = "init.m4s";
        rep.SegmentTemplate.SegmentTimeline = new MPD.SegmentTimeline();
        MPD.S S = new MPD.S();
        S.t = 0L;
        S.d = duration;
        rep.SegmentTemplate.SegmentTimeline.S = singletonList(S);
        AdaptationSet.Representation = singletonList(rep);
        return dash;
    }

    public static MPD staticAudioDash(int sampleRate, long duration, int channelCount) {
        long durationMsec = duration * 1000 / sampleRate;
        MPD dash = new MPD();
        dash.type = "static";
        dash.minBufferTime = "PT3S";
        dash.mediaPresentationDuration = ptFromMsec(durationMsec);
        dash.profiles = "urn:mpeg:dash:profile:full:2011";
        dash.Period = new MPD.Period();
        dash.Period.duration = ptFromMsec(durationMsec);
        MPD.AdaptationSet AdaptationSet = new MPD.AdaptationSet();
        dash.Period.AdaptationSet = singletonList(AdaptationSet);
        AdaptationSet.segmentAlignment = "true";
        AdaptationSet.contentType = "audio";
        AdaptationSet.bitstreamSwitching = "true";
        MPD.Representation rep = new MPD.Representation();
        rep.id = "0";
        rep.mimeType = "audio/mp4";
        rep.codecs = "mp4a.40.2";
        rep.audioSamplingRate = "" + sampleRate;
        rep.SegmentTemplate = new MPD.SegmentTemplate();
        rep.SegmentTemplate.timescale = "" + sampleRate;
        rep.SegmentTemplate.media = "chunk.m4s";
        rep.SegmentTemplate.startNumber = "1";
        rep.SegmentTemplate.initialization = "init.m4s";
        rep.SegmentTemplate.SegmentTimeline = new MPD.SegmentTimeline();
        MPD.S S = new MPD.S();
        S.t = 0L;
        S.d = duration;
        rep.SegmentTemplate.SegmentTimeline.S = singletonList(S);
        MPD.AudioChannelConfiguration ac = new MPD.AudioChannelConfiguration();
        ac.schemeIdUri = "urn:mpeg:dash:23003:3:audio_channel_configuration:2011";
        ac.value = "" + channelCount;
        rep.AudioChannelConfiguration = ac;
        AdaptationSet.Representation = singletonList(rep);
        return dash;
    }

    public static MPD dynamicDash(int width, int height, int timescale, int frameDuration, long durationMsec,
            int sampleRate, int bandwidth, long ctime) {
        MPD dash = new MPD();
        dash.type = "dynamic";
        dash.timeShiftBufferDepth = "PT10S";
        dash.minBufferTime = "PT5S";
        dash.profiles = "urn:mpeg:dash:profile:isoff-live:2011";
        //        dash.availabilityStartTime = now();
        dash.availabilityStartTime = now(ctime);
        dash.minimumUpdatePeriod = "PT0S";
        dash.Period = new MPD.Period();
        dash.Period.id = "1";
        dash.Period.start = "PT0S";
        dash.Period.AdaptationSet = singletonList(new MPD.AdaptationSet());
        MPD.AdaptationSet adaptationSet = dash.Period.AdaptationSet.get(0);
        adaptationSet.segmentAlignment = "true";
        adaptationSet.maxWidth = "" + width;
        adaptationSet.maxHeight = "" + height;
        adaptationSet.maxFrameRate = timescale + "/" + frameDuration;
        adaptationSet.par = width + ":" + height;
        adaptationSet.ContentComponent = asList(new MPD.ContentComponent("1", "video"), new MPD.ContentComponent("2", "audio"));
        adaptationSet.SegmentTemplate = new MPD.SegmentTemplate();
        adaptationSet.SegmentTemplate.timescale = "1000";
        adaptationSet.SegmentTemplate.media = "dash$Number$.m4s";
        adaptationSet.SegmentTemplate.startNumber = "1";
        adaptationSet.SegmentTemplate.duration = "" + durationMsec;
        adaptationSet.SegmentTemplate.initialization = "dashinit.mp4";
        MPD.Representation rep = new MPD.Representation();
        rep.id = "1";
        rep.mimeType = "video/mp4";
        rep.codecs = "avc1.640015,mp4a.40.2";
        rep.width = "" + width;
        rep.height = "" + height;
        rep.frameRate = timescale + "/" + frameDuration;
        rep.sar = "1:1";
        rep.audioSamplingRate = "" + sampleRate;
        rep.startWithSAP = "1";
        rep.bandwidth = "" + bandwidth;
        MPD.AudioChannelConfiguration ac = new MPD.AudioChannelConfiguration();
        ac.schemeIdUri = "urn:mpeg:dash:23003:3:audio_channel_configuration:2011";
        ac.value = "2";
        rep.AudioChannelConfiguration = ac;
        adaptationSet.Representation = asList(rep);
        return dash;
    }

    public static String now() {
        Date d = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String format = sdf.format(d);
        return format;
    }

    public static String now(long mstime) {
        Date d = new Date(mstime);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String format = sdf.format(d);
        return format;
    }

    public static String ptFromMsec(long msec) {
        long h = msec / 3600000L;
        long m = (msec - (h * 3600000L)) / 60000L;
        float s = (msec - (h * 3600000L) - (m * 60000L)) / 1000f;
        StringBuilder sb = new StringBuilder();
        sb.append("PT");
        if (h > 0) {
            sb.append(h).append("H");
        }
        if (m > 0) {
            sb.append(m).append("M");
        }
        sb.append(String.format("%.1fS", s));
        return sb.toString();
    }

    private static final Pattern XS_DURATION_PATTERN = Pattern
            .compile("^(-)?P(([0-9]*)Y)?(([0-9]*)M)?(([0-9]*)D)?" + "(T(([0-9]*)H)?(([0-9]*)M)?(([0-9.]*)S)?)?$");

    /**
     * Parses an xs:duration attribute value, returning the parsed duration in
     * milliseconds.
     *
     * @param value The attribute value to parse.
     * @return The parsed duration in milliseconds.
     */
    public static long parseXsDuration(String value) {
        Matcher matcher = XS_DURATION_PATTERN.matcher(value);
        if (matcher.matches()) {
            boolean negated = !str(matcher.group(1)).isEmpty();
            // Durations containing years and months aren't completely defined. We assume there are
            // 30.4368 days in a month, and 365.242 days in a year.
            String years = matcher.group(3);
            double durationSeconds = (years != null) ? Double.parseDouble(years) * 31556908 : 0;
            String months = matcher.group(5);
            durationSeconds += (months != null) ? Double.parseDouble(months) * 2629739 : 0;
            String days = matcher.group(7);
            durationSeconds += (days != null) ? Double.parseDouble(days) * 86400 : 0;
            String hours = matcher.group(10);
            durationSeconds += (hours != null) ? Double.parseDouble(hours) * 3600 : 0;
            String minutes = matcher.group(12);
            durationSeconds += (minutes != null) ? Double.parseDouble(minutes) * 60 : 0;
            String seconds = matcher.group(14);
            durationSeconds += (seconds != null) ? Double.parseDouble(seconds) : 0;
            long durationMillis = (long) (durationSeconds * 1000);
            return negated ? -durationMillis : durationMillis;
        } else {
            return (long) (Double.parseDouble(value) * 3600 * 1000);
        }
    }

    public static String str(String str) {
        return str == null ? "" : str;
    }

    public static MPD parseXml(File media) {
        try {
            MPD dash = new Persister().read(MPD.class, media);
            return dash;
        } catch (Exception e) {
            throw new RuntimeException("parseXml " + media, e);
        }
    }

    public static MPD parseXml(String source) {
        try {
            MPD dash = new Persister().read(MPD.class, source);
            return dash;
        } catch (Exception e) {
            throw new RuntimeException("parseXml", e);
        }
    }

    public static MPD makeStatic(MPD src, long durationMsec) {
        if ("static".equals(src.type)) {
            return src;
        }
        src.type = "static";
        src.mediaPresentationDuration = ptFromMsec(durationMsec);
        src.minimumUpdatePeriod = null;
        src.suggestedPresentationDelay = null;
        src.availabilityStartTime = null;
        src.publishTime = null;
        return src;
    }

    @Test
    public void testMakeStatic() throws Exception {
        MPD dash = parseXml(new File("testdata/file.mpd"));
        MPD makeStatic = makeStatic(dash, 503400);
        new Persister().write(makeStatic, System.out);
    }

    public static boolean hasVideo(MPD mpd) {
        List<MPD.AdaptationSet> adaptationSet = ListUtils.list(mpd.Period.AdaptationSet);
        return adaptationSet.stream().filter(as -> as.contentType.equals("video")).findFirst().isPresent();
    }

    public static MPD merge(MPD acc, MPD cur) {
        List<MPD.AdaptationSet> accAS = acc.Period.AdaptationSet;
        List<MPD.AdaptationSet> curAS = cur.Period.AdaptationSet;

        List<MPD.AdaptationSet> all = ListUtils.listConcat(accAS, curAS);
        BinaryOperator<MPD.AdaptationSet> merger = (as1, as2) -> {
            Map<String, MPD.Representation> mergedRep = ListUtils.listConcat(as1.Representation, as2.Representation)
                    .stream()
                    .collect(toMap(r -> r.id, r -> r, (r1, r2) -> r2, LinkedHashMap::new));
            as1.Representation = ListUtils.list(mergedRep.values());
            return as1;
        };
        Map<String, MPD.AdaptationSet> mergedAS = all
                .stream()
                .collect(toMap(a -> a.contentType, a -> a, merger, LinkedHashMap::new));

        acc.Period.AdaptationSet = ListUtils.list(mergedAS.values());
        return acc;
    }

    public static MPD makeDynamic(MPD mpd, long availabilityStartTime) {
        if ("dynamic".equals(mpd)) {
            return mpd;
        }
        mpd.type = "dynamic";
        mpd.minimumUpdatePeriod = "PT500S";
        mpd.suggestedPresentationDelay = "PT5S";
        mpd.availabilityStartTime = now(availabilityStartTime);
        mpd.publishTime = now();
        mpd.profiles = "urn:mpeg:dash:profile:isoff-live:2011";
        return mpd;
    }

    public static String asString(MPD mpd) {
        StringWriter stringWriter = new StringWriter();
        try {
            new Persister().write(mpd, stringWriter);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return stringWriter.toString();
    }
}
