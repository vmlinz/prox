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

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

public class UdpProxySession implements Runnable {

    private static final String TAG = "UdpProxySession";

    private final int sourcePort;

    private final InetAddress remoteAddress;
    private final int remotePort;

    private final Selector selector;
    private final DatagramChannel serverChannel;

    private final Thread thread;

    private final ByteBuffer buffer = ByteBuffer.allocate(0xFFFF);

    private volatile boolean running = true;

    public UdpProxySession(int sourcePort, InetAddress remoteAddress, int remotePort) throws IOException {
        this.sourcePort = sourcePort;

        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;

        selector = Selector.open();

        serverChannel = DatagramChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(0));
        serverChannel.register(selector, SelectionKey.OP_READ);

        thread = new Thread(this, "DNS Proxy Session");
        thread.start();
    }

    public void send(ByteBuffer buffer) throws IOException {
        serverChannel.send(buffer, new InetSocketAddress(remoteAddress, remotePort));
    }

    public void close() {
        running = false;
        IOUtils.closeQuietly(selector);
        IOUtils.closeQuietly(serverChannel);
    }

    @Override
    public synchronized void run() {
        try {
            while (running) {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (key.isReadable()) {
                        onReadable((DatagramChannel) key.channel());
                    }
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "running dns proxy error", e);
        } finally {
            close();
        }
    }

    private void onReadable(DatagramChannel channel) throws IOException {
        buffer.clear();
        channel.receive(buffer);
        buffer.flip();
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public InetAddress getRemoteAddress() {
        return remoteAddress;
    }

    public int getRemotePort() {
        return remotePort;
    }

}
