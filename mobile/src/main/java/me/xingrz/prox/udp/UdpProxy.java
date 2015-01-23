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

package me.xingrz.prox.udp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import me.xingrz.prox.ProxVpnService;

public class UdpProxy implements Runnable, Closeable {

    private static final String TAG = "UdpProxy";

    private static final long UDP_SESSION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);

    private final LruCache<Integer, UdpProxySession> sessions = new LruCache<Integer, UdpProxySession>(20) {
        @Override
        protected void entryRemoved(boolean evicted, Integer key, UdpProxySession oldValue, UdpProxySession newValue) {
            oldValue.close();
            if (oldValue.isFinished()) {
                Log.v(TAG, "Removed finished UDP session local:" + key);
            } else {
                Log.v(TAG, "Terminated UDP session local:" + key);
            }
        }
    };

    private final Selector selector;
    private final DatagramChannel serverChannel;

    private final ByteBuffer buffer = ByteBuffer.allocate(0xFFFF);

    private final Thread thread;

    private final Handler collector = new Handler(Looper.getMainLooper());

    private final Runnable clean = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();

            for (UdpProxySession session : sessions.snapshot().values()) {
                if (now - session.createdAt >= UDP_SESSION_TIMEOUT_MS) {
                    sessions.remove(session.getSourcePort());
                    Log.v(TAG, "Cleaned expired UDP session local:" + session.getSourcePort());
                }
            }

            collector.postDelayed(clean, UDP_SESSION_TIMEOUT_MS);
        }
    };

    public UdpProxy() throws IOException {
        selector = Selector.open();

        serverChannel = DatagramChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(0));
        serverChannel.register(selector, SelectionKey.OP_READ);

        thread = new Thread(this, "UDPProxy");
        thread.start();

        Log.d(TAG, "UDP proxy running on " + port());

        collector.postDelayed(clean, UDP_SESSION_TIMEOUT_MS);
    }

    @Override
    public void close() {
        sessions.evictAll();
        thread.interrupt();
        collector.removeCallbacksAndMessages(null);
    }

    public int port() {
        return serverChannel.socket().getLocalPort();
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

                    if (key.isReadable()) {
                        accept((DatagramChannel) key.channel());
                    }
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Running UDP proxy error", e);
        }

        IOUtils.closeQuietly(selector);
        IOUtils.closeQuietly(serverChannel);

        Log.v(TAG, "UDP server closed");
    }

    /**
     * 将 UDP 包发到公网，并等待同一个端口的返回
     *
     * @param sourcePort    本地来源端口，作为识别
     * @param remoteAddress 目标地址
     * @param remotePort    目标端口
     * @return 所创建的会话
     * @throws IOException
     */
    public UdpProxySession createSession(int sourcePort, InetAddress remoteAddress, int remotePort) throws IOException {
        UdpProxySession session = new UdpProxySession(this, sourcePort, remoteAddress, remotePort);
        sessions.put(sourcePort, session);

        Log.v(TAG, "Created UDP session local:" + sourcePort +
                " -> proxy:" + port() +
                " -> proxy:" + session.socket().getLocalPort() +
                " -> " + session.getRemoteAddress().getHostAddress() + ":" + session.getRemotePort());

        return session;
    }

    /**
     * 收到了刚才 VPN 网关 {@link #createSession(int, java.net.InetAddress, int)} 过后的转发数据包
     * 那么我们就将它发到该去的地方吧！
     *
     * @param localChannel 代表网关的通道
     * @throws IOException
     */
    private void accept(DatagramChannel localChannel) throws IOException {
        buffer.clear();

        InetSocketAddress source = (InetSocketAddress) localChannel.receive(buffer);
        if (source == null) {
            Log.w(TAG, "no source of packet, ignored");
            return;
        }

        Log.v(TAG, "Accepted UDP channel from " + source.getPort());

        UdpProxySession session = sessions.get(source.getPort());
        if (session == null) {
            Log.w(TAG, "no session for packet, ignored");
            return;
        }

        buffer.flip();

        session.send(buffer);

        Log.v(TAG, "Sent out UDP session local:" + session.getSourcePort() +
                " -> proxy:" + port() +
                " -> proxy:" + session.socket().getLocalPort() +
                " -> " + session.getRemoteAddress().getHostAddress() + ":" + session.getRemotePort());
    }

    /**
     * 将公网的 UDP 返回反哺回给 VPN 网关
     *
     * @param buffer  返回数据
     * @param session 对应的会话
     * @throws IOException
     */
    void feedback(ByteBuffer buffer, UdpProxySession session) throws IOException {
        InetSocketAddress address = new InetSocketAddress(
                ProxVpnService.FAKE_REMOTE_ADDRESS, session.getSourcePort());

        Log.v(TAG, "Received in UDP session " +
                address.getHostString() + ":" + session.getSourcePort() +
                " <- " + serverChannel.socket().getLocalAddress().getHostAddress() + ":" + port() +
                " <- proxy:" + session.socket().getLocalPort() +
                " <- " + session.getRemoteAddress().getHostAddress() + ":" + session.getRemotePort());

        serverChannel.send(buffer, address);
    }

    /**
     * 完成会话
     *
     * @param sourcePort 本地来源端口，作为识别
     * @return 会话对象
     */
    public UdpProxySession finishSession(int sourcePort) {
        Log.v(TAG, "Finished UDP session local:" + sourcePort);
        return sessions.remove(sourcePort);
    }

}
