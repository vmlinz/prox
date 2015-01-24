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

package me.xingrz.prox.ip;

public class IPHeader {

    public static final byte VERSION_4 = 4;
    public static final byte VERSION_6 = 6;

    /**
     * 协议代码
     * http://www.iana.org/assignments/protocol-numbers
     */

    public static final byte PROTOCOL_TCP = 6;
    public static final byte PROTOCOL_UDP = 17;

    protected byte[] packet;

    public IPHeader(byte[] packet) {
        this.packet = packet;
    }

    public byte version() {
        return (byte) (packet[0] >> 4);
    }

    /**
     * 返回本层的读取偏移值，比如 IP 层是从 0 算起，TCP 层则从 IP 层的头部后算起
     *
     * @return 本层级偏移值
     */
    public int offset() {
        return 0;
    }

    @Override
    public String toString() {
        return String.format("IP[ver:%s]", version());
    }

}
