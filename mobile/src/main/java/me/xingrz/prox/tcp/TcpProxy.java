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

import me.xingrz.prox.AbstractTransportProxy;
import me.xingrz.prox.tcp.tunnel.IncomingTunnel;
import me.xingrz.prox.tcp.tunnel.RawTunnel;
import me.xingrz.prox.tcp.tunnel.Tunnel;

public class TcpProxy extends AbstractTransportProxy<ServerSocketChannel, TcpProxySession> {

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
    protected void onSelected(SelectionKey key) throws IOException {
        if (key.isReadable()) {
            ((Tunnel) key.attachment()).onReadable(key);
        } else if (key.isWritable()) {
            ((Tunnel) key.attachment()).onWritable(key);
        } else if (key.isConnectable()) {
            ((Tunnel) key.attachment()).onConnectible();
        } else if (key.isAcceptable()) {
            onAccepted();
        }
    }

    @Override
    protected TcpProxySession createSession(int sourcePort, InetAddress remoteAddress, int remotePort)
            throws IOException {
        return new TcpProxySession(sourcePort, remoteAddress, remotePort);
    }

    @Override
    public TcpProxySession pickSession(int sourcePort, InetAddress remoteAddress, int remotePort)
            throws IOException {
        TcpProxySession session = getSession(sourcePort);

        if (session == null
                || !session.getRemoteAddress().equals(remoteAddress)
                || session.getRemotePort() != remotePort) {
            session = createSession(sourcePort, remoteAddress, remotePort);
            Log.v(TAG, "new session from " + sourcePort + " to "
                    + remoteAddress.getHostAddress() + ":" + remotePort);
        }

        session.active();
        return session;
    }

    private void onAccepted() {
        Tunnel localTunnel = null;

        try {
            SocketChannel localChannel = serverChannel.accept();
            localTunnel = new IncomingTunnel(localChannel, selector);

            Log.d(TAG, "accepted from " + localChannel.socket().getPort());

            TcpProxySession session = getSession(localChannel.socket().getPort());
            if (session == null) {
                Log.w(TAG, "no session for this socket, ignored");
                IOUtils.closeQuietly(localTunnel);
                return;
            }

            Log.d(TAG, "session " + session.getRemoteAddress().getHostAddress()
                    + ":" + session.getRemotePort());

            InetSocketAddress remoteAddress = new InetSocketAddress(
                    session.getRemoteAddress(),
                    session.getRemotePort());

            Tunnel remoteTunnel = new RawTunnel(remoteAddress, selector);
            remoteTunnel.setBrotherTunnel(localTunnel);//关联兄弟
            localTunnel.setBrotherTunnel(remoteTunnel);//关联兄弟
            remoteTunnel.connect(remoteAddress);//开始连接
        } catch (IOException e) {
            Log.e(TAG, "remote socket create failed", e);
            IOUtils.closeQuietly(localTunnel);
        }
    }

}
