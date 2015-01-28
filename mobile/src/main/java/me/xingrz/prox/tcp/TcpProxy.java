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

import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import me.xingrz.prox.tcp.tunnel.RemoteTunnel;
import me.xingrz.prox.tcp.tunnel.Tunnel;
import me.xingrz.prox.transport.AbstractTransportProxy;

public class TcpProxy extends AbstractTransportProxy<ServerSocketChannel, SocketChannel, TcpProxySession> {

    private static final String TAG = "TCPProxy";

    private static final int TCP_SESSION_MAX_COUNT = 60;
    private static final long TCP_SESSION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(60);

    public TcpProxy() throws IOException {
        super(TCP_SESSION_MAX_COUNT, TCP_SESSION_TIMEOUT_MS, TAG);
    }

    @Override
    protected ServerSocketChannel createChannel(Selector selector) throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(new InetSocketAddress(0));
        channel.register(selector, SelectionKey.OP_ACCEPT);
        return channel;
    }

    @Override
    public int port() {
        return serverChannel.socket().getLocalPort();
    }

    @Override
    protected void onSelected(SelectionKey key) {
        try {
            if (key.isAcceptable()) {
                accept(serverChannel.accept());
            } else if (key.isConnectable()) {
                ((RemoteTunnel) key.attachment()).onConnectible();
            } else if (key.isReadable()) {
                ((Tunnel) key.attachment()).onReadable(key);
            } else if (key.isWritable()) {
                ((Tunnel) key.attachment()).onWritable(key);
            }
        } catch (IOException e) {
            Log.w(TAG, "TCP proxy faced an error", e);
        }
    }

    @Override
    protected TcpProxySession createSession(final int sourcePort, InetAddress remoteAddress, int remotePort)
            throws IOException {
        return new TcpProxySession(selector, sourcePort, remoteAddress, remotePort);
    }

    @Override
    public TcpProxySession pickSession(int sourcePort, InetAddress remoteAddress, int remotePort)
            throws IOException {
        TcpProxySession session = getSession(sourcePort);

        if (session == null
                || !session.getRemoteAddress().equals(remoteAddress)
                || session.getRemotePort() != remotePort) {
            session = super.pickSession(sourcePort, remoteAddress, remotePort);

            Log.v(TAG, "Created TCP session local:" + sourcePort
                    + " -> proxy:" + port()
                    + " -> proxy:(pending) "
                    + " -> " + remoteAddress.getHostAddress() + ":" + remotePort);
        }

        return session;
    }

    /**
     * 接收到来自 VPN 的 TCP 通道，开始取出会话信息并建立远程通道
     *
     * @param localChannel 从 VPN 传来的本地通道
     * @throws IOException
     */
    @Override
    public void accept(SocketChannel localChannel) throws IOException {
        int sourcePort = localChannel.socket().getPort();
        Log.v(TAG, "Accepted TCP channel from " + sourcePort);

        TcpProxySession session = getSession(sourcePort);
        if (session == null) {
            Log.w(TAG, "no session for this socket, ignored");
            IOUtils.closeQuietly(localChannel);
            return;
        }

        session.accept(localChannel);
    }

    @Override
    protected boolean shouldRecycleSession(TcpProxySession session) {
        return super.shouldRecycleSession(session) && !session.isEstablished();
    }

}
