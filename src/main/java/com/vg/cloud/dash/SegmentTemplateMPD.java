package com.vg.cloud.dash;

import static com.vg.Utils.gsonClone;
import static java.lang.Integer.parseInt;

import java.util.List;

import com.vg.ListUtils;
import com.vg.util.TimeUtil;

public class SegmentTemplateMPD implements MPDTemplate {
    public static boolean debug = false;

    public MPD init(MPD mpd, long ctime, String rid) {
        mpd = MPDUtil.makeDynamic(mpd, ctime);
        mpd.Period.AdaptationSet.get(0).Representation.get(0).id = rid;
        MPD.Representation rep = mpd.Period.AdaptationSet.get(0).Representation.get(0);
        rep.SegmentTemplate.initialization = "stream-$RepresentationID$.0.m4s";
        rep.SegmentTemplate.media = "stream-$RepresentationID$.$Number$.m4s";
        rep.SegmentTemplate.startNumber = "1";
        return mpd;
    }

    public MPD fill(MPD prev, MPD cur) {
        prev = gsonClone(prev);

        MPD.Representation prevRep = prev.Period.AdaptationSet.get(0).Representation.get(0);
        MPD.Representation curRep = cur.Period.AdaptationSet.get(0).Representation.get(0);
        List<MPD.S> prevList = ListUtils.list(prevRep.SegmentTemplate.SegmentTimeline.S);
        List<MPD.S> curList = ListUtils.list(curRep.SegmentTemplate.SegmentTimeline.S);
        for (MPD.S _s : curList) {
            _s.t = null;
            prevList.add(_s);
        }

        long durationTv = prevList.stream().mapToLong(s -> s.d).sum();
        int timescale = parseInt(prev.Period.AdaptationSet.get(0).Representation.get(0).SegmentTemplate.timescale);
        long durationMsec = TimeUtil.convertTimescale(durationTv, timescale, 1000);

        prev.mediaPresentationDuration = MPDUtil.ptFromMsec(durationMsec);

        long prevBufTime = MPDUtil.parseXsDuration(prev.minBufferTime);
        long curBufTime = MPDUtil.parseXsDuration(cur.minBufferTime);
        prev.minBufferTime = MPDUtil.ptFromMsec(Math.max(prevBufTime, curBufTime));
        return prev;
    }
}
