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

package me.xingrz.prox.udp;

import me.xingrz.prox.internet.IPHeader;
import me.xingrz.prox.internet.IPv4Header;
import me.xingrz.prox.internet.NumericUtils;

/**
 * UDP 包
 *
 * @author XiNGRZ
 * @see <a href="https://en.wikipedia.org/wiki/User_Datagram_Protocol#Packet_structure">UDP 包结构</a>
 */
public class UdpHeader extends IPv4Header {

    public UdpHeader(byte[] packet) {
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

    public int udpHeaderLength() {
        return 8;
    }

    public int udpDataOffset() {
        return offset() + udpHeaderLength();
    }

    public int udpDataLength() {
        return totalLength() - udpDataOffset();
    }

    public int getUdpHeaderChecksum() {
        return NumericUtils.readShort(packet, offset() + 6);
    }

    public void setUdpHeaderChecksum(int checksum) {
        NumericUtils.writeShort(packet, offset() + 6, checksum);
    }

    @Override
    public void recomputeChecksum() {
        super.recomputeChecksum();

        setUdpHeaderChecksum(0);

        long pseudo = 0;
        pseudo += getSourceIp() & 0xffff;
        pseudo += (getSourceIp() >> 16) & 0xffff;
        pseudo += getDestinationIp() & 0xffff;
        pseudo += (getDestinationIp() >> 16) & 0xffff;
        pseudo += IPHeader.PROTOCOL_UDP;
        pseudo += ipDataLength();

        setUdpHeaderChecksum(checksum(pseudo, ipDataOffset(), ipDataLength()));
    }

    @Override
    public String toString() {
        return String.format("UDP[srcPort:%s, dstPort:%s, dataLength:%s]",
                getSourcePort(), getDestinationPort(), udpDataLength());
    }

}
