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

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import me.xingrz.prox.AbstractTransportProxy;

public class UdpProxySession extends AbstractTransportProxy.Session {

    private static final String TAG = "UdpProxySession";

    private final Selector selector;
    private final DatagramChannel serverChannel;

    public UdpProxySession(Selector selector, int sourcePort,
                           InetAddress remoteAddress, int remotePort) throws IOException {
        super(sourcePort, remoteAddress, remotePort);

        this.selector = selector;

        this.serverChannel = DatagramChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.socket().bind(new InetSocketAddress(0));
    }

    public DatagramSocket socket() {
        return serverChannel.socket();
    }

    public void send(ByteBuffer buffer) throws IOException {
        serverChannel.register(this.selector, SelectionKey.OP_READ, this);
        serverChannel.send(buffer, new InetSocketAddress(getRemoteAddress(), getRemotePort()));
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(serverChannel);
    }

}
