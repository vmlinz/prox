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

public class IpUtils {

    public static String toString(int ip) {
        return String.format("%s.%s.%s.%s",
                (ip >> 24) & 0xff,
                (ip >> 16) & 0xff,
                (ip >> 8) & 0xff,
                ip & 0xff);
    }

    public static int toInteger(String ip) {
        String[] split = ip.split(".");
        return (Integer.parseInt(split[0]) << 24)
                | (Integer.parseInt(split[1]) << 16)
                | (Integer.parseInt(split[2]) << 8)
                | (Integer.parseInt(split[3]));
    }

}
