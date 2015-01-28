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
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import me.xingrz.prox.ProxVpnService;
import me.xingrz.prox.tcp.tunnel.IncomingTunnel;
import me.xingrz.prox.tcp.tunnel.OutgoingTunnel;
import me.xingrz.prox.transport.AbstractTransportProxy;

public class TcpProxySession extends AbstractTransportProxy.Session {

    private static final String TAG = "TcpProxySession";

    private IncomingTunnel localTunnel;
    private OutgoingTunnel remoteTunnel;

    public TcpProxySession(Selector selector, int sourcePort,
                           InetAddress remoteAddress, int remotePort) throws IOException {
        super(selector, sourcePort, remoteAddress, remotePort);
    }

    @Override
    public void close() throws IOException {
        IOUtils.closeQuietly(localTunnel);
        IOUtils.closeQuietly(remoteTunnel);
    }

    public boolean isEstablished() {
        return remoteTunnel != null && remoteTunnel.isTunnelEstablished();
    }

    public void accept(SocketChannel localChannel) throws IOException {
        localTunnel = new IncomingTunnel(selector, localChannel);
        remoteTunnel = new OutgoingTunnel(selector, new InetSocketAddress(getRemoteAddress(), getRemotePort()));

        ProxVpnService.getInstance().protect(remoteTunnel.socket());

        localTunnel.setBrother(remoteTunnel);
        remoteTunnel.setBrother(localTunnel);
        remoteTunnel.connect();

        Log.v(TAG, "Tunneling TCP session local:" + getSourcePort()
                + " <=> proxy:" + localChannel.socket().getLocalPort()
                + " <=> proxy:" + remoteTunnel.socket().getLocalPort()
                + " <=> " + getRemoteAddress().getHostAddress() + ":" + getRemotePort());
    }

}
