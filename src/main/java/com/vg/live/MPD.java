package com.vg.live;

import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Root;

/**
 * <pre>
 * <MPD xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 *   xmlns="urn:mpeg:dash:schema:mpd:2011"
 *   type="dynamic"
 *   timeShiftBufferDepth="PT10S"
 *   minBufferTime="PT2S"
 *   profiles="urn:mpeg:dash:profile:full:2011">
 *   <Period id="1" start="PT0S">
 *     <AdaptationSet segmentAlignment="true" maxWidth="432" maxHeight="240" maxFrameRate="90000/3003" par="432:240">
 *    <ContentComponent id="1" contentType="video" />
 *    <ContentComponent id="2" contentType="audio" />
 *    <SegmentTemplate timescale="1000" media="test$Number$.m4s" startNumber="1" duration="5017" initialization="testinit.mp4"/>
 *    <Representation id="1" mimeType="video/mp4" codecs="avc1.640015,mp4a.40.2" width="432" height="240" frameRate="90000/3003" sar="1:1" audioSamplingRate="48000" startWithSAP="1" bandwidth="737886">
 *     <AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2"/>
 *    </Representation>
 *   </AdaptationSet>
 *   </Period>
 * </MPD>
 *
 * </pre>
 *
 * @author zhukov
 */

@Root(name = "MPD", strict = false)
@NamespaceList({ @Namespace(prefix = "xsi", reference = "http://www.w3.org/2001/XMLSchema-instance"),
        @Namespace(reference = "urn:mpeg:dash:schema:mpd:2011") })
public class MPD {

    @Attribute(required = false)
    public String type;
    @Attribute(required = false)
    String timeShiftBufferDepth;
    @Attribute(required = false)
    public String minBufferTime;
    @Attribute(required = false)
    public String profiles;
    @Attribute(required = false)
    public String mediaPresentationDuration;
    @Attribute(required = false)
    public String vgTotalDurationSec;
    @Attribute(required = false)
    public String availabilityStartTime; //"2014-09-02T13:36:09Z"
    @Attribute(required = false)
    public String minimumUpdatePeriod;
    @Attribute(required = false)
    public String suggestedPresentationDelay;
    @Attribute(required = false)
    public String publishTime;

    @Element(required = false)
    public Period Period;

    @Root(name = "Period", strict = false)
    public static class Period {
        @Attribute(required = false)
        String id;
        @Attribute(required = false)
        public String start;
        @Attribute(required = false)
        String duration;
        @ElementList(inline = true, required = false)
        public List<AdaptationSet> AdaptationSet;
    }

    @Root(name = "AdaptationSet", strict = false)
    public static class AdaptationSet {
        @Attribute(required = false)
        public String segmentAlignment;
        @Attribute(required = false)
        public String bitstreamSwitching;
        @Attribute(required = false)
        String maxWidth;
        @Attribute(required = false)
        String maxHeight;
        @Attribute(required = false)
        String maxFrameRate;
        @Attribute(required = false)
        String par;
        @Attribute(required = false)
        public String contentType;

        @ElementList(inline = true, required = false)
        public List<ContentComponent> ContentComponent;
        @Element(required = false)
        SegmentTemplate SegmentTemplate;
        @ElementList(inline = true, required = false)
        public List<Representation> Representation;
    }

    @Root(name = "ContentComponent", strict = false)
    public static class ContentComponent {
        public ContentComponent() {
        }

        public ContentComponent(String id, String contentType) {
            this.id = id;
            this.contentType = contentType;
        }

        @Attribute(required = false)
        String id;
        @Attribute(required = false)
        String contentType;
    }

    @Root(name = "SegmentTemplate", strict = false)
    public static class SegmentTemplate {
        @Attribute(required = false)
        public String timescale;
        @Attribute(required = false)
        public String media;
        @Attribute(required = false)
        public String startNumber;
        @Attribute(required = false)
        public String duration;
        @Attribute(required = false)
        public String initialization;
        @Element(required = false)
        public SegmentTimeline SegmentTimeline;
    }

    @Root(name = "SegmentList", strict = false)
    public static class SegmentList {
        @Attribute(required = false)
        public String timescale;
        @Attribute(required = false)
        public String duration;
        @Attribute(required = false)
        public String startNumber;
        @Element(required = false)
        public Initialization Initialization;
        @ElementList(inline = true, required = false)
        public List<SegmentURL> SegmentUrl;
        @Element(required = false)
        public SegmentTimeline SegmentTimeline;
    }

    @Root(name = "Initialization", strict = false)
    public static class Initialization {
        @Attribute(required = false)
        public String sourceURL;
    }

    @Root(name = "SegmentURL", strict = false)
    public static class SegmentURL {
        @Attribute(required = false)
        public String media;
    }

    @Root(name = "SegmentTimeline", strict = false)
    public static class SegmentTimeline {
        @ElementList(inline = true, required = false)
        public List<S> S;
    }

    @Root(name = "S", strict = false)
    public static class S {
        @Attribute(required = false)
        public Long t;
        @Attribute(required = false)
        public long d;
        @Attribute(required = false)
        public long r;
    }

    @Root(name = "Representation", strict = false)
    public static class Representation {
        @Attribute(required = false)
        public String id;
        @Attribute(required = false)
        public String mimeType;
        @Attribute(required = false)
        public String codecs;
        @Attribute(required = false)
        public String width;
        @Attribute(required = false)
        public String height;
        @Attribute(required = false)
        String frameRate;
        @Attribute(required = false)
        String sar;
        @Attribute(required = false)
        String audioSamplingRate;
        @Attribute(required = false)
        String startWithSAP;
        @Attribute(required = false)
        public String bandwidth;

        @Element(required = false)
        public AudioChannelConfiguration AudioChannelConfiguration;
        @Element(required = false)
        public SegmentTemplate SegmentTemplate;
        @Element(required = false)
        public SegmentList SegmentList;
    }

    @Root(name = "AudioChannelConfiguration", strict = false)
    public static class AudioChannelConfiguration {
        @Attribute(required = false)
        public String schemeIdUri;
        @Attribute(required = false)
        public String value;
    }

}
