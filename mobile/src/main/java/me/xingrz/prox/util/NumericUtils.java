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

package me.xingrz.prox.util;

/**
 * @author XiNGRZ
 */
public class NumericUtils {

    public static short readShort(byte[] data, int offset) {
        return ((short) (((data[offset] & 0xff) << 0x08)
                | (data[offset + 1] & 0xff)));
    }

    public static void writeShort(byte[] data, int offset, short value) {
        data[offset] = (byte) (value >> 0x08);
        data[offset + 1] = (byte) value;
    }

    public static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 0x18)
                | ((data[offset + 1] & 0xff) << 0x10)
                | ((data[offset + 2] & 0xff) << 0x08)
                | (data[offset + 3] & 0xFF);
    }

    public static void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >> 0x18);
        data[offset + 1] = (byte) (value >> 0x10);
        data[offset + 2] = (byte) (value >> 0x08);
        data[offset + 3] = (byte) (value);
    }

}
