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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;

public class HttpHeaderParser {

    private static final FormattingLogger logger = FormattingLoggers.getContextLogger();

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

    private static final byte HTTP_GET = 'G';
    private static final byte HTTP_POST_OR_PUT = 'P';
    private static final byte HTTP_DELETE = 'D';
    private static final byte HTTP_HEAD = 'H';
    private static final byte HTTP_OPTIONS = 'O';
    private static final byte HTTP_TRACE = 'T';
    private static final byte HTTP_CONNECT = 'C';

    private static final byte TLS_HANDSHAKE = 0x16;
    private static final byte TLS_MESSAGE_CLIENT_HELLO = 0X01;
    private static final short TLS_EXTENSION_SERVER_NAME = 0x0000;
    private static final byte TLS_SNI_HOST_NAME = 0x00;

    public static String parseHost(ByteBuffer buffer) {
        switch (buffer.get(0)) {
            case HTTP_GET:
            case HTTP_POST_OR_PUT:
            case HTTP_DELETE:
            case HTTP_HEAD:
            case HTTP_OPTIONS:
            case HTTP_TRACE:
            case HTTP_CONNECT:
                return parseHttpHost(buffer);
            case TLS_HANDSHAKE:
                return parseTlsHost(buffer);
            default:
                return null;
        }
    }

    private static String parseHttpHost(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        String[] headers = new String(bytes).split("\r\n");
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

    /**
     * 尝试从 TLS client hello 中的 SNI 读出 server_name
     * http://tools.ietf.org/html/rfc4366#section-3.1
     *
     * @param buffer 缓冲区
     * @return Host 或 {@value null}
     */
    private static String parseTlsHost(ByteBuffer buffer) {
        if (buffer.limit() <= 5) {
            return null;
        }

        // Handshake (1) + SSL Major Version (1) + SSL Minor Version (1)
        buffer.position(3);

        // Total Length
        if (buffer.getShort() != buffer.remaining()) {
            logger.v("Not a SSL handshake: wrong length");
            return null;
        }

        int skipping;

        try {
            while (buffer.hasRemaining()) {
                // Message Type
                byte messageType = buffer.get();

                // Message Length
                skipping = (0xFF0000 & (buffer.get() << 16))
                        | (0x00FF00 & (buffer.get() << 8))
                        | (0x0000FF & buffer.get());

                if (skipping > buffer.remaining()) {
                    logger.v("Not a SSL handshake: message length out of bound");
                    return null;
                }

                if (messageType != TLS_MESSAGE_CLIENT_HELLO) {
                    buffer.position(buffer.position() + skipping);
                    continue;
                }

                // TLS Version
                buffer.position(buffer.position() + 2);

                // Random
                buffer.position(buffer.position() + 32);

                // Session ID
                skipping = buffer.get();
                buffer.position(buffer.position() + skipping);

                // Cipher Suites
                skipping = buffer.getShort();
                buffer.position(buffer.position() + skipping);

                // Compression Methods
                skipping = buffer.get();
                buffer.position(buffer.position() + skipping);

                // Extensions
                buffer.position(buffer.position() + 2);
                while (buffer.hasRemaining()) {
                    // Extension Type
                    short extensionType = buffer.getShort();

                    // Extension Length
                    skipping = buffer.getShort();

                    if (extensionType != TLS_EXTENSION_SERVER_NAME) {
                        buffer.position(buffer.position() + skipping);
                        continue;
                    }

                    // Server Name list
                    buffer.position(buffer.position() + 2);
                    while (buffer.hasRemaining()) {
                        // Name Type
                        byte nameType = buffer.get();

                        // Name Length
                        skipping = buffer.getShort();

                        if (nameType != TLS_SNI_HOST_NAME) {
                            buffer.position(buffer.position() + skipping);
                            continue;
                        }

                        if (buffer.remaining() < skipping) {
                            logger.v("Not a SSL handshake: server name length out of bound");
                            return null;
                        }

                        byte[] bytes = new byte[skipping];
                        buffer.get(bytes);

                        String name = new String(bytes);
                        logger.v("Found SNI: %s", name);
                        return name;
                    }
                }
            }

            logger.v("No SNI found");
            return null;
        } catch (IllegalArgumentException e) {
            logger.v("Not a SSL handshake: out of bound");
            return null;
        }
    }

}
