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

package me.xingrz.prox.internet;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IPv4 包
 *
 * @author XiNGRZ
 * @see <a href="https://en.wikipedia.org/wiki/IPv4#Header">IPv4 首部结构</a>
 */
public class IPv4Header extends IPHeader {

    public IPv4Header(byte[] packet) {
        super(packet);
    }

    /**
     * @return IP 包头部长度
     */
    public int ipHeaderLength() {
        return (packet[0] & 0xf) * 4;
    }

    public int ipDataOffset() {
        return ipHeaderLength();
    }

    public int ipDataLength() {
        return totalLength() - ipDataOffset();
    }

    /**
     * @return IP 包总长度
     */
    public int totalLength() {
        return NumericUtils.readShort(packet, 2);
    }

    /**
     * @return 协议
     */
    public byte protocol() {
        return packet[9];
    }

    public int getSourceIp() {
        return NumericUtils.readInt(packet, 12);
    }

    public InetAddress getSourceIpAddress() {
        try {
            return InetAddress.getByAddress(new byte[]{
                    packet[12], packet[13], packet[14], packet[15]
            });
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public void setSourceIp(int ip) {
        NumericUtils.writeInt(packet, 12, ip);
    }

    public void setSourceIp(InetAddress address) {
        System.arraycopy(address.getAddress(), 0, packet, 12, 4);
    }

    public int getDestinationIp() {
        return NumericUtils.readInt(packet, 16);
    }

    public InetAddress getDestinationIpAddress() {
        try {
            return InetAddress.getByAddress(new byte[]{
                    packet[16], packet[17], packet[18], packet[19]
            });
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public void setDestinationIp(int ip) {
        NumericUtils.writeInt(packet, 16, ip);
    }

    public void setDestinationIp(InetAddress address) {
        System.arraycopy(address.getAddress(), 0, packet, 16, 4);
    }

    public int getIpHeaderChecksum() {
        return NumericUtils.readShort(packet, 10);
    }

    public void setIpHeaderChecksum(int checksum) {
        NumericUtils.writeShort(packet, 10, checksum);
    }

    /**
     * 重新计算并覆盖 Checksum
     */
    public void recomputeChecksum() {
        setIpHeaderChecksum((short) 0);
        setIpHeaderChecksum(checksum(0, 0, ipHeaderLength()));
    }

    protected short checksum(long sum, int offset, int length) {
        for (int i = 0; i < length; i += 2) {
            sum += (packet[offset + i] << 8) & 0xFF00;
        }

        for (int i = 1; i < length; i += 2) {
            sum += packet[offset + i] & 0x00FF;
        }

        while ((sum >> 16) > 0) {
            sum = (sum >> 16) + (sum & 0xFFFF);
        }

        return (short) (0xFFFF - sum);
    }

    public void writeTo(OutputStream stream) throws IOException {
        stream.write(packet, 0, totalLength());
    }

    @Override
    public String toString() {
        return String.format("IPv4[headerLen:%s, totalLen:%s,  protocol:%s, srcIp:%s, dstIp:%s]",
                ipHeaderLength(), totalLength(), protocol(),
                IpUtils.toString(getSourceIp()),
                IpUtils.toString(getDestinationIp()));
    }

}
