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

package me.xingrz.prox;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import me.xingrz.prox.internet.IPHeader;
import me.xingrz.prox.internet.IPv4Header;
import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;
import me.xingrz.prox.pac.AutoConfigManager;
import me.xingrz.prox.tcp.TcpHeader;
import me.xingrz.prox.tcp.TcpProxy;
import me.xingrz.prox.tcp.TcpProxySession;
import me.xingrz.prox.transport.TransportProxyRunner;
import me.xingrz.prox.udp.UdpHeader;
import me.xingrz.prox.udp.UdpProxy;
import me.xingrz.prox.udp.UdpProxySession;

public class ProxVpnService extends VpnService implements Runnable {

    private static final FormattingLogger logger = FormattingLoggers.getContextLogger();


    private static InetAddress getAddressQuietly(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * VPN 网关所运行的地址
     */
    public static final InetAddress PROXY_ADDRESS = getAddressQuietly("10.80.19.20");

    /**
     * 入站数据包发往此地址可被 VPN 截获
     */
    public static final InetAddress FAKE_CLIENT_ADDRESS = getAddressQuietly("10.80.7.20");


    private static ProxVpnService instance;

    public static ProxVpnService getInstance() {
        return instance;
    }


    public static final String EXTRA_PAC_URL = "pac_url";


    private Thread thread;

    private ParcelFileDescriptor intf;
    private FileOutputStream ingoing;

    private byte[] packet;

    private IPHeader ipHeader;
    private IPv4Header iPv4Header;

    private TcpHeader tcpHeader;
    private UdpHeader udpHeader;

    private TransportProxyRunner proxyRunner;

    private TcpProxy tcpProxy;
    private UdpProxy udpProxy;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        packet = new byte[0xffff];

        ipHeader = new IPHeader(packet);
        iPv4Header = new IPv4Header(packet);

        tcpHeader = new TcpHeader(packet);
        udpHeader = new UdpHeader(packet);

        AutoConfigManager.createInstance();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (thread != null) {
            thread.interrupt();
        }

        thread = new Thread(this, "VpnServer");

        String configUrl = intent.getStringExtra(EXTRA_PAC_URL);
        AutoConfigManager.getInstance().load(configUrl, new AutoConfigManager.ConfigLoadCallback() {
            @Override
            public void onConfigLoad() {
                thread.start();
                logger.d("VPN service started");
            }
        });

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }

        IOUtils.closeQuietly(ingoing);
        ingoing = null;

        IOUtils.closeQuietly(intf);
        intf = null;

        IOUtils.closeQuietly(proxyRunner);
        IOUtils.closeQuietly(tcpProxy);
        IOUtils.closeQuietly(udpProxy);

        AutoConfigManager.destroy();

        instance = null;

        logger.d("VPN service destroyed");

        super.onDestroy();
    }

    @Override
    public void run() {
        FileInputStream outgoing;

        try {
            proxyRunner = new TransportProxyRunner();

            tcpProxy = proxyRunner.create(TcpProxy.class);
            logger.d("TCP proxy started");

            udpProxy = proxyRunner.create(UdpProxy.class);
            logger.d("UDP proxy started");

            proxyRunner.start();

            intf = establish();
            logger.d("VPN interface established");

            ingoing = new FileOutputStream(intf.getFileDescriptor());
            outgoing = new FileInputStream(intf.getFileDescriptor());

            int size;
            while ((size = outgoing.read(packet)) != -1) {
                if (!tcpProxy.isRunning()) {
                    logger.e("TCP proxy unexpectedly stopped");
                    break;
                }

                if (!udpProxy.isRunning()) {
                    logger.e("UDP proxy unexpectedly stopped");
                    break;
                }

                if (size > 0) {
                    onIPPacketReceived(size);
                }
            }

            IOUtils.closeQuietly(outgoing);
        } catch (IOException e) {
            logger.w(e, "VPN ended with exception");
        }
    }

    private ParcelFileDescriptor establish() {
        return new Builder()
                .addAddress(PROXY_ADDRESS, 24)
                .addRoute("0.0.0.0", 0)
                .establish();
    }

    private void onIPPacketReceived(int size) throws IOException {
        if (ipHeader.version() == IPHeader.VERSION_4) {
            onIPv4PacketReceived(size);
        }
    }

    private void onIPv4PacketReceived(int size) throws IOException {
        if (iPv4Header.totalLength() != size) {
            logger.w("Ignored IP packet with wrong length");
            return;
        }

        if (!iPv4Header.getSourceIpAddress().equals(PROXY_ADDRESS)) {
            return;
        }

        switch (iPv4Header.protocol()) {
            case IPHeader.PROTOCOL_TCP:
                onTCPPacketReceived();
                break;
            case IPHeader.PROTOCOL_UDP:
                onUDPPacketReceived();
                break;
        }
    }

    private void onTCPPacketReceived() throws IOException {
        if (tcpHeader.getSourcePort() == tcpProxy.port()) {
            // 如果是来自本地 TCP 代理，表示是从隧道回来的包，回写给 VPN

            TcpProxySession session = tcpProxy.getSession(tcpHeader.getDestinationPort());
            if (session == null) {
                return;
            }

            session.active();

            if (tcpHeader.fin()) {
                session.finish();
                tcpProxy.finishSession(tcpHeader.getDestinationPort());
            }

            // 因为 TCP 是传输层协议，而我们的 VPN 是工作在网络层的
            // 所以我们要直接在网络层将 TCP 包转发到 TCPProxy
            // 再用工作在传输层的 TCPProxy 接收，随后再转发到外网
            // 反之亦然

            tcpHeader.setSourceIp(session.getRemoteAddress());
            tcpHeader.setSourcePort(session.getRemotePort());
            tcpHeader.setDestinationIp(PROXY_ADDRESS);
            tcpHeader.recomputeChecksum();
            tcpHeader.writeTo(ingoing);
        } else {
            // 否则是即将发往公网的数据包，将它转发给我们的 TCP 代理
            TcpProxySession session = tcpProxy.pickSession(tcpHeader.getSourcePort(),
                    tcpHeader.getDestinationIpAddress(), tcpHeader.getDestinationPort());

            session.active();

            tcpHeader.setSourceIp(FAKE_CLIENT_ADDRESS);
            tcpHeader.setDestinationIp(PROXY_ADDRESS);
            tcpHeader.setDestinationPort(tcpProxy.port());
            tcpHeader.recomputeChecksum();
            tcpHeader.writeTo(ingoing);
        }
    }

    private void onUDPPacketReceived() throws IOException {
        if (udpHeader.getSourcePort() == udpProxy.port()) {
            // UDP 代理丢回给 VPN 的

            UdpProxySession session = udpProxy.finishSession(udpHeader.getDestinationPort());
            if (session == null) {
                return;
            }

            udpHeader.setSourceIp(session.getRemoteAddress());
            udpHeader.setSourcePort(session.getRemotePort());
            udpHeader.setDestinationIp(PROXY_ADDRESS);
            udpHeader.recomputeChecksum();
            udpHeader.writeTo(ingoing);
        } else {
            // 发出去前被 VPN 截获的

            // 创建会话，让 UDP 代理服务器先准备好好外网端的通道
            UdpProxySession session = udpProxy.pickSession(udpHeader.getSourcePort(),
                    udpHeader.getDestinationIpAddress(), udpHeader.getDestinationPort());

            // 保护外网端通道不被 VPN 拦截
            protect(session.socket());

            udpHeader.setSourceIp(FAKE_CLIENT_ADDRESS);
            udpHeader.setDestinationIp(PROXY_ADDRESS);
            udpHeader.setDestinationPort(udpProxy.port());
            udpHeader.recomputeChecksum();
            udpHeader.writeTo(ingoing);
        }
    }

}
