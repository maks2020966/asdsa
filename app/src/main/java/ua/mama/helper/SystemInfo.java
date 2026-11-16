package ua.mama.helper;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;

/** Reads phone state (RAM, storage, battery) and performs the "boost". */
public final class SystemInfo {
    private SystemInfo() {}

    /** @return {total, available} RAM in bytes */
    public static long[] ram(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        if (am != null) am.getMemoryInfo(mi);
        return new long[]{Math.max(1, mi.totalMem), mi.availMem};
    }

    /** @return {total, available} internal storage in bytes */
    public static long[] storage() {
        try {
            StatFs s = new StatFs(Environment.getDataDirectory().getPath());
            return new long[]{Math.max(1, s.getTotalBytes()), s.getAvailableBytes()};
        } catch (Exception e) {
            return new long[]{1, 0};
        }
    }

    /** @return {percent, temperatureC, charging(1/0)}; -1 when unknown */
    public static int[] battery(Context c) {
        Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return new int[]{-1, -1, 0};
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        int pct = (level >= 0 && scale > 0) ? level * 100 / scale : -1;
        return new int[]{pct, temp >= 0 ? temp / 10 : -1, charging ? 1 : 0};
    }

    public static int pct(long part, long total) {
        return (int) Math.round(part * 100.0 / Math.max(1, total));
    }

    /** Simple 0..100 "health" score shown on the home screen. */
    public static int score(int ramFreePct, int storageFreePct, long upDays) {
        int s = 100;
        if (ramFreePct < 15) s -= 25; else if (ramFreePct < 25) s -= 15; else if (ramFreePct < 35) s -= 5;
        if (storageFreePct < 10) s -= 40; else if (storageFreePct < 20) s -= 25; else if (storageFreePct < 30) s -= 10;
        if (upDays >= 7) s -= 15; else if (upDays >= 3) s -= 8;
        return Math.max(5, s);
    }

    /**
     * Stops background processes of user apps and clears this app's own cache.
     * @return {freedRamBytes, checkedAppsCount}
     */
    public static long[] boost(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        long before = ram(c)[1];
        int checked = 0;
        if (am != null) {
            PackageManager pm = c.getPackageManager();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if (ai.packageName.equals(c.getPackageName())) continue;
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                try {
                    am.killBackgroundProcesses(ai.packageName);
                    checked++;
                } catch (Exception ignored) {
                }
            }
        }
        deleteContents(c.getCacheDir());
        File ext = c.getExternalCacheDir();
        if (ext != null) deleteContents(ext);
        System.gc();
        try {
            Thread.sleep(1800);
        } catch (InterruptedException ignored) {
        }
        long after = ram(c)[1];
        return new long[]{after - before, checked};
    }

    private static void deleteContents(File dir) {
        if (dir == null) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) deleteContents(f);
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
