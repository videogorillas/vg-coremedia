package com.vg.cloud.dash;

import com.vg.Utils;
import com.vg.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;

import static com.vg.cloud.dash.MPD.*;
import static java.lang.Integer.parseInt;
import static java.util.Collections.singletonList;

public class SegmentListMPD implements MPDTemplate {

    public MPD init(MPD mpd, long ctime, String rid) {
        mpd = MPDUtil.makeDynamic(mpd, ctime);
        mpd.Period.AdaptationSet.get(0).Representation.get(0).id = rid;
        AdaptationSet adaptationSet = mpd.Period.AdaptationSet.get(0);
        Representation rep = adaptationSet.Representation.get(0);
        rep.SegmentList = new SegmentList();
        rep.SegmentList.timescale = rep.SegmentTemplate.timescale;
        rep.SegmentList.startNumber = rep.SegmentTemplate.startNumber;
        rep.SegmentList.SegmentTimeline = new SegmentTimeline();
        rep.SegmentList.SegmentTimeline.S = rep.SegmentTemplate.SegmentTimeline.S;

        if (rep.SegmentList.Initialization == null) {
            rep.SegmentList.Initialization = new Initialization();
            rep.SegmentList.Initialization.sourceURL = rep.SegmentTemplate.initialization;
        }

        if (rep.SegmentList.SegmentUrl == null) {
            rep.SegmentList.SegmentUrl = new ArrayList<>();
        }

        SegmentURL url = new SegmentURL();
        url.media = rep.SegmentTemplate.media;
        rep.SegmentList.SegmentUrl.add(url);

        rep.SegmentTemplate = null;
        mpd.Period.AdaptationSet.get(0).Representation = singletonList(rep);
        return mpd;
    }

    public MPD fill(MPD prev, MPD cur) {
        prev = Utils.gsonClone(prev);

        Representation prevRep = prev.Period.AdaptationSet.get(0).Representation.get(0);
        Representation curRep = cur.Period.AdaptationSet.get(0).Representation.get(0);

        List<MPD.S> prevList = prevRep.SegmentList.SegmentTimeline.S;
        List<MPD.S> curList = curRep.SegmentList.SegmentTimeline.S;
        for (S _s : curList) {
            _s.t = null;
            prevList.add(_s);
        }

        List<MPD.SegmentURL> prevListUrl = prevRep.SegmentList.SegmentUrl;
        List<MPD.SegmentURL> curListUrl = curRep.SegmentList.SegmentUrl;
        for (SegmentURL _s : curListUrl) {
            prevListUrl.add(_s);
        }

        long durationTv = prevList.stream().mapToLong(s -> s.d).sum();
        int timescale = parseInt(prev.Period.AdaptationSet.get(0).Representation.get(0).SegmentList.timescale);
        long durationMsec = TimeUtil.convertTimescale(durationTv, timescale, 1000);

        prev.mediaPresentationDuration = MPDUtil.ptFromMsec(durationMsec);

        long prevBufTime = MPDUtil.parseXsDuration(prev.minBufferTime);
        long curBufTime = MPDUtil.parseXsDuration(cur.minBufferTime);
        prev.minBufferTime = MPDUtil.ptFromMsec(Math.max(prevBufTime, curBufTime));
        return prev;
    }
}
