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

package me.xingrz.prox.logging;

import android.util.Log;

import me.xingrz.prox.BuildConfig;

public class FormattingLoggers {

    public static FormattingLogger getContextLogger() {
        return getLogger(StackTraceUtils.getCallerClassName(new Throwable()));
    }

    public static FormattingLogger getContextLogger(String subName) {
        return getLogger(StackTraceUtils.getCallerClassName(new Throwable()) + "/" + subName);
    }

    public static FormattingLogger getLogger(String tag) {
        return BuildConfig.DEBUG
                ? getDebugLogger(tag)
                : getReleaseLogger(tag);
    }

    private static FormattingLogger getDebugLogger(final String tag) {
        return new FormattingLogger() {
            @Override
            public void v(String msg, Object... args) {
                Log.v(tag, String.format(msg, args));
            }

            @Override
            public void v(Throwable tr, String msg, Object... args) {
                Log.v(tag, String.format(msg, args), tr);
            }

            @Override
            public void d(String msg, Object... args) {
                Log.d(tag, String.format(msg, args));
            }

            @Override
            public void d(Throwable tr, String msg, Object... args) {
                Log.d(tag, String.format(msg, args), tr);
            }

            @Override
            public void i(String msg, Object... args) {
                Log.i(tag, String.format(msg, args));
            }

            @Override
            public void i(Throwable tr, String msg, Object... args) {
                Log.i(tag, String.format(msg, args), tr);
            }

            @Override
            public void w(String msg, Object... args) {
                Log.w(tag, String.format(msg, args));
            }

            @Override
            public void w(Throwable tr, String msg, Object... args) {
                Log.w(tag, String.format(msg, args), tr);
            }

            @Override
            public void e(String msg, Object... args) {
                Log.e(tag, String.format(msg, args));
            }

            @Override
            public void e(Throwable tr, String msg, Object... args) {
                Log.e(tag, String.format(msg, args), tr);
            }

            @Override
            public String getTag() {
                return tag;
            }
        };
    }

    private static FormattingLogger getReleaseLogger(final String tag) {
        return new FormattingLogger() {
            @Override
            public void v(String msg, Object... args) {
            }

            @Override
            public void v(Throwable tr, String msg, Object... args) {
            }

            @Override
            public void d(String msg, Object... args) {
            }

            @Override
            public void d(Throwable tr, String msg, Object... args) {
            }

            @Override
            public void i(String msg, Object... args) {
                Log.i(tag, String.format(msg, args));
            }

            @Override
            public void i(Throwable tr, String msg, Object... args) {
                Log.i(tag, String.format(msg, args), tr);
            }

            @Override
            public void w(String msg, Object... args) {
                Log.w(tag, String.format(msg, args));
            }

            @Override
            public void w(Throwable tr, String msg, Object... args) {
                Log.w(tag, String.format(msg, args), tr);
            }

            @Override
            public void e(String msg, Object... args) {
                Log.e(tag, String.format(msg, args));
            }

            @Override
            public void e(Throwable tr, String msg, Object... args) {
                Log.e(tag, String.format(msg, args), tr);
            }

            @Override
            public String getTag() {
                return tag;
            }
        };
    }

}
