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

package me.xingrz.prox.tcp;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedList;

import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;

public class TcpQueue {

    private static final FormattingLogger logger = FormattingLoggers.getContextLogger();

    private static final int MAX_EXECUTING_COUNT = 20;

    private static final LinkedList<TcpProxySession> queue = new LinkedList<>();
    private static final HashMap<InetSocketAddress, Integer> executions = new HashMap<>();

    public static void tick() {
        TcpProxySession session = queue.peek();
        if (session == null) {
            return;
        }

        InetSocketAddress destination = session.getDestination();

        Integer executing = executions.get(destination);
        if (executing == null) {
            executing = 0;
        }

        if (executing < MAX_EXECUTING_COUNT) {
            queue.remove(session);

            logger.v("Picked session %08x. Destination %s executing %d, remains %d", session.hashCode(),
                    destination.getAddress().getHostAddress(), executing, queue.size());

            session.connect();
            executing++;
            executions.put(destination, executing);
        } else {
            logger.v("Destination %s executing %d sessions, remains %d, waiting",
                    destination.getAddress().getHostAddress(), executing, queue.size());
        }
    }

    public static void queue(TcpProxySession session) {
        queue.offer(session);

        logger.v("Enqueued session %08x, destination %s",
                session.hashCode(), session.getDestination().getAddress().getHostAddress());

        tick();
    }

    public static void finish(TcpProxySession session) {
        InetSocketAddress destination = session.getDestination();

        Integer executing = executions.get(destination);
        if (executing != null) {
            if (executing <= 1) {
                executions.remove(destination);
            } else {
                executions.put(destination, executing - 1);
            }
        }

        logger.v("Finished session %08x, destination %s",
                session.hashCode(), session.getDestination().getAddress().getHostAddress());

        tick();
    }

    public static void remove(TcpProxySession session) {
        queue.remove(session);
        logger.v("Removed non-executed session %08x, destination %s",
                session.hashCode(), session.getDestination().getAddress().getHostAddress());

        tick();
    }

}
