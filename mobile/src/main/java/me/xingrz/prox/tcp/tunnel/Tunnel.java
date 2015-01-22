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

import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import me.xingrz.prox.ProxVpnService;

public abstract class Tunnel implements Closeable {

    private static final String TAG = "Tunnel";

    private static final ByteBuffer GL_BUFFER = ByteBuffer.allocate(20000);

    protected abstract void onConnected(ByteBuffer buffer) throws IOException;

    protected abstract boolean isTunnelEstablished();

    protected abstract void afterReceived(ByteBuffer buffer) throws IOException;

    protected abstract void beforeSend(ByteBuffer buffer) throws IOException;

    protected abstract void onDispose();

    private SocketChannel innerChannel;
    private ByteBuffer m_SendRemainBuffer;
    private Selector selector;
    private Tunnel brotherTunnel;

    private volatile boolean disposed;

    private InetSocketAddress serverAddress;
    protected InetSocketAddress destinationAddress;

    public Tunnel(SocketChannel innerChannel, Selector selector) {
        this.innerChannel = innerChannel;
        this.selector = selector;
    }

    public Tunnel(InetSocketAddress serverAddress, Selector selector) throws IOException {
        SocketChannel innerChannel = SocketChannel.open();
        innerChannel.configureBlocking(false);
        this.innerChannel = innerChannel;
        this.selector = selector;
        this.serverAddress = serverAddress;
    }

    public void setBrotherTunnel(Tunnel brotherTunnel) {
        this.brotherTunnel = brotherTunnel;
    }

    public void connect(InetSocketAddress destinationAddress) throws IOException {
        if (!ProxVpnService.getInstance().protect(innerChannel.socket())) {
            Log.w(TAG, "failed protecting socket");
            return;
        }

        this.destinationAddress = destinationAddress;

        innerChannel.register(selector, SelectionKey.OP_CONNECT, this);//注册连接事件
        innerChannel.connect(serverAddress);//连接目标
    }

    protected void beginReceive() throws IOException {
        if (innerChannel.isBlocking()) {
            innerChannel.configureBlocking(false);
        }

        innerChannel.register(selector, SelectionKey.OP_READ, this);//注册读事件
    }

    protected boolean write(ByteBuffer buffer, boolean copyRemainData) throws IOException {
        int bytesSent;
        while (buffer.hasRemaining()) {
            bytesSent = innerChannel.write(buffer);
            if (bytesSent == 0) {
                break;//不能再发送了，终止循环
            }
        }

        if (buffer.hasRemaining()) {//数据没有发送完毕
            if (copyRemainData) {//拷贝剩余数据，然后侦听写入事件，待可写入时写入。
                //拷贝剩余数据
                if (m_SendRemainBuffer == null) {
                    m_SendRemainBuffer = ByteBuffer.allocate(buffer.capacity());
                }
                m_SendRemainBuffer.clear();
                m_SendRemainBuffer.put(buffer);
                m_SendRemainBuffer.flip();
                innerChannel.register(selector, SelectionKey.OP_WRITE, this);//注册写事件
            }
            return false;
        } else {//发送完毕了
            return true;
        }
    }

    protected void onTunnelEstablished() throws Exception {
        beginReceive();//开始接收数据
        brotherTunnel.beginReceive();//兄弟也开始收数据吧
    }

    public final void onConnectible() {
        try {
            if (innerChannel.finishConnect()) {
                onConnected(GL_BUFFER);
            } else {
                Log.w(TAG, "Failed connecting to " + serverAddress);
                close();
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed connecting to " + serverAddress, e);
            close();
        }
    }

    public final void onReadable(SelectionKey key) {
        try {
            ByteBuffer buffer = GL_BUFFER;
            buffer.clear();

            int bytesRead = innerChannel.read(buffer);
            if (bytesRead > 0) {
                buffer.flip();
                afterReceived(buffer);//先让子类处理，例如解密数据。
                if (isTunnelEstablished() && buffer.hasRemaining()) {//将读到的数据，转发给兄弟。
                    brotherTunnel.beforeSend(buffer);//发送之前，先让子类处理，例如做加密等。
                    if (!brotherTunnel.write(buffer, true)) {
                        key.cancel();//兄弟吃不消，就取消读取事件。
                    }
                }
            } else if (bytesRead < 0) {
                close();//连接已关闭，释放资源。
            }
        } catch (IOException e) {
            Log.w(TAG, "Error reading tunnel", e);
            close();
        }
    }

    public final void onWritable(SelectionKey key) {
        try {
            beforeSend(m_SendRemainBuffer);//发送之前，先让子类处理，例如做加密等。

            if (write(m_SendRemainBuffer, false)) {//如果剩余数据已经发送完毕
                key.cancel();//取消写事件。
                if (isTunnelEstablished()) {
                    brotherTunnel.beginReceive();//这边数据发送完毕，通知兄弟可以收数据了。
                } else {
                    beginReceive();//开始接收代理服务器响应数据
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Error writing tunnel", e);
            close();
        }
    }

    @Override
    public void close() {
        closeInternal(true);
    }

    private void closeInternal(boolean disposeBrother) {
        if (disposed) {
            return;
        }

        IOUtils.closeQuietly(innerChannel);

        if (brotherTunnel != null && disposeBrother) {
            brotherTunnel.closeInternal(false);//把兄弟的资源也释放了。
        }

        selector = null;

        innerChannel = null;
        brotherTunnel = null;

        m_SendRemainBuffer = null;

        disposed = true;
        onDispose();
    }

}
