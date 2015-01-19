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

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class ProxVpnService extends VpnService implements Runnable {

    private static final String TAG = "ProxVpnService";

    private static ProxVpnService instance;

    public static ProxVpnService getInstance() {
        return instance;
    }

    public static final String EXTRA_PAC_URL = "pac_url";

    private String configUrl;

    private Thread thread;

    private ParcelFileDescriptor intf;
    private FileOutputStream ingoing;

    private ProxAutoConfig pac;

    private byte[] packet;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        packet = new byte[20480];
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (thread != null) {
            thread.interrupt();
        }

        configUrl = intent.getStringExtra(EXTRA_PAC_URL);

        thread = new Thread(this, "VPN Thread");
        thread.start();

        Log.d(TAG, "VPN service started with PAC: " + configUrl);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }

        if (intf != null) {
            intf = null;
        }

        if (ingoing != null) {
            ingoing = null;
        }

        instance = null;

        Log.d(TAG, "VPN service destroyed");

        super.onDestroy();
    }

    @Override
    public synchronized void run() {
        FileInputStream outgoing = null;

        try {
            pac = ProxAutoConfig.fromUrl(configUrl);
            Log.d(TAG, "PAC loaded");

            intf = establish();
            Log.d(TAG, "VPN interface established");

            ingoing = new FileOutputStream(intf.getFileDescriptor());
            outgoing = new FileInputStream(intf.getFileDescriptor());

            int size;
            while ((size = outgoing.read(packet)) != -1) {
                if (size > 0) {
                    onIPPacketReceived(size);
                }
            }

            outgoing.close();
        } catch (IOException e) {
            Log.e(TAG, "VPN ended with error", e);
        } finally {
            IOUtils.closeQuietly(ingoing);
            IOUtils.closeQuietly(outgoing);
            IOUtils.closeQuietly(intf);
        }
    }

    private ParcelFileDescriptor establish() {
        return new Builder()
                .addAddress("192.168.0.1", 24)
                .addRoute("0.0.0.0", 0)
                .establish();
    }

    private void onIPPacketReceived(int size) throws IOException {
    }

}
