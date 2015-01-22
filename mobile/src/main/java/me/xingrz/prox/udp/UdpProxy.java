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

import android.util.Log;
import android.util.LruCache;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

public class UdpProxy implements Runnable {

    private static final String TAG = "UdpProxy";

    private final LruCache<Integer, UdpProxySession> sessions = new LruCache<Integer, UdpProxySession>(200) {
        @Override
        protected void entryRemoved(boolean evicted, Integer key, UdpProxySession oldValue, UdpProxySession newValue) {
            oldValue.close();
        }
    };

    private final Selector selector;
    private final DatagramChannel serverChannel;

    private final Thread thread;

    private final ByteBuffer buffer = ByteBuffer.allocate(0xFFFF);

    public UdpProxy() throws IOException {
        selector = Selector.open();

        serverChannel = DatagramChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(0));
        serverChannel.register(selector, SelectionKey.OP_READ);

        thread = new Thread(this, "DNS Proxy");
        thread.start();
    }

    public void stop() {
        thread.interrupt();
        sessions.evictAll();
        IOUtils.closeQuietly(selector);
        IOUtils.closeQuietly(serverChannel);
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
            Log.w(TAG, "running dns proxy error", e);
        } finally {
            stop();
        }
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

        InetSocketAddress localAddress = (InetSocketAddress) localChannel.receive(buffer);
        if (localAddress == null) {
            Log.w(TAG, "no address for packet, ignored");
            return;
        }

        buffer.flip();

        UdpProxySession session = sessions.get(localAddress.getPort());
        if (session == null) {
            Log.w(TAG, "no session for packet, ignored");
            return;
        }

        session.send(buffer);
    }

    /**
     * 将公网的 UDP 返回反哺回给 VPN 网关
     *
     * @param buffer 返回数据
     * @param session 对应的会话
     * @throws IOException
     */
    void feedback(ByteBuffer buffer, UdpProxySession session) throws IOException {
        InetSocketAddress address = new InetSocketAddress(
                serverChannel.socket().getLocalAddress(), session.getSourcePort());

        serverChannel.send(buffer, address);
    }

    /**
     * 完成会话
     *
     * @param sourcePort 本地来源端口，作为识别
     * @return 会话对象
     */
    public UdpProxySession finishSession(int sourcePort) {
        return sessions.remove(sourcePort);
    }

}
