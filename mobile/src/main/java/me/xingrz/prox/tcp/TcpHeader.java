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

package me.xingrz.prox.tcp;

import me.xingrz.prox.internet.IPHeader;
import me.xingrz.prox.internet.IPv4Header;
import me.xingrz.prox.internet.NumericUtils;

/**
 * https://en.wikipedia.org/wiki/Transmission_Control_Protocol#TCP_segment_structure
 *
 * @author XiNGRZ
 */
public class TcpHeader extends IPv4Header {

    private static final byte FLAG_ACK = 0x10;
    private static final byte FLAG_PSH = 0x08;
    private static final byte FLAG_RST = 0x04;
    private static final byte FLAG_SYN = 0x02;
    private static final byte FLAG_FIN = 0x01;

    public TcpHeader(byte[] packet) {
        super(packet);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int offset() {
        return super.offset() + ipHeaderLength();
    }

    public int getSourcePort() {
        return NumericUtils.readShort(packet, offset());
    }

    public void setSourcePort(int port) {
        NumericUtils.writeShort(packet, offset(), port);
    }

    public int getDestinationPort() {
        return NumericUtils.readShort(packet, offset() + 2);
    }

    public void setDestinationPort(int port) {
        NumericUtils.writeShort(packet, offset() + 2, port);
    }

    public int tcpHeaderLength() {
        return ((packet[offset() + 12] >> 4) & 0xf) * 4;
    }

    public int tcpDataOffset() {
        return offset() + tcpHeaderLength();
    }

    public int tcpDataLength() {
        return totalLength() - tcpDataOffset();
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

    public int getTcpHeaderChecksum() {
        return NumericUtils.readShort(packet, offset() + 16);
    }

    public void setTcpHeaderChecksum(int checksum) {
        NumericUtils.writeShort(packet, offset() + 16, checksum);
    }

    @Override
    public void recomputeChecksum() {
        super.recomputeChecksum();

        setTcpHeaderChecksum(0);

        long pseudo = 0;
        pseudo += getSourceIp() & 0xffff;
        pseudo += (getSourceIp() >> 16) & 0xffff;
        pseudo += getDestinationIp() & 0xffff;
        pseudo += (getDestinationIp() >> 16) & 0xffff;
        pseudo += IPHeader.PROTOCOL_TCP;
        pseudo += ipDataLength();

        setTcpHeaderChecksum(checksum(pseudo, ipDataOffset(), ipDataLength()));
    }

    @Override
    public String toString() {
        String flag = "";
        if (ack()) flag += "ACK";
        if (psh()) flag += "PSH";
        if (rst()) flag += "RST";
        if (syn()) flag += "SYN";
        if (fin()) flag += "FIN";

        return String.format("TCP[srcPort:%s, dstPort:%s, offset:%s, flag:%s]",
                getSourcePort(), getDestinationPort(), tcpDataOffset(), flag);
    }

}
