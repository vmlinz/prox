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

package me.xingrz.prox.transport;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;
import me.xingrz.prox.selectable.Acceptable;
import me.xingrz.prox.selectable.Connectible;
import me.xingrz.prox.selectable.Readable;
import me.xingrz.prox.selectable.Writable;

/**
 * 代理服务器
 * <p/>
 * 该类内部通过一条线程维护了一个 {@link java.nio.channels.Selector}，让 {@link me.xingrz.prox.tcp.TcpProxy}
 * 和 {@link me.xingrz.prox.udp.UdpProxy} 能够运行在统一条线程之上。
 *
 * @author XiNGRZ
 */
public class TransportProxyRunner implements Runnable, Closeable {

    protected final FormattingLogger logger = FormattingLoggers.getContextLogger();

    private final Selector selector;
    private final Thread thread;

    public TransportProxyRunner() throws IOException {
        selector = Selector.open();
        thread = new Thread(this, "ProxyThread");
    }

    /**
     * 创建代理服务
     *
     * @param cls 代理服务器类，必须继承自 {@link me.xingrz.prox.transport.AbstractTransportProxy}
     * @param <P> 继承自 {@link me.xingrz.prox.transport.AbstractTransportProxy} 的类
     * @return 实例
     * @throws IOException 实例启动时遇到 IO 异常
     */
    public <P extends AbstractTransportProxy> P create(Class<P> cls) throws IOException {
        try {
            P instance = cls.newInstance();
            instance.start(selector);
            return instance;
        } catch (InstantiationException | IllegalAccessException e) {
            return null;
        }
    }

    /**
     * 启动 Selector 进程
     */
    public void start() {
        thread.start();
    }

    @Override
    public void run() {
        try {
            while (true) {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        ((Acceptable) key.attachment()).onAcceptable(key);
                    } else if (key.isConnectable()) {
                        ((Connectible) key.attachment()).onConnectible(key);
                    } else if (key.isReadable()) {
                        ((Readable) key.attachment()).onReadable(key);
                    } else if (key.isWritable()) {
                        ((Writable) key.attachment()).onWritable(key);
                    }
                }
            }
        } catch (ClosedSelectorException ignored) {
            logger.d("Proxy selector closed");
        } catch (IOException e) {
            logger.w(e, "Proxy running error");
        }

        logger.d("Proxy closed");
    }

    /**
     * @return 是否正在运行
     */
    public boolean isRunning() {
        return thread.isAlive();
    }

    /**
     * 关闭代理服务
     *
     * @throws IOException 关闭时遇到异常
     */
    @Override
    public void close() throws IOException {
        selector.close();
    }

}
