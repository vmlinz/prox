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

import android.util.SparseArray;

public class NatSessionManager<S extends AbstractTransportProxy.Session> extends SparseArray<S> {

    private final int maxCount;

    public NatSessionManager(int maxCount) {
        this.maxCount = maxCount;
    }

    @Override
    public void delete(int key) {
        triggerOnRemoved(get(key));
        super.delete(key);
    }

    @Override
    public void removeAt(int index) {
        triggerOnRemoved(valueAt(index));
        super.removeAt(index);
    }

    @Override
    public void put(int key, S value) {
        triggerOnRemoved(get(key));
        super.put(key, value);
        onAdded();
    }

    @Override
    public void setValueAt(int index, S value) {
        triggerOnRemoved(valueAt(index));
        super.setValueAt(index, value);
    }

    @Override
    public void clear() {

        for (int i = 0; i < size(); i++) {
            removeAt(i);
        }
    }

    protected void onAdded() {
        if (size() > maxCount) {
            for (int i = 0; i < size(); i++) {
                if (shouldRecycle(valueAt(i))) {
                    removeAt(i);
                }
            }
        }
    }

    private void triggerOnRemoved(S session) {
        if (session != null) {
            onRemoved(session);
        }
    }

    protected void onRemoved(S session) {
    }

    protected boolean shouldRecycle(S session) {
        return false;
    }

}
