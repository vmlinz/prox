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

package me.xingrz.prox.tcp;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;
import me.xingrz.prox.selectable.Acceptable;
import me.xingrz.prox.transport.AbstractTransportProxy;

public class TcpProxy extends AbstractTransportProxy<ServerSocketChannel, TcpProxySession>
        implements Acceptable {

    private static final int TCP_SESSION_MAX_COUNT = 60;
    private static final long TCP_SESSION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(60);

    public TcpProxy() {
        super(TCP_SESSION_MAX_COUNT, TCP_SESSION_TIMEOUT_MS);
    }

    @Override
    protected ServerSocketChannel createChannel(Selector selector) throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(new InetSocketAddress(0));
        channel.register(selector, SelectionKey.OP_ACCEPT, this);
        return channel;
    }

    @Override
    protected FormattingLogger getLogger() {
        return FormattingLoggers.getContextLogger();
    }

    @Override
    public int port() {
        return serverChannel.socket().getLocalPort();
    }

    @Override
    protected TcpProxySession createSession(final int sourcePort, InetAddress remoteAddress, int remotePort) throws IOException {
        return new TcpProxySession(selector, sourcePort, remoteAddress, remotePort);
    }

    @Override
    public TcpProxySession pickSession(int sourcePort, InetAddress remoteAddress, int remotePort) throws IOException {
        TcpProxySession session = getSession(sourcePort);

        if (session == null
                || !session.getRemoteAddress().equals(remoteAddress)
                || session.getRemotePort() != remotePort) {
            session = super.pickSession(sourcePort, remoteAddress, remotePort);
            logger.v("Created session %08x local:%d -> in:%d -> out:(pending) -> %s:%d",
                    session.hashCode(),
                    sourcePort, port(), remoteAddress.getHostAddress(), remotePort);
        }

        return session;
    }

    /**
     * 接收到来自 VPN 的 TCP 通道，开始取出会话信息并建立远程通道
     */
    @Override
    public void onAcceptable(SelectionKey key) {
        SocketChannel localChannel;

        try {
            localChannel = serverChannel.accept();
        } catch (IOException e) {
            logger.w(e, "Failed to accept client, ignored");
            return;
        }

        int sourcePort = localChannel.socket().getPort();

        TcpProxySession session = getSession(sourcePort);
        if (session == null) {
            logger.w("Ignored socket from %d without session", sourcePort);
            IOUtils.closeQuietly(localChannel);
            return;
        }

        logger.v("Accepted channel from %d, session %08x", sourcePort, session.hashCode());

        session.accept(localChannel);
    }

    @Override
    protected boolean shouldRecycleSession(TcpProxySession session) {
        return super.shouldRecycleSession(session) && !session.isAccepted();
    }

}
