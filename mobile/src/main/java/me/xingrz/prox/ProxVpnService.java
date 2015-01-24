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
import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import me.xingrz.prox.ip.IPHeader;
import me.xingrz.prox.ip.IPv4Header;
import me.xingrz.prox.tcp.NatSession;
import me.xingrz.prox.tcp.NatSessionManager;
import me.xingrz.prox.tcp.TcpHeader;
import me.xingrz.prox.tcp.TcpProxy;
import me.xingrz.prox.udp.UdpHeader;
import me.xingrz.prox.udp.UdpProxy;
import me.xingrz.prox.udp.UdpProxySession;

public class ProxVpnService extends VpnService implements Runnable {

    private static final String TAG = "ProxVpnService";


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
    public static final InetAddress FAKE_REMOTE_ADDRESS = getAddressQuietly("10.80.7.20");


    private static ProxVpnService instance;

    public static ProxVpnService getInstance() {
        return instance;
    }


    public static final String EXTRA_PAC_URL = "pac_url";


    private String configUrl;

    private Thread thread;

    private ParcelFileDescriptor intf;
    private FileOutputStream ingoing;

    private ProxAutoConfig pac;

    private byte[] packet;

    private IPHeader ipHeader;
    private IPv4Header iPv4Header;

    private TcpHeader tcpHeader;
    private UdpHeader udpHeader;

    private TcpProxy tcpProxy;
    private UdpProxy udpProxy;

    private NatSessionManager sessionManager;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        packet = new byte[0xffff];

        ipHeader = new IPHeader(packet);
        iPv4Header = new IPv4Header(packet);

        tcpHeader = new TcpHeader(packet);
        udpHeader = new UdpHeader(packet);

        sessionManager = NatSessionManager.getInstance();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (thread != null) {
            thread.interrupt();
        }

        configUrl = intent.getStringExtra(EXTRA_PAC_URL);

        thread = new Thread(this, "VPNServer");
        thread.start();

        Log.d(TAG, "VPN service started with PAC: " + configUrl);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }

        instance = null;

        Log.d(TAG, "VPN service destroyed");

        super.onDestroy();
    }

    @Override
    public synchronized void run() {
        FileInputStream outgoing = null;

        try {
            pac = ProxAutoConfig.fromUrl(configUrl);
            Log.d(TAG, "PAC loaded");

            intf = establish();
            Log.d(TAG, "VPN interface established");

            tcpProxy = new TcpProxy();
            tcpProxy.start();
            Log.d(TAG, "TCP proxy started");

            udpProxy = new UdpProxy();
            Log.d(TAG, "UDP proxy started");

            ingoing = new FileOutputStream(intf.getFileDescriptor());
            outgoing = new FileInputStream(intf.getFileDescriptor());

            int size;
            while ((size = outgoing.read(packet)) != -1) {
                if (size > 0) {
                    onIPPacketReceived(size);
                }
            }

            outgoing.close();
        } catch (IOException e) {
            Log.w(TAG, "VPN ended with exception", e);
        } finally {
            IOUtils.closeQuietly(ingoing);
            ingoing = null;

            IOUtils.closeQuietly(outgoing);

            IOUtils.closeQuietly(intf);
            intf = null;

            IOUtils.closeQuietly(tcpProxy);
            IOUtils.closeQuietly(udpProxy);

            if (pac != null) {
                pac.destroy();
            }
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
            Log.w(TAG, "ignored TCP packet with wrong length");
            return;
        }

        //Log.v(TAG, iPv4Header.toString());
        switch (iPv4Header.protocol()) {
            case IPHeader.PROTOCOL_TCP:
                //onTCPPacketReceived();
                break;
            case IPHeader.PROTOCOL_UDP:
                onUDPPacketReceived();
                break;
        }
    }

    private void onTCPPacketReceived() throws IOException {
        //Log.v(TAG, tcpHeader.toString());

        // 只处理经由本 VPN 发出去的包
        if (!tcpHeader.getSourceIpAddress().equals(PROXY_ADDRESS)) {
            return;
        }

        if (tcpHeader.getSourcePort() == tcpProxy.port()) {
            // 如果是来自本地 TCP 代理，表示是从隧道回来的包，回写给 VPN
            NatSession session = sessionManager.getSession(tcpHeader.getDestinationPort());
            if (session == null) {
                Log.w(TAG, "no session record for " + iPv4Header + " " + tcpHeader);
                return;
            }

            // 因为 TCP 是传输层协议，而我们的 VPN 是工作在网络层的
            // 所以我们要直接在网络层将 TCP 包转发到 TCPProxy
            // 再用工作在传输层的 TCPProxy 接收，随后再转发到外网
            // 反之亦然

            tcpHeader.setSourceIp(tcpHeader.getDestinationIp());
            tcpHeader.setSourcePort(session.remotePort);
            tcpHeader.setDestinationIp(PROXY_ADDRESS);
            tcpHeader.recomputeChecksum();
            tcpHeader.writeTo(ingoing);
        } else {
            // 否则是即将发往公网的数据包，将它转发给我们的 TCP 代理
            NatSession session = sessionManager.pickSession(
                    tcpHeader.getSourcePort(),
                    tcpHeader.getDestinationIp(),
                    tcpHeader.getDestinationPort());

            /*if (session.remoteHost == null) {
                String host = HttpHeaderParser.parseHost(packet,
                        tcpHeader.tcpDataOffset(), tcpHeader.tcpDataLength());
                if (host == null) {
                    host = tcpHeader.getDestinationIpAddress().getHostAddress();
                }

                session.remoteHost = host;
            }

            Log.v(TAG, "Host: " + session.remoteHost);

            if (session.proxy == null) {
                String proxy = pac.findProxyForUrl(null, session.remoteHost);
                if (proxy == null) {
                    proxy = "DIRECT";
                }

                session.proxy = proxy;
            }*/

            tcpHeader.setSourceIp(tcpHeader.getDestinationIp()); // 有必要的
            tcpHeader.setDestinationIp(PROXY_ADDRESS);
            tcpHeader.setDestinationPort(tcpProxy.port());
            tcpHeader.recomputeChecksum();
            tcpHeader.writeTo(ingoing);
        }
    }

    private void onUDPPacketReceived() throws IOException {
        Log.v(TAG, iPv4Header.toString());
        Log.v(TAG, udpHeader.toString());

        if (!udpHeader.getSourceIpAddress().equals(PROXY_ADDRESS)) {
            return;
        }

        if (udpHeader.getSourcePort() == udpProxy.port()) {
            // UDP 代理丢回给 VPN 的

            UdpProxySession session = udpProxy.finishSession(udpHeader.getDestinationPort());
            if (session == null) {
                Log.w(TAG, "UDP session invalid");
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
            UdpProxySession session = udpProxy.createSession(udpHeader.getSourcePort(),
                    udpHeader.getDestinationIpAddress(), udpHeader.getDestinationPort());

            // 保护外网端通道不被 VPN 拦截
            protect(session.socket());

            udpHeader.setSourceIp(udpHeader.getDestinationIp()); // 有必要
            udpHeader.setDestinationIp(PROXY_ADDRESS);
            udpHeader.setDestinationPort(udpProxy.port());
            udpHeader.recomputeChecksum();
            udpHeader.writeTo(ingoing);
        }
    }

}
