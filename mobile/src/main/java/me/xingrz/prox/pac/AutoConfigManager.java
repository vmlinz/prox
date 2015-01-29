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

package me.xingrz.prox.pac;

import android.os.Handler;
import android.os.HandlerThread;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;

public class AutoConfigManager {

    private static volatile AutoConfigManager instance;

    public static void createInstance() {
        instance = new AutoConfigManager();
    }

    public static AutoConfigManager getInstance() {
        return instance;
    }

    public static void destroy() {
        instance.dispose();
    }


    public static interface ConfigLoadCallback {
        public void onConfigLoad();
    }

    public static interface ProxyLookupCallback {
        public void onProxyLookup(String proxy);
    }


    private static final FormattingLogger logger = FormattingLoggers.getContextLogger();

    private static final long PAC_RELOAD_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);


    private final HandlerThread thread;
    private final Handler handler;

    private String url;

    private AutoConfig autoConfig;

    private AutoConfigManager() {
        thread = new HandlerThread("ConfigManager");
        thread.start();

        handler = new Handler(thread.getLooper());
    }

    public void load(String url, final ConfigLoadCallback callback) {
        this.url = url;

        handler.removeCallbacks(reloading);

        handler.post(new Runnable() {
            @Override
            public void run() {
                blockingLoadConfig();
                if (callback != null) {
                    callback.onConfigLoad();
                }
            }
        });
    }

    private final Runnable reloading = new Runnable() {
        @Override
        public void run() {
            blockingLoadConfig();
        }
    };

    private void blockingLoadConfig() {
        logger.v("Loading proxy auto config from %s", url);

        try {
            AutoConfig newConfig = AutoConfig.fromUrl(url);

            if (autoConfig != null) {
                autoConfig.destroy();
            }

            autoConfig = newConfig;

            logger.v("Proxy auto config loaded");
        } catch (IOException e) {
            logger.w(e, "Error loading auto config");
        }

        handler.postDelayed(reloading, PAC_RELOAD_INTERVAL_MS);
    }

    public void lookup(final String host, final ProxyLookupCallback callback) {
        logger.v("Queued to lookup for host %s", host);
        handler.post(new Runnable() {
            @Override
            public void run() {
                String proxy = autoConfig.findProxyForHost(host);
                callback.onProxyLookup(proxy);
                logger.v("Finished for host %s: %s", host, proxy);
            }
        });
    }

    private void dispose() {
        handler.removeCallbacksAndMessages(null);

        thread.interrupt();

        if (autoConfig != null) {
            autoConfig.destroy();
            autoConfig = null;
        }
    }

}
