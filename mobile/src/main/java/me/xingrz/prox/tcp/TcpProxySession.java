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

import android.net.Uri;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import me.xingrz.prox.ProxVpnService;
import me.xingrz.prox.internet.IpUtils;
import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;
import me.xingrz.prox.pac.AutoConfigManager;
import me.xingrz.prox.tcp.http.HostReverseLookup;
import me.xingrz.prox.tcp.http.HttpConnectHandler;
import me.xingrz.prox.tcp.tunnel.IncomingTunnel;
import me.xingrz.prox.tcp.tunnel.OutgoingTunnel;
import me.xingrz.prox.transport.AbstractTransportProxy;
import me.xingrz.prox.udp.dns.DnsReverseCache;

public class TcpProxySession extends AbstractTransportProxy.Session {

    private IncomingTunnel incomingTunnel;
    private OutgoingTunnel outgoingTunnel;

    public TcpProxySession(Selector selector, int sourcePort,
                           InetAddress remoteAddress, int remotePort) {
        super(selector, sourcePort, remoteAddress, remotePort);
    }

    @Override
    public FormattingLogger getLogger() {
        return FormattingLoggers.getContextLogger(String.format("%08x", hashCode()));
    }

    @Override
    public void close() throws IOException {
        IOUtils.closeQuietly(incomingTunnel);
        IOUtils.closeQuietly(outgoingTunnel);
    }

    public boolean isEstablished() {
        return outgoingTunnel != null && outgoingTunnel.isEstablished();
    }

    public void accept(SocketChannel localChannel) {
        active();

        incomingTunnel = new IncomingTunnel(selector, localChannel, String.format("%08x", hashCode())) {
            @Override
            protected void onParsedHost(String host) {
                if (host == null) {
                    host = DnsReverseCache.lookup(IpUtils.toInteger(getRemoteAddress()));
                    logger.v("Host DNS reverse lookup %s : %s", getRemoteAddress().getHostAddress(), host);
                }

                if (host == null) {
                    host = HostReverseLookup.get(getRemoteAddress().getHostAddress());
                    logger.v("Host history reverse lookup %s : %s", getRemoteAddress().getHostAddress(), host);
                }

                if (host == null) {
                    logger.v("Not a HTTP request");
                    connect(null);
                } else {
                    logger.v("Parsed HTTP Host: %s", host);
                    HostReverseLookup.put(getRemoteAddress().getHostAddress(), host);
                    AutoConfigManager.getInstance().lookup(host, new AutoConfigManager.ProxyLookupCallback() {
                        @Override
                        public void onProxyLookup(Uri proxy) {
                            connect(proxy);
                        }
                    });
                }
            }
        };

        logger.v("Established incoming tunnel local:%d <=> proxy:%d",
                getSourcePort(), incomingTunnel.socket().getLocalPort());

        try {
            outgoingTunnel = new OutgoingTunnel(selector, String.format("%08x", hashCode()));
        } catch (IOException e) {
            logger.w(e, "Failed to issue outgoing tunnel, close");
            IOUtils.closeQuietly(this);
            return;
        }

        ProxVpnService.getInstance().protect(outgoingTunnel.socket());

        logger.v("Established outgoing tunnel proxy:%d -> internet",
                outgoingTunnel.socket().getLocalPort());

        incomingTunnel.setBrother(outgoingTunnel);
        outgoingTunnel.setBrother(incomingTunnel);

        incomingTunnel.beginReceiving();
    }

    private void connect(Uri proxy) {
        try {
            outgoingTunnel.connect(getDestinationAddress(proxy));
        } catch (IOException e) {
            logger.w(e, "Error connecting outgoing tunnel to remote host");
            IOUtils.closeQuietly(this);
            return;
        }

        logger.v("Tunneling local:%d <=> in:%d <=> out:%d <=> %s:%d",
                getSourcePort(),
                incomingTunnel.socket().getLocalPort(), outgoingTunnel.socket().getLocalPort(),
                getRemoteAddress().getHostAddress(), getRemotePort());
    }

    private InetSocketAddress getDestinationAddress(Uri proxy) {
        if (proxy != null) {
            if (proxy.getScheme().equals(AutoConfigManager.PROXY_TYPE_HTTP)) {
                outgoingTunnel.setProxy(new HttpConnectHandler(outgoingTunnel,
                        getRemoteAddress().getHostAddress(), getRemotePort()));

                logger.v("Use HTTP proxy %s:%d", proxy.getHost(), proxy.getPort());

                return new InetSocketAddress(proxy.getHost(), proxy.getPort());
            } else {
                logger.v("Unsupported proxy scheme %s, ignored", proxy.getScheme());
            }
        }

        return new InetSocketAddress(getRemoteAddress(), getRemotePort());
    }

}
