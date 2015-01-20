/*
 * Copyright (C) 2015 XiNGRZ <chenxingyu92@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package me.xingrz.prox.tcpip;

import me.xingrz.prox.util.NumericUtils;

/**
 * https://en.wikipedia.org/wiki/Transmission_Control_Protocol#TCP_segment_structure
 *
 * @author XiNGRZ
 */
public class TCPHeader extends IPv4Header {

    private static final byte FLAG_ACK = 0x10;
    private static final byte FLAG_PSH = 0x08;
    private static final byte FLAG_RST = 0x04;
    private static final byte FLAG_SYN = 0x02;
    private static final byte FLAG_FIN = 0x01;

    public TCPHeader(byte[] packet) {
        super(packet);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int offset() {
        return super.offset() + ipHeaderLength();
    }

    public short getSourcePort() {
        return NumericUtils.readShort(packet, offset());
    }

    public short getDestinationPort() {
        return NumericUtils.readShort(packet, offset() + 2);
    }

    public int tcpHeaderLength() {
        return ((packet[offset() + 12] >> 4) & 0xf) * 4;
    }

    public int tcpDataOffset() {
        return offset() + tcpHeaderLength();
    }

    public boolean ack() {
        return (packet[offset() + 13] & FLAG_ACK) == FLAG_ACK;
    }

    public boolean psh() {
        return (packet[offset() + 13] & FLAG_PSH) == FLAG_PSH;
    }

    public boolean rst() {
        return (packet[offset() + 13] & FLAG_RST) == FLAG_RST;
    }

    public boolean syn() {
        return (packet[offset() + 13] & FLAG_SYN) == FLAG_SYN;
    }

    public boolean fin() {
        return (packet[offset() + 13] & FLAG_FIN) == FLAG_FIN;
    }

    @Override
    public void recomputeChecksum() {
        super.recomputeChecksum();
    }

    @Override
    public String toString() {
        String flag = "---";
        if (ack()) flag = "ACK";
        else if (psh()) flag = "PSH";
        else if (rst()) flag = "RST";
        else if (syn()) flag = "SYN";
        else if (fin()) flag = "FIN";

        return String.format("TCP[srcPort:%s, dstPort:%s, offset:%s, flag:%s]",
                getSourcePort(), getDestinationPort(), tcpDataOffset(), flag);
    }

}
