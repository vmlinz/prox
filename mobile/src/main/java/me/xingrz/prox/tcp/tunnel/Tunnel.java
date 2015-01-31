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

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.selectable.Readable;
import me.xingrz.prox.selectable.Writable;

/**
 * Tunnel 基类
 * 一个 Tunnel 包装了一条 {@link java.nio.channels.SocketChannel}
 * 两个 Tunnel 可以互相 {@link #setBrother(Tunnel)} 对接，内部便会自动维护互相的读写
 */
public abstract class Tunnel implements Closeable, Readable, Writable {

    protected final FormattingLogger logger;

    protected final Selector selector;
    protected final SocketChannel channel;

    protected Tunnel brother;

    private ByteBuffer receiving = ByteBuffer.allocate(0xFFFF);
    private ByteBuffer remaining = ByteBuffer.allocate(0xFFFF);

    private boolean closed;

    public Tunnel(Selector selector, SocketChannel channel, String sessionKey) {
        this.selector = selector;
        this.channel = channel;
        this.logger = getLogger(sessionKey);
    }

    protected abstract FormattingLogger getLogger(String sessionKey);

    public Socket socket() {
        return channel.socket();
    }

    /**
     * @param brother 对接到这条 Tunnel
     */
    public final void setBrother(Tunnel brother) {
        this.brother = brother;
    }

    /**
     * 开始接收数据
     */
    public final void beginReceiving() {
        try {
            if (channel.isBlocking()) {
                channel.configureBlocking(false);
            }

            channel.register(selector, SelectionKey.OP_READ, this);
        } catch (IOException e) {
            logger.w(e, "Failed to begin receiving, close");
            IOUtils.closeQuietly(this);
        }
    }

    /**
     * 当 Selector 选择到 OP_READ 的时候会调用此方法
     *
     * @param key Selection Key
     */
    @Override
    public void onReadable(SelectionKey key) {
        receiving.clear();

        int read;

        try {
            read = channel.read(receiving);
        } catch (IOException e) {
            logger.w(e, "Failed to read from channel, terminated");
            IOUtils.closeQuietly(this);
            return;
        }

        if (read == -1) {
            IOUtils.closeQuietly(this);
            return;
        }

        receiving.flip();

        // 空，忽略
        if (!receiving.hasRemaining()) {
            return;
        }

        // 子类处理了数据
        if (afterReceived(receiving)) {
            return;
        }

        // 没东西需要处理
        if (!receiving.hasRemaining()) {
            return;
        }

        brother.beforeSending(receiving);

        if (!brother.write(receiving)) {
            logger.v("Brother not ready for receiving, canceled");
            key.cancel();
        }
    }

    /**
     * Tunnel 从自己的 {@link #channel} 接收到了数据
     *
     * @param buffer 接收到的数据
     * @return {@code true} 表示已自己消化了数据，{@code false} 则会将数据发给 {@link #brother}
     */
    protected abstract boolean afterReceived(ByteBuffer buffer);

    /**
     * 另一条 Tunnel 即将向自己发送数据
     *
     * @param buffer 即将发送的数据
     */
    protected abstract void beforeSending(ByteBuffer buffer);

    /**
     * 向该 Tunnel 的 {@link #channel} 发送数据
     *
     * @param buffer 即将发送的数据
     * @return 是否完整发送了所有数据
     */
    public boolean write(ByteBuffer buffer) {
        return writeInternal(buffer);
    }

    protected boolean writeInternal(ByteBuffer buffer) {
        try {
            while (buffer.hasRemaining()) {
                if (channel.write(buffer) == 0) {
                    break;
                }
            }
        } catch (IOException e) {
            logger.w(e, "Failed writing to " + channelToString());
            return false;
        }

        if (buffer.hasRemaining()) {
            keepRemaining(buffer);
            return false;
        } else {
            return true;
        }
    }

    protected void keepRemaining(ByteBuffer buffer) {
        remaining.clear();
        remaining.put(buffer);
        remaining.flip();

        // 通道连接完毕后即可写入，所以不需要注册
        if (!channel.isConnected()) {
            return;
        }

        try {
            channel.register(selector, SelectionKey.OP_WRITE, this);
        } catch (ClosedChannelException e) {
            logger.w(e, "Failed to register OP_WRITE since channel is closed");
            IOUtils.closeQuietly(this);
        }
    }

    /**
     * 当 Selector 选择到 OP_WRITE 的时候会调用此方法
     *
     * @param key Selection Key
     */
    @Override
    public void onWritable(SelectionKey key) {
        beforeSending(remaining);
        writeRemaining();
        key.cancel();
    }

    protected void writeRemaining() {
        try {
            while (remaining.hasRemaining()) {
                if (channel.write(remaining) == 0) {
                    break;
                }
            }
        } catch (IOException e) {
            logger.w(e, "Failed writing remaining data, closed");
            IOUtils.closeQuietly(this);
            return;
        }

        brother.beginReceiving();
    }

    /**
     * 关闭 Tunnel 并释放资源
     */
    @Override
    public void close() {
        closeInternal(true);
    }

    private void closeInternal(boolean withBrother) {
        if (!closed) {
            IOUtils.closeQuietly(channel);

            if (withBrother && brother != null) {
                brother.closeInternal(false);
            }

            remaining = null;
            receiving = null;

            brother = null;

            closed = true;
            onClose();
        }
    }

    /**
     * Tunnel 已关闭
     */
    protected abstract void onClose();

    protected final String channelToString() {
        return String.format("Channel[%s:%d -> %s:%d]",
                channel.socket().getLocalAddress().getHostAddress(),
                channel.socket().getLocalPort(),
                channel.socket().getInetAddress().getHostAddress(),
                channel.socket().getPort());
    }

}
