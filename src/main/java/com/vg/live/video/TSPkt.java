package com.vg.live.video;

import java.nio.ByteBuffer;

/**
 * https://en.wikipedia.org/wiki/MPEG_transport_stream
 *
 * @author zhukov
 *
 */
public class TSPkt {
    public static final int TSPKT_SIZE = 188;
    public int pid;
    public boolean payloadStart;
    public int dataOffset;
    int payloadPos;
    public int payloadLength;
    public transient ByteBuffer _data;
    public long streamOffset;
    private int header;
    public static final int Continuity_counter = 0xf;
    public static final int Payload_flag = 0x10;
    public static final int Adaptation_field_flag = 0x20;
    public static final int Scrambling_control = 0xc0;
    public static final int PIDmask = 0x1fff00;
    public static final int Transport_Priority = 0x200000;
    public static final int Payload_Unit_Start_Indicator = 0x400000;
    public static final int Transport_Error_Indicator = 0x800000;
    public static final int Sync_byte = 0xff000000;
    public static final int NOT_SCRAMBLED = 0;
    public static final int TS_START_CODE = 0x47;
    
    public static final int PAT_PID = 0;
    public static final int Null_Packet_PID = 8191;
    
    public TSPkt() {
    }
    
    public TSPkt(int pid, boolean payloadStart, int streamOffset) {
        this.pid = pid;
        this.payloadStart = payloadStart;
        this.streamOffset = streamOffset;
    }
    
    public ByteBuffer data() {
        if (_data.capacity() == 0) {
            throw new RuntimeException("accessing released payload");
        }
        _data.limit(dataOffset + TSPkt.TSPKT_SIZE);
        _data.position(dataOffset);
        return _data;
    }
    
    public ByteBuffer payload() {
        if (_data.capacity() == 0) {
            throw new RuntimeException("accessing released payload");
        }
        _data.limit(payloadPos + payloadLength);
        _data.position(payloadPos);
        return _data;
    }
    
    @Override
    public String toString() {
        //        return ""+getContinuityCounter()+(payloadStart?"S":"");
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("pid: ").append(pid);
        sb.append(", cc: ").append(getContinuityCounter());
        sb
                .append(", payload: '")
                .append(payloadStart ? "start+" : "")
                .append(payloadPos)
                .append('+')
                .append(payloadLength)
                .append("'");
        sb.append(", offset: ").append(streamOffset);
        sb.append(", header: ").append(Integer.toHexString(header));
        sb.append("}");
        return sb.toString();
    }
    
    public static boolean isTSPkt(ByteBuffer buffer) {
        int header = buffer.getInt(buffer.position());
        int marker = (header & Sync_byte) >> 24;
        //        System.out.println(Long.toHexString(header & 0xffffffffL));
        if (TS_START_CODE != marker) {
            return false;
        }
        return true;
    }
    
    public static void parsePacket(TSPkt pkt, ByteBuffer buffer) {
        int header = buffer.getInt();
        int marker = (header & Sync_byte) >> 24;
        if (TS_START_CODE != marker) {
            throw new RuntimeException("not ts packet");
        }
        boolean error = (header & Transport_Error_Indicator) != 0;
        boolean payloadStart = (header & Payload_Unit_Start_Indicator) != 0;
        boolean priority = (header & Transport_Priority) != 0;
        int pid = (header & PIDmask) >> 8;
        int scrambling = (header & Scrambling_control) >> 6;
        boolean adaptation = (header & Adaptation_field_flag) != 0;
        boolean hasPayload = (header & Payload_flag) != 0;
        int continuity = header & Continuity_counter;
        
        if (adaptation) {
            int taken = 0;
            taken = (buffer.get() & 0xff) + 1;
            buffer.position(Math.min(buffer.limit(), buffer.position() + taken - 1));
        }
        if (!hasPayload) {
            //            System.out.println("no payload");
        }
        if (error) {
            System.out.println("ts error");
        }
        
        pkt.header = header;
        pkt.pid = pid;
        pkt.payloadStart = payloadStart;
        pkt.payloadPos = buffer.position();
        pkt.payloadLength = buffer.remaining();
        pkt._data = buffer; //((b0 & 0x10) != 0) ? buffer : null;
    }
    
    public int getContinuityCounter() {
        int continuity = header & Continuity_counter;
        return continuity;
    }
    
    public boolean hasPayload() {
        boolean hasPayload = (header & Payload_flag) != 0;
        return hasPayload;
    }
    
    //32-8186   0x0020-0x1FFA   May be assigned as needed to Program Map Tables, elementary streams and other data tables
    //8188-8190   0x1FFC-0x1FFE   May be assigned as needed to Program Map Tables, elementary streams and other data tables
    public static boolean isElementaryStreamPID(int pid) {
        return (pid >= 32 && pid <= 8186) || (pid >= 8188 && pid <= 8190);
    }
    
    public void reset() {
        this.pid = 0;
        this.payloadStart = false;
        this.dataOffset = 0;
        this.payloadPos = 0;
        this.payloadLength = 0;
        this._data = null;
        this.streamOffset = 0;
        this.header = 0;
    }
}