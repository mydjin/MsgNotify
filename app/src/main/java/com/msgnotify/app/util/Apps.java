package com.msgnotify.app.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 已安装应用查询与缓存
 */
public final class Apps {

    public static class Item {
        public String pkg;
        public String name;
        public Drawable icon;
    }

    private static final Map<String, String> NAME_CACHE = new HashMap<>();
    private static final Map<String, Drawable> ICON_CACHE = new HashMap<>();

    private Apps() {
    }

    public static String appName(Context c, String pkg) {
        if (pkg == null) return "";
        String cached = NAME_CACHE.get(pkg);
        if (cached != null) return cached;
        String name = pkg;
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            name = String.valueOf(pm.getApplicationLabel(ai));
        } catch (Throwable ignored) {
        }
        NAME_CACHE.put(pkg, name);
        return name;
    }

    public static Drawable icon(Context c, String pkg) {
        if (pkg == null) return null;
        Drawable cached = ICON_CACHE.get(pkg);
        if (cached != null) return cached;
        Drawable d = null;
        try {
            d = c.getPackageManager().getApplicationIcon(pkg);
        } catch (Throwable ignored) {
        }
        if (d != null) ICON_CACHE.put(pkg, d);
        return d;
    }

    /** 可启动的应用列表（按名称排序，已选中的排前面） */
    public static List<Item> launchable(Context c, java.util.Set<String> selected) {
        PackageManager pm = c.getPackageManager();
        List<Item> list = new ArrayList<>();

        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);

        java.util.Set<String> seen = new java.util.HashSet<>();
        String self = c.getPackageName();
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg == null || self.equals(pkg) || !seen.add(pkg)) continue;
            Item it = new Item();
            it.pkg = pkg;
            it.name = String.valueOf(ri.loadLabel(pm));
            list.add(it);
            NAME_CACHE.put(pkg, it.name);
        }

        final java.util.Set<String> sel = selected == null
                ? Collections.<String>emptySet() : selected;
        Collections.sort(list, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                boolean sa = sel.contains(a.pkg);
                boolean sb = sel.contains(b.pkg);
                if (sa != sb) return sa ? -1 : 1;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return list;
    }
}
