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

import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import me.xingrz.prox.logging.FormattingLogger;

/**
 * 传输层代理服务器抽象
 *
 * @param <C> 服务器通道，比如 {@link java.nio.channels.ServerSocketChannel} 或 {@link java.nio.channels.DatagramChannel}
 * @param <S> 会话
 */
public abstract class AbstractTransportProxy
        <C extends SelectableChannel, S extends AbstractTransportProxy.Session>
        implements Runnable, Closeable {

    /**
     * 会话抽象
     */
    public static abstract class Session implements Closeable {

        protected final FormattingLogger logger;

        protected final Selector selector;

        private final int sourcePort;

        private final InetAddress remoteAddress;
        private final int remotePort;

        private boolean finished = false;

        long lastActive = System.currentTimeMillis();

        public Session(Selector selector, int sourcePort, InetAddress remoteAddress, int remotePort) {
            this.selector = selector;
            this.sourcePort = sourcePort;
            this.remoteAddress = remoteAddress;
            this.remotePort = remotePort;
            this.logger = getLogger();
        }

        protected abstract FormattingLogger getLogger();

        /**
         * @return 来源端口
         */
        public int getSourcePort() {
            return sourcePort;
        }

        /**
         * @return 远端地址
         */
        public InetAddress getRemoteAddress() {
            return remoteAddress;
        }

        /**
         * @return 远端端口
         */
        public int getRemotePort() {
            return remotePort;
        }

        /**
         * @return 该会话是否已完成，或被强行终结
         */
        public boolean isFinished() {
            return finished;
        }

        /**
         * 标记会话为完成
         */
        public void finish() {
            finished = true;
        }

        /**
         * 标记会话活动，不然过期未活动会被回收
         */
        public void active() {
            lastActive = System.currentTimeMillis();
        }

    }

    protected final FormattingLogger logger = getLogger();

    private final long sessionTimeout;

    private final NatSessionManager<S> sessions;

    protected final Selector selector;
    protected final C serverChannel;

    private final Thread thread;

    public AbstractTransportProxy(int maxSessionCount, long sessionTimeout)
            throws IOException {

        this.sessionTimeout = sessionTimeout;
        this.sessions = new NatSessionManager<S>(maxSessionCount) {
            @Override
            protected void onRemoved(S session) {
                IOUtils.closeQuietly(session);
                if (session.isFinished()) {
                    logger.v("Removed finished session %08x", session.hashCode());
                } else {
                    logger.v("Terminated session %08x, session count: %s", session.hashCode(), size());
                }
            }

            @Override
            protected boolean shouldRecycle(S session) {
                return shouldRecycleSession(session);
            }
        };

        this.selector = Selector.open();

        this.serverChannel = createChannel(selector);

        this.thread = new Thread(this, logger.getTag());
        this.thread.start();

        logger.d("Proxy running on %d", port());
    }

    protected abstract FormattingLogger getLogger();

    protected abstract C createChannel(Selector selector) throws IOException;

    public abstract int port();

    protected abstract void onSelected(SelectionKey key);

    @Override
    public void run() {
        try {
            while (true) {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (key.isValid()) {
                        onSelected(key);
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

    public boolean isRunning() {
        return thread.isAlive();
    }

    @Override
    public void close() throws IOException {
        sessions.clear();
        IOUtils.closeQuietly(selector);
        IOUtils.closeQuietly(serverChannel);
    }

    /**
     * 创建新的会话，子类必须重载此方法
     *
     * @param sourcePort    来源端口，作为标识
     * @param remoteAddress 目标地址
     * @param remotePort    目标端口
     * @return 新的会话实例
     */
    protected abstract S createSession(int sourcePort, InetAddress remoteAddress, int remotePort)
            throws IOException;

    /**
     * 抽取一个会话
     * 默认实现为创建新会话并将它放入会话队列中，子类可以根据需要重载它，比如复用已有会话
     *
     * @param sourcePort    来源端口，作为标识
     * @param remoteAddress 目标地址
     * @param remotePort    目标端口
     * @return 新的会话实例
     */
    public S pickSession(int sourcePort, InetAddress remoteAddress, int remotePort) throws IOException {
        S session = createSession(sourcePort, remoteAddress, remotePort);
        sessions.put(sourcePort, session);
        return session;
    }

    /**
     * 获取一个已有的会话
     *
     * @param sourcePort 来源端口，作为标识
     * @return 会话实例，或 {@value null} 表示不存在
     */
    public S getSession(int sourcePort) {
        return sessions.get(sourcePort);
    }

    /**
     * 完成并删除会话
     *
     * @param sourcePort 来源端口
     * @return 会话实例，或 {@value null} 表示不存在
     */
    public S finishSession(int sourcePort) {
        S session = sessions.get(sourcePort);
        sessions.remove(sourcePort);
        return session;
    }

    protected boolean shouldRecycleSession(S session) {
        return System.currentTimeMillis() - session.lastActive >= sessionTimeout;
    }

}
