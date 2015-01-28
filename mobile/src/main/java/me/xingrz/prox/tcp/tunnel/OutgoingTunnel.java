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

import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;

public class OutgoingTunnel extends RemoteTunnel {

    public OutgoingTunnel(Selector selector, String sessionKey) throws IOException {
        super(selector, sessionKey);
    }

    @Override
    protected FormattingLogger getLogger(String sessionKey) {
        return FormattingLoggers.getContextLogger(sessionKey);
    }

    @Override
    protected void onConnected() throws IOException {
        logger.v("Connected to remote host");
        tunnelEstablished();
    }

    @Override
    public boolean isTunnelEstablished() {
        return true;
    }

    @Override
    protected void afterReceived(ByteBuffer buffer) throws IOException {

    }

    @Override
    protected void beforeSending(ByteBuffer buffer) throws IOException {

    }

    @Override
    protected void onClose(boolean finished) {

    }

}
