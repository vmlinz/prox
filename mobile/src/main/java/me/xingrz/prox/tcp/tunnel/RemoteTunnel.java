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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public abstract class RemoteTunnel extends Tunnel {

    private static SocketChannel makeChannel() throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.socket().bind(new InetSocketAddress(0));
        return channel;
    }

    private ByteBuffer beginning;

    public RemoteTunnel(Selector selector, String sessionKey) throws IOException {
        super(selector, makeChannel(), sessionKey);
    }

    protected void onConnected() throws IOException {
        beginReceiving();

        logger.v("Connected and start to write buffered %d bytes beginning", beginning.remaining());
        write(beginning, true);
        beginning.clear();
    }

    public final void onConnectible() throws IOException {
        if (channel.finishConnect()) {
            onConnected();
        }
    }

    public void connect(InetSocketAddress address) throws IOException {
        if (channel.connect(address)) {
            onConnected();
        } else {
            channel.register(selector, SelectionKey.OP_CONNECT, this);
            logger.v("Waiting for OP_CONNECT");
        }
    }

    /**
     * {@inheritDoc}
     * {@link me.xingrz.prox.tcp.tunnel.RemoteTunnel} 的 {@link #write(java.nio.ByteBuffer, boolean)}
     * 方法允许在 {@link #connect(java.net.InetSocketAddress)} 前调用，数据会先缓存在内部
     *
     * @param buffer              即将发送的数据
     * @param shouldKeepRemaining 如果没发送完整，是否等 {@link #channel} 再次就绪后继续写入
     * @return
     * @throws IOException
     */
    @Override
    protected boolean write(ByteBuffer buffer, boolean shouldKeepRemaining) throws IOException {
        if (!channel.isConnected() && shouldKeepRemaining) {
            logger.v("Writes is buffered, since tunnel is not connected");

            if (beginning == null) {
                beginning = ByteBuffer.allocate(buffer.capacity());
            }

            beginning.clear();
            beginning.put(buffer);
            beginning.flip();

            return false;
        } else {
            return super.write(buffer, shouldKeepRemaining);
        }
    }

}
