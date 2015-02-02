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

package me.xingrz.prox.udp.dns;

import android.util.LruCache;

import java.net.InetAddress;
import java.nio.ByteBuffer;

import me.xingrz.prox.internet.IpUtils;
import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;

public class DnsReverseCache {

    private static final FormattingLogger logger = FormattingLoggers.getContextLogger();

    private static LruCache<Integer, String> caches = new LruCache<>(200);

    public static void put(DnsRecord[] records) {
        if (records == null) {
            return;
        }

        for (DnsRecord record : records) {
            for (Integer address : record.addresses) {
                // 我们很任性地只存前三段
                caches.put(address & 0xFFFFFF00, record.name);
                logger.v("Cached %s %s", IpUtils.toString(address & 0xFFFFFF00), record.name);
            }
        }
    }

    public static void put(ByteBuffer buffer) {
        put(DnsRecord.parse(buffer));
    }

    public static void put(InetAddress address, String host) {
        caches.put(IpUtils.toInteger(address) & 0xFFFFFF00, host);
        logger.v("Cached %s %s", IpUtils.toString(IpUtils.toInteger(address) & 0xFFFFFF00), host);
    }

    public static String lookup(int address) {
        logger.v("Looking up %s", IpUtils.toString(address & 0xFFFFFF00));
        return caches.get(address & 0xFFFFFF00);
    }

}
