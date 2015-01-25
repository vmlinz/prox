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

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Iterator;

public abstract class AbstractTransportProxy
        <C extends AbstractSelectableChannel, S extends AbstractTransportProxy.Session>
        implements Runnable, Closeable {

    private static final String TAG = "TransportLayerProxy";

    public static abstract class Session implements Closeable {

        private final int sourcePort;

        private final InetAddress remoteAddress;
        private final int remotePort;

        private boolean finished = false;

        long lastActive = System.currentTimeMillis();

        public Session(int sourcePort, InetAddress remoteAddress, int remotePort) {
            this.sourcePort = sourcePort;
            this.remoteAddress = remoteAddress;
            this.remotePort = remotePort;
        }

        public int getSourcePort() {
            return sourcePort;
        }

        public InetAddress getRemoteAddress() {
            return remoteAddress;
        }

        public int getRemotePort() {
            return remotePort;
        }

        public boolean isFinished() {
            return finished;
        }

        protected void finish() {
            finished = true;
        }

        public void active() {
            lastActive = System.currentTimeMillis();
        }

    }

    private final long sessionTimeout;

    private final LruCache<Integer, S> sessions;

    private final Handler sessionCleaner = new Handler(Looper.getMainLooper());

    private final Runnable sessionCleanerRunnable = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();

            for (S session : sessions.snapshot().values()) {
                if (now - session.lastActive >= sessionTimeout) {
                    sessions.remove(session.getSourcePort());
                    Log.v(TAG, "Cleaned expired " + proxyName + " session:" + session.getSourcePort());
                }
            }

            sessionCleaner.postDelayed(sessionCleanerRunnable, sessionTimeout);
        }
    };

    private final String proxyName;

    protected final Selector selector;
    protected final C serverChannel;

    private final Thread thread;

    public AbstractTransportProxy(int maxSessionCount, long sessionTimeout, final String proxyName)
            throws IOException {

        this.sessionTimeout = sessionTimeout;
        this.sessions = new LruCache<Integer, S>(maxSessionCount) {
            @Override
            protected void entryRemoved(boolean evicted, Integer key, S oldValue, S newValue) {
                IOUtils.closeQuietly(oldValue);
                if (oldValue.isFinished()) {
                    Log.v(TAG, "Removed finished " + proxyName + " session:" + key);
                } else {
                    Log.v(TAG, "Terminated " + proxyName + " session:" + key);
                }
            }
        };

        this.proxyName = proxyName;

        this.selector = Selector.open();

        this.serverChannel = createChannel(selector);

        this.thread = new Thread(this, proxyName);
        this.thread.start();

        Log.d(TAG, "Proxy " + proxyName + " running on " + port());

        sessionCleaner.postDelayed(sessionCleanerRunnable, sessionTimeout);
    }

    protected abstract C createChannel(Selector selector) throws IOException;

    public abstract int port();

    protected abstract void onSelected(SelectionKey key) throws IOException;

    @Override
    public synchronized void run() {
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
        } catch (IOException e) {
            Log.w(TAG, "Running UDP proxy error", e);
        }

        IOUtils.closeQuietly(selector);
        IOUtils.closeQuietly(serverChannel);

        Log.v(TAG, "UDP server closed");
    }

    @Override
    public void close() throws IOException {
        sessions.evictAll();
        thread.interrupt();
        sessionCleaner.removeCallbacksAndMessages(null);
    }

    /**
     * 创建新的会话，子类必须重载此方法
     *
     * @param sourcePort    来源端口，作为标识
     * @param remoteAddress 目标地址
     * @param remotePort    目标端口
     * @return 新的 {@link me.xingrz.prox.AbstractTransportProxy.Session} 实例
     * @throws IOException
     */
    protected abstract S createSession(int sourcePort, InetAddress remoteAddress, int remotePort)
            throws IOException;

    public S pickSession(int sourcePort, InetAddress remoteAddress, int remotePort) throws IOException {
        S session = createSession(sourcePort, remoteAddress, remotePort);
        sessions.put(sourcePort, session);
        return session;
    }

    public S getSession(int sourcePort) {
        return sessions.get(sourcePort);
    }

    /**
     * 完成并删除会话
     *
     * @param sourcePort 来源端口
     * @return 会话
     */
    public S finishSession(int sourcePort) {
        return sessions.remove(sourcePort);
    }

}
