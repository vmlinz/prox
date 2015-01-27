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

import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;

/**
 * PAC 解析器
 *
 * @author XiNGRZ
 */
public class ProxAutoConfig {

    private static final String TAG = "ProxAutoConfig";

    /**
     * 从 {@code url} 抓取 PAC 内容并返回新实例
     *
     * @param url PAC 地址
     * @return 新的 {@code ProxAutoConfig} 实例
     * @throws IOException
     */
    public static ProxAutoConfig fromUrl(String url) throws IOException {
        HttpResponse response = new DefaultHttpClient().execute(new HttpGet(url));
        String config = EntityUtils.toString(response.getEntity());
        return new ProxAutoConfig(config);
    }

    private static final String PROXY_PREFIX = "PROXY ";
    private static final String PAC_FUNCTION = "FindProxyForURL";

    private final Context rhino;
    private final Scriptable scope;
    private final Function handler;

    /**
     * 以 PAC 内容创建新实例
     *
     * @param config PAC 内容
     */
    public ProxAutoConfig(String config) {
        rhino = Context.enter();
        rhino.setOptimizationLevel(-1);

        scope = rhino.initStandardObjects();
        rhino.evaluateString(scope, config, "pac", 1, null);

        handler = (Function) scope.get(PAC_FUNCTION, scope);
    }

    /**
     * 查找指定 {@code url} 或 {@code host} 的代理地址
     *
     * @param url  URL，与 {@code host} 不能同时为 {@code null}
     * @param host 主机名，与 {@code url} 不能同时为 {@code null}
     * @return 代理服务器地址，或 {@code null} 表示直连
     */
    public String findProxyForUrl(String url, String host) {
        if (handler == null) {
            Log.w(TAG, "no handler function found");
            return null;
        }

        Object result = handler.call(rhino, scope, scope, new Object[]{url, host});

        if (result == null) {
            Log.w(TAG, "null result");
            return null;
        }

        if (!(result instanceof String)) {
            Log.w(TAG, "result not String");
            return null;
        }

        String config = (String) result;

        if (config.startsWith(PROXY_PREFIX)) {
            return config.split(";")[0].substring(PROXY_PREFIX.length());
        }

        return null;
    }

    /**
     * 释放对象
     */
    public void destroy() {
        try {
            Context.exit();
        } catch (IllegalStateException ignored) {
        }
    }

}
