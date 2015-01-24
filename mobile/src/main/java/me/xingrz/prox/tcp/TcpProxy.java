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

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import me.xingrz.prox.ip.IpUtils;
import me.xingrz.prox.tcp.tunnel.IncomingTunnel;
import me.xingrz.prox.tcp.tunnel.Tunnel;

public class TcpProxy implements Runnable, Closeable {

    private static final String TAG = "TCPProxy";

    private Selector selector;
    private ServerSocketChannel serverChannel;

    private NatSessionManager sessionManager = NatSessionManager.getInstance();

    private Thread thread;

    public TcpProxy() throws IOException {
        selector = Selector.open();

        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(0));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public int port() {
        return serverChannel.socket().getLocalPort();
    }

    public void start() {
        thread = new Thread(this, "TCP Proxy Thread");
        thread.start();
        Log.v(TAG, "running on " + port());
    }

    @Override
    public void close() {
        thread.interrupt();
        IOUtils.closeQuietly(selector);
        IOUtils.closeQuietly(serverChannel);
    }

    @Override
    public synchronized void run() {
        try {
            while (true) {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isReadable()) {
                        ((Tunnel) key.attachment()).onReadable(key);
                    } else if (key.isWritable()) {
                        ((Tunnel) key.attachment()).onWritable(key);
                    } else if (key.isConnectable()) {
                        ((Tunnel) key.attachment()).onConnectible();
                    } else if (key.isAcceptable()) {
                        onAccepted(key);
                    }
                }
            }
        } catch (ClosedSelectorException ignored) {
        } catch (IOException e) {
            Log.w(TAG, "running tcp proxy error", e);
        } finally {
            close();
        }
    }

    private void onAccepted(SelectionKey key) {
        Tunnel localTunnel = null;

        try {
            SocketChannel localChannel = serverChannel.accept();
            localTunnel = new IncomingTunnel(localChannel, selector);

            Log.d(TAG, "accepted from " + localChannel.socket().getPort());

            NatSession session = sessionManager.getSession(localChannel.socket().getPort());
            if (session == null) {
                Log.w(TAG, "no session for this socket, ignored");
                IOUtils.closeQuietly(localTunnel);
                return;
            }

            Log.d(TAG, "session " + IpUtils.toString(session.remoteIp) + ":" + session.remotePort);

            /*InetSocketAddress destAddress = getDestAddress(localChannel);
            if (destAddress != null) {
                Tunnel remoteTunnel = TunnelFactory.createTunnelByConfig(destAddress, selector);
                remoteTunnel.setBrotherTunnel(localTunnel);//关联兄弟
                localTunnel.setBrotherTunnel(remoteTunnel);//关联兄弟
                remoteTunnel.connect(destAddress);//开始连接
            } else {
                LocalVpnService.Instance.writeLog("Error: socket(%s:%d) target host is null.", localChannel.socket().getInetAddress().toString(), localChannel.socket().getPort());
                localTunnel.close();
            }*/
        } catch (IOException e) {
            Log.e(TAG, "remote socket create failed", e);
            IOUtils.closeQuietly(localTunnel);
        }
    }

}
