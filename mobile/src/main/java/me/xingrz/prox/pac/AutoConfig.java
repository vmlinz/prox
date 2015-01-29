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

import android.util.LruCache;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;

import me.xingrz.prox.logging.FormattingLogger;
import me.xingrz.prox.logging.FormattingLoggers;

/**
 * PAC 解析器
 *
 * @author XiNGRZ
 */
class AutoConfig {

    private static final FormattingLogger logger = FormattingLoggers.getContextLogger();

    /**
     * 从 {@code url} 抓取 PAC 内容并返回新实例
     *
     * @param url PAC 地址
     * @return 新的 {@code ProxAutoConfig} 实例
     * @throws IOException
     */
    public static AutoConfig fromUrl(String url) throws IOException {
        HttpResponse response = new DefaultHttpClient().execute(new HttpGet(url));
        String config = EntityUtils.toString(response.getEntity());
        return new AutoConfig(config);
    }

    private static final String PROXY_PREFIX = "PROXY ";
    private static final String PAC_FUNCTION = "FindProxyForURL";

    private final LruCache<String, String> cache = new LruCache<>(200);

    private final Context rhino;
    private final Scriptable scope;
    private final Function handler;

    /**
     * 以 PAC 内容创建新实例
     *
     * @param config PAC 内容
     */
    public AutoConfig(String config) {
        rhino = Context.enter();
        rhino.setOptimizationLevel(-1);

        scope = rhino.initStandardObjects();
        rhino.evaluateString(scope, config, "pac", 1, null);

        handler = (Function) scope.get(PAC_FUNCTION, scope);
    }

    /**
     * 查找指定主机的代理地址
     *
     * @param host 主机名
     * @return 代理服务器地址，或 {@code null} 表示直连
     */
    public String findProxyForHost(String host) {
        String cached = cache.get(host);
        if (cached != null) {
            return parse(cached);
        }

        if (handler == null) {
            logger.w("no handler function found");
            return null;
        }

        Object result = handler.call(rhino, scope, scope, new Object[]{null, host});

        if (result == null) {
            logger.w("null result");
            return null;
        }

        if (!(result instanceof String)) {
            logger.w("result not String");
            return null;
        }

        String config = (String) result;
        cache.put(host, config);
        return parse(config);
    }

    private String parse(String config) {
        if (config.startsWith(PROXY_PREFIX)) {
            return config.split(";")[0].substring(PROXY_PREFIX.length());
        } else {
            return null;
        }
    }

    /**
     * 释放对象
     */
    public void destroy() {
        try {
            Context.exit();
        } catch (IllegalStateException ignored) {
        }

        cache.evictAll();
    }

}
