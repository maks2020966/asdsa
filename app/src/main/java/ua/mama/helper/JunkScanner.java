package ua.mama.helper;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Finds files that are safe (or worth checking) to delete. */
public final class JunkScanner {

    public static final class Category {
        public final String name;
        public final String desc;
        public final boolean defaultOn;
        public final boolean perFile;
        public final List<File> files = new ArrayList<>();
        public long size = 0;

        Category(String name, String desc, boolean defaultOn, boolean perFile) {
            this.name = name;
            this.desc = desc;
            this.defaultOn = defaultOn;
            this.perFile = perFile;
        }

        void add(File f) {
            files.add(f);
            size += f.length();
        }
    }

    private static final long BIG = 100L * 1024 * 1024;
    private static final long OLD_MS = 90L * 24 * 60 * 60 * 1000;

    private Category apk, thumbs, statuses, temp, cache, big, oldDl;
    private String downloadPath = "";
    private long now;

    public static List<Category> scan() {
        return new JunkScanner().run();
    }

    private List<Category> run() {
        apk = new Category("📦 Файли встановлення (APK)",
                "Програми вже встановлені — ці файли більше не потрібні.", true, false);
        thumbs = new Category("🖼 Кеш мініатюр фото",
                "Маленькі копії фото для галереї. Самі фото НЕ видаляються — телефон створить мініатюри знову.", true, false);
        statuses = new Category("💬 Переглянуті статуси WhatsApp",
                "Тимчасові файли статусів друзів, які ви вже бачили.", true, false);
        cache = new Category("🗂 Кеш програм",
                "Тимчасові файли програм. Безпечно видаляти.", true, false);
        temp = new Category("📝 Тимчасові та службові файли",
                "Файли .log і .tmp — програми їх більше не використовують.", true, false);
        big = new Category("🎬 Великі файли (понад 100 МБ)",
                "Зазвичай це відео. Позначте лише те, що точно не потрібно!", false, true);
        oldDl = new Category("📥 Старі завантаження (понад 3 місяці)",
                "Файли з папки «Завантаження», які лежать давно. Перевірте перед видаленням.", false, true);

        now = System.currentTimeMillis();
        File root = Environment.getExternalStorageDirectory();
        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (dl != null) downloadPath = dl.getAbsolutePath() + "/";

        walk(root, 0);
        // WhatsApp and some other apps keep media here on newer Android versions
        walk(new File(root, "Android/media"), 1);

        File[] pkgs = new File(root, "Android/data").listFiles();
        if (pkgs != null) {
            for (File p : pkgs) addAll(new File(p, "cache"), cache, 0);
        }

        sortBySize(big.files);
        sortBySize(oldDl.files);
        return Arrays.asList(apk, thumbs, statuses, cache, temp, big, oldDl);
    }

    private void walk(File dir, int depth) {
        if (depth > 10 || dir == null) return;
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            String lower = f.getName().toLowerCase(Locale.ROOT);
            if (f.isDirectory()) {
                if (depth == 0 && lower.equals("android")) continue;
                if (lower.equals(".thumbnails")) {
                    addAll(f, thumbs, 0);
                    continue;
                }
                if (lower.equals(".statuses")) {
                    addAll(f, statuses, 0);
                    continue;
                }
                walk(f, depth + 1);
            } else {
                classify(f, lower);
            }
        }
    }

    private void classify(File f, String lower) {
        if (lower.endsWith(".apk") || lower.endsWith(".apks") || lower.endsWith(".xapk")) {
            apk.add(f);
            return;
        }
        if (lower.endsWith(".log") || lower.endsWith(".tmp") || lower.endsWith(".temp")) {
            temp.add(f);
            return;
        }
        long len = f.length();
        if (len >= BIG) {
            big.add(f);
            return;
        }
        if (!downloadPath.isEmpty() && f.getAbsolutePath().startsWith(downloadPath)
                && now - f.lastModified() > OLD_MS && len > 0) {
            oldDl.add(f);
        }
    }

    private void addAll(File f, Category c, int depth) {
        if (depth > 12 || !f.exists()) return;
        if (f.isDirectory()) {
            File[] l = f.listFiles();
            if (l != null) for (File x : l) addAll(x, c, depth + 1);
        } else {
            c.add(f);
        }
    }

    private static void sortBySize(List<File> list) {
        Collections.sort(list, (a, b) -> Long.compare(b.length(), a.length()));
    }
}
