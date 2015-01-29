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

package me.xingrz.prox.tcp.tunnel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;
import me.xingrz.prox.tcp.http.HttpHeaderParser;

public class IncomingTunnel extends Tunnel {

    private boolean firstBufferReceived = false;

    public IncomingTunnel(Selector selector, SocketChannel channel, String sessionKey) {
        super(selector, channel, sessionKey);
    }

    @Override
    protected FormattingLogger getLogger(String sessionKey) {
        return FormattingLoggers.getContextLogger(sessionKey);
    }

    @Override
    protected boolean isTunnelEstablished() {
        return true;
    }

    @Override
    protected void afterReceived(ByteBuffer buffer) throws IOException {
        // TODO: 以后要考虑 Header 太长导致 Host 不在第一个 buffer 里的情况
        if (firstBufferReceived) {
            return;
        }

        String host = HttpHeaderParser.parseHost(buffer.asReadOnlyBuffer());

        if (host == null) {
            logger.v("Not a HTTP request");
        } else {
            logger.v("HTTP request to host: %s", host);
        }

        onParsedHost(host);

        firstBufferReceived = true;
    }

    @Override
    protected void beforeSending(ByteBuffer buffer) throws IOException {

    }

    @Override
    protected void onClose(boolean finished) {

    }

    /**
     * 解析完头部
     *
     * @param host Host 字段，不一定有，可能是 {@code null}
     */
    protected void onParsedHost(String host) {
    }

}
