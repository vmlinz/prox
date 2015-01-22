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

import me.xingrz.prox.ip.IPv4Header;
import me.xingrz.prox.ip.NumericUtils;

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
        return NumericUtils.readShort(packet, offset() + 4);
    }

    public int udpDataOffset() {
        return offset() + udpHeaderLength();
    }

    public int udpDataLength() {
        return totalLength() - udpDataOffset();
    }

    public short getUdpHeaderChecksum() {
        return (short) NumericUtils.readShort(packet, offset() + 7);
    }

    public void setUdpHeaderChecksum(short checksum) {
        NumericUtils.writeShort(packet, offset() + 7, checksum);
    }

    @Override
    public void recomputeChecksum() {
        super.recomputeChecksum();

        int ipDataLength = ipDataLength();
        if (ipDataLength < 0) {
            return;
        }

        int sum = sum(12, 8) + (protocol() & 0xff) + ipDataLength;

        setUdpHeaderChecksum((short) 0);
        setUdpHeaderChecksum(checksum(sum, offset(), ipDataLength));
    }

    @Override
    public String toString() {
        return String.format("UDP[srcPort:%s, dstPort:%s, length:%s]",
                getSourcePort(), getDestinationPort(), udpDataLength());
    }

}
