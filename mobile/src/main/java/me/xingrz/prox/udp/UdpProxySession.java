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

import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;
import me.xingrz.prox.selectable.Readable;
import me.xingrz.prox.transport.AbstractTransportProxy;

public class UdpProxySession extends AbstractTransportProxy.Session implements Readable {

    private final UdpProxy udpProxy;
    private final DatagramChannel serverChannel;

    public UdpProxySession(UdpProxy udpProxy, Selector selector, int sourcePort,
                           InetAddress remoteAddress, int remotePort) throws IOException {
        super(selector, sourcePort, remoteAddress, remotePort);
        this.udpProxy = udpProxy;
        this.serverChannel = DatagramChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.socket().bind(new InetSocketAddress(0));
    }

    @Override
    public FormattingLogger getLogger() {
        return FormattingLoggers.getContextLogger(String.format("%08x", hashCode()));
    }

    public DatagramSocket socket() {
        return serverChannel.socket();
    }

    public void send(ByteBuffer buffer) throws IOException {
        serverChannel.register(selector, SelectionKey.OP_READ, this);
        serverChannel.send(buffer, new InetSocketAddress(getRemoteAddress(), getRemotePort()));
    }

    @Override
    public void onReadable(SelectionKey key) {
        udpProxy.receive((DatagramChannel) key.channel(), this);
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(serverChannel);
    }

}
