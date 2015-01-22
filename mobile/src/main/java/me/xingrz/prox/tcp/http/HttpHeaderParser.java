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

package me.xingrz.prox.tcp.http;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class HttpHeaderParser {

    private static final String TAG = "HttpHeaderParser";

    private static final List<String> SUPPORT_HTTP_METHOD = new ArrayList<>();

    static {
        SUPPORT_HTTP_METHOD.add("GET");
        SUPPORT_HTTP_METHOD.add("POST");
        SUPPORT_HTTP_METHOD.add("PUT");
        SUPPORT_HTTP_METHOD.add("DELETE");
        SUPPORT_HTTP_METHOD.add("HEAD");
        SUPPORT_HTTP_METHOD.add("OPTIONS");
        SUPPORT_HTTP_METHOD.add("TRACE");
        SUPPORT_HTTP_METHOD.add("CONNECT");
    }

    public static String parseHost(byte[] data, int offset, int length) {
        switch (data[offset]) {
            case 'G': // GET
            case 'P': // POST, PUT
            case 'D': // DELETE
            case 'H': // HEAD
            case 'O': // OPTIONS
            case 'T': // TRACE
            case 'C': // CONNECT
                return parseHttpHost(data, offset, length);
            default:
                return null;
        }
    }

    private static String parseHttpHost(byte[] data, int offset, int length) {
        String[] headers = new String(data, offset, length).split("\r\n");
        if (headers.length < 2) {
            // CONNECT www.google.com:443 HTTP/1.1\r\n
            // \r\n
            return null;
        }

        String[] request = headers[0].split(" ");
        if (request.length != 3) {
            return null;
        }

        if (!SUPPORT_HTTP_METHOD.contains(request[0]) || !request[2].startsWith("HTTP/1.")) {
            return null;
        }

        Log.v(TAG, headers[0]);

        if ("CONNECT".equals(request[0])) {
            return request[1].split(":")[0];
        }

        for (int i = 1; i < headers.length - 1; i++) {
            String[] field = headers[i].split(":");
            if (field[0].toLowerCase().equals("host")) {
                return field[1].trim();
            }
        }

        return null;
    }

}
