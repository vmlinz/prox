package me.xingrz.prox.tcp;

import android.util.LruCache;

import java.net.InetAddress;

import me.xingrz.prox.internet.IpUtils;

public class Blacklist {

    private static final LruCache<Integer, Boolean> blacklist = new LruCache<>(200);

    public static void countUp(InetAddress address) {
        blacklist.put(IpUtils.toInteger(address) & 0xFFFFFF00, true);
    }

    public static boolean contains(InetAddress address) {
        return blacklist.get(IpUtils.toInteger(address) & 0xFFFFFF00) != null;
    }

}
