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

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import me.xingrz.prox.logging.FormattingLogger;

/**
 * Tunnel 基类
 * 一个 Tunnel 包装了一条 {@link java.nio.channels.SocketChannel}
 * 两个 Tunnel 可以互相 {@link #setBrother(Tunnel)} 对接，内部便会自动维护互相的读写
 */
public abstract class Tunnel implements Closeable {

    protected final FormattingLogger logger;

    protected final Selector selector;
    protected final SocketChannel channel;

    private Tunnel brother;

    private ByteBuffer remaining;

    private boolean closed;

    private boolean finished;

    public Tunnel(Selector selector, SocketChannel channel, String sessionKey) {
        this.selector = selector;
        this.channel = channel;
        this.logger = getLogger(sessionKey);
    }

    protected abstract FormattingLogger getLogger(String sessionKey);

    public Socket socket() {
        return channel.socket();
    }

    protected abstract boolean isTunnelEstablished();

    /**
     * Tunnel 从自己的 {@link #channel} 接收到了数据
     *
     * @param buffer 接收到的数据
     * @throws IOException
     */
    protected abstract void afterReceived(ByteBuffer buffer) throws IOException;

    /**
     * Tunnel 即将向自己的 {@link #channel} 发送数据
     *
     * @param buffer 即将发送的数据
     * @throws IOException
     */
    protected abstract void beforeSending(ByteBuffer buffer) throws IOException;

    /**
     * Tunnel 已关闭
     *
     * @param finished 完成
     */
    protected abstract void onClose(boolean finished);

    /**
     * @param brother 对接到这条 Tunnel
     */
    public final void setBrother(Tunnel brother) {
        this.brother = brother;
    }

    protected final void tunnelEstablished() throws IOException {
        beginReceiving();
        brother.beginReceiving();
    }

    public final void beginReceiving() throws IOException {
        if (channel.isBlocking()) {
            channel.configureBlocking(false);
        }

        channel.register(selector, SelectionKey.OP_READ, this);
        logger.v("Began receiving and waiting for OP_READ");
    }

    /**
     * 当 Selector 选择到 OP_READ 的时候会调用此方法
     *
     * @param key Selection Key
     * @throws IOException
     */
    public final void onReadable(SelectionKey key) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(0xFFFF);
        int read = channel.read(buffer);
        if (read > 0) {
            buffer.flip();
            afterReceived(buffer);
            logger.v("Received %d bytes", read);

            if (isTunnelEstablished() && buffer.hasRemaining()) {
                brother.beforeSending(buffer);
                logger.v("Sending to brother...");

                if (brother.write(buffer, true)) {
                    logger.v("Sent to brother");
                } else {
                    logger.v("Brother not ready for receiving, canceled");
                    key.cancel();
                }
            }
        } else if (read == -1) {
            finished = true;
            close();
            logger.v("Nothing to read, finished");
        }
    }

    /**
     * 当 Selector 选择到 OP_WRITE 的时候会调用此方法
     *
     * @param key Selection Key
     * @throws IOException
     */
    public final void onWritable(SelectionKey key) throws IOException {
        logger.v("Been writable, resuming writing");
        beforeSending(remaining);
        if (write(remaining, false)) {
            key.cancel();
            if (isTunnelEstablished()) {
                brother.beginReceiving();
            } else {
                beginReceiving();
            }
        }
    }

    /**
     * 向该 Tunnel 的 {@link #channel} 发送数据
     *
     * @param buffer              即将发送的数据
     * @param shouldKeepRemaining 如果没发送完整，是否等 {@link #channel} 再次就绪后继续写入
     * @return 是否完整发送了所有数据
     * @throws IOException
     */
    protected boolean write(ByteBuffer buffer, boolean shouldKeepRemaining) throws IOException {
        try {
            logger.v("Writing to channel");
            while (buffer.hasRemaining()) {
                if (channel.write(buffer) == 0) {
                    logger.v("Writing ended");
                    break;
                }
            }
        } catch (IOException e) {
            logger.w(e, "Failed writing to " + channelToString());
            return false;
        }

        if (buffer.hasRemaining()) {
            if (shouldKeepRemaining) {
                if (remaining == null) {
                    remaining = ByteBuffer.allocate(buffer.capacity());
                }

                remaining.clear();
                remaining.put(buffer);
                remaining.flip();

                channel.register(selector, SelectionKey.OP_WRITE, this);

                logger.v("Kept remaining buffer and waiting for a OP_WRITE");
            } else {
                logger.v("Writes dropped");
            }

            return false;
        } else {
            logger.v("Writing completed");
            return true;
        }
    }

    /**
     * 关闭 Tunnel 并释放资源
     *
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        closeInternal(true);
    }

    private void closeInternal(boolean withBrother) throws IOException {
        if (!closed) {
            channel.close();

            if (withBrother && brother != null) {
                brother.finished = brother.finished || finished;
                brother.closeInternal(false);
            }

            remaining = null;
            brother = null;

            closed = true;
            onClose(finished);
        }
    }

    protected final String channelToString() {
        return String.format("Channel[%s:%d -> %s:%d]",
                channel.socket().getLocalAddress().getHostAddress(),
                channel.socket().getLocalPort(),
                channel.socket().getInetAddress().getHostAddress(),
                channel.socket().getPort());
    }

}
