package com.vg.cloud.dash;

public interface MPDTemplate {
    public MPD init(MPD mpd, long ctime, String rid);
    public MPD fill(MPD prev, MPD cur);
}
