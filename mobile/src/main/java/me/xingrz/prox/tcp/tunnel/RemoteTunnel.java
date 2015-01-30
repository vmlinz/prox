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

package me.xingrz.prox.tcp.tunnel;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import me.xingrz.prox.selectable.Connectible;

public abstract class RemoteTunnel extends Tunnel implements Connectible {

    private static SocketChannel makeChannel() throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(new InetSocketAddress(0));
        return channel;
    }

    public RemoteTunnel(Selector selector, String sessionKey) throws IOException {
        super(selector, makeChannel(), sessionKey);
    }

    public void connect(InetSocketAddress address) throws IOException {
        if (channel.connect(address)) {
            onConnectedInternal();
        } else {
            channel.register(selector, SelectionKey.OP_CONNECT, this);
            logger.v("Waiting for OP_CONNECT");
        }
    }

    @Override
    public final void onConnectible(SelectionKey key) {
        try {
            if (channel.finishConnect()) {
                onConnectedInternal();
            } else {
                IOUtils.closeQuietly(this);
            }
        } catch (IOException e) {
            logger.w(e, "Error finishing connect");
            IOUtils.closeQuietly(this);
        }
    }

    private void onConnectedInternal() throws IOException {
        onConnected();
        beginReceiving();
        handshake();
    }

    protected abstract void onConnected();

    protected void handshake() {
        establish();
    }

    protected void establish() {
        onEstablished();
        writeRemaining();
    }

    protected abstract void onEstablished();

    /**
     * Tunnel 是否完成了握手、可以开始写入数据
     *
     * @return 已握手完成建立
     */
    public boolean isEstablished() {
        return channel.isConnected();
    }

    /**
     * {@inheritDoc}
     * {@link me.xingrz.prox.tcp.tunnel.RemoteTunnel} 的 {@link #write(java.nio.ByteBuffer)}
     * 方法允许在 {@link #connect(java.net.InetSocketAddress)} 前调用，数据会先缓存在内部
     *
     * @param buffer 即将发送的数据
     * @return
     * @throws IOException
     */
    @Override
    public boolean write(ByteBuffer buffer) {
        if (!isEstablished()) {
            logger.v("Buffered %d bytes of write since tunnel is not connected", buffer.remaining());
            keepRemaining(buffer);
            return false;
        }

        return super.write(buffer);
    }

}
