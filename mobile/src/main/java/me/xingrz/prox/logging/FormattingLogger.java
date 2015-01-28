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

public interface FormattingLogger {

    public void v(String msg, Object... args);

    public void v(Throwable tr, String msg, Object... args);

    public void d(String msg, Object... args);

    public void d(Throwable tr, String msg, Object... args);

    public void i(String msg, Object... args);

    public void i(Throwable tr, String msg, Object... args);

    public void w(String msg, Object... args);

    public void w(Throwable tr, String msg, Object... args);

    public void e(String msg, Object... args);

    public void e(Throwable tr, String msg, Object... args);

}
