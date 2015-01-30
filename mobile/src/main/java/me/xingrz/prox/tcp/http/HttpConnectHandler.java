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

import me.xingrz.prox.BuildConfig;
import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;
import me.xingrz.prox.tcp.tunnel.ProxyHandler;
import me.xingrz.prox.tcp.tunnel.RemoteTunnel;

public class HttpConnectHandler extends ProxyHandler {

    private static final FormattingLogger logger = FormattingLoggers.getContextLogger();

    private static final String HTTP_CONNECT = "CONNECT %s:%d HTTP/1.1\r\n" +
            "Proxy-Connection: keep-alive\r\n" +
            "User-Agent: prox/" + BuildConfig.VERSION_NAME + "\r\n" +
            "\r\n";

    private final String host;
    private final int port;

    private boolean established = false;

    public HttpConnectHandler(RemoteTunnel remoteTunnel, String host, int port) {
        super(remoteTunnel);
        this.host = host;
        this.port = port;
    }

    @Override
    protected ByteBuffer handshake() {
        return ByteBuffer.wrap(String.format(HTTP_CONNECT, host, port).getBytes());
    }

    @Override
    protected boolean establish(ByteBuffer buffer) {
        String response = new String(buffer.array(), buffer.position(), 12);
        if (response.matches("^HTTP/1.[01] 200$")) {
            established = true;
        }

        return established;
    }

    @Override
    protected boolean isEstablished() {
        return established;
    }

    @Override
    protected void beforeSending(ByteBuffer buffer) {
    }

    @Override
    protected void afterReceived(ByteBuffer buffer) {
    }

    @Override
    protected void onClose() {
    }

}
