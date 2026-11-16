package ua.mama.helper;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_STORAGE = 42;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final Map<String, String> HEAVY = new HashMap<>();

    static {
        HEAVY.put("com.facebook.katana", "Важка програма. Краще поставити «Facebook Lite».");
        HEAVY.put("com.instagram.android", "Важка програма. Є легша версія «Instagram Lite».");
        HEAVY.put("com.zhiliaoapp.musically", "Дуже важка програма. Є «TikTok Lite».");
        HEAVY.put("com.ss.android.ugc.trill", "Дуже важка програма. Є «TikTok Lite».");
        HEAVY.put("com.facebook.orca", "Messenger забирає багато пам'яті у фоні.");
    }

    private LinearLayout content;
    private ScrollView scroll;
    private final Button[] tabs = new Button[4];
    private int currentTab = 0;
    private int renderGen = 0;

    private List<JunkScanner.Category> scanResult = null;
    private boolean scanning = false;
    private final Set<File> selected = new HashSet<>();
    private TextView selectedLabel;

    private static class AppItem {
        String label;
        String pkg;
        String hint;
        Drawable icon;
        long size;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(this, 20));
        scroll.addView(content, new ViewGroup.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(Ui.dp(this, 8));
        String[] labels = {"🏠\nГоловна", "🧹\nОчистка", "📱\nДодатки", "💡\nПоради"};
        for (int i = 0; i < labels.length; i++) {
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setTextSize(13);
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            final int idx = i;
            b.setOnClickListener(v -> showTab(idx));
            nav.addView(b, new LinearLayout.LayoutParams(0, WRAP, 1f));
            tabs[i] = b;
        }
        root.addView(nav, new LinearLayout.LayoutParams(MATCH, WRAP));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentTab == 0 || currentTab == 2 || (currentTab == 1 && scanResult == null)) {
            showTab(currentTab);
        }
    }

    private void showTab(int i) {
        currentTab = i;
        renderGen++;
        for (int k = 0; k < tabs.length; k++) {
            tabs[k].setTextColor(k == i ? Ui.GREEN : Ui.GRAY);
            tabs[k].setTypeface(null, k == i ? Typeface.BOLD : Typeface.NORMAL);
        }
        content.removeAllViews();
        scroll.scrollTo(0, 0);
        switch (i) {
            case 0: showHome(); break;
            case 1: showClean(); break;
            case 2: showApps(); break;
            default: showTips(); break;
        }
    }

    // ---------------------------------------------------------------- home

    private void showHome() {
        content.addView(Ui.header(this, "Мамин Помічник", "Щоб телефон працював швидше 💚"));

        long[] ram = SystemInfo.ram(this);
        long[] st = SystemInfo.storage();
        int[] bat = SystemInfo.battery(this);
        long upDays = SystemClock.elapsedRealtime() / 86_400_000L;
        int ramFree = SystemInfo.pct(ram[1], ram[0]);
        int stFree = SystemInfo.pct(st[1], st[0]);
        int score = SystemInfo.score(ramFree, stFree, upDays);
        int scoreColor = score >= 75 ? Ui.GREEN : (score >= 50 ? Ui.ORANGE : Ui.RED);

        // Health score
        LinearLayout sc = Ui.card(this);
        sc.addView(Ui.center(Ui.title(this, "Стан телефону")));
        sc.addView(Ui.center(Ui.colored(this, score + " / 100", scoreColor, 44)));
        String verdict = score >= 75 ? "Добре! Телефон у порядку 👍"
                : score >= 50 ? "Непогано, але можна краще. Подивіться вкладку «Поради»."
                : "Телефону важко 😟 Почистіть пам'ять і перезавантажте його.";
        sc.addView(Ui.center(Ui.text(this, verdict)));
        content.addView(sc);

        // Boost
        LinearLayout bc = Ui.card(this);
        TextView rocket = Ui.center(Ui.colored(this, "🚀", Ui.DARK, 48));
        bc.addView(rocket);
        Button boost = Ui.button(this, "Прискорити зараз", Ui.GREEN, 20);
        boost.setOnClickListener(v -> doBoost(boost, rocket));
        bc.addView(boost);
        bc.addView(Ui.center(Ui.text(this,
                "Зупиняє програми, які працюють у фоні й забирають пам'ять. Натискайте, коли телефон почав гальмувати.")));
        content.addView(bc);

        // RAM
        int ramUsed = 100 - ramFree;
        LinearLayout rc = Ui.card(this);
        rc.addView(Ui.title(this, "🧠 Оперативна пам'ять"));
        rc.addView(Ui.bar(this, ramUsed, ramUsed > 85 ? Ui.RED : (ramUsed > 70 ? Ui.ORANGE : Ui.BLUE)));
        rc.addView(Ui.colored(this, "Вільно " + Ui.size(ram[1]) + " з " + Ui.size(ram[0]), Ui.DARK, 17));
        rc.addView(Ui.text(this,
                "Це «робочий стіл» телефону. Коли він заповнений — програми відкриваються повільно. "
                        + "У Redmi 9A його небагато, тому не тримайте відкритими багато програм одночасно."));
        content.addView(rc);

        // Storage
        int stUsed = 100 - stFree;
        LinearLayout stc = Ui.card(this);
        stc.addView(Ui.title(this, "💾 Пам'ять для файлів"));
        stc.addView(Ui.bar(this, stUsed, stFree < 10 ? Ui.RED : (stFree < 20 ? Ui.ORANGE : Ui.PURPLE)));
        stc.addView(Ui.colored(this, "Вільно " + Ui.size(st[1]) + " з " + Ui.size(st[0]), Ui.DARK, 17));
        if (stFree < 20) {
            stc.addView(Ui.colored(this,
                    "⚠️ Мало вільного місця! Саме через це телефон гальмує найбільше. Треба, щоб було вільно хоча б 20%.",
                    Ui.RED, 16));
            Button go = Ui.button(this, "🧹 Почистити пам'ять", Ui.ORANGE);
            go.setOnClickListener(v -> showTab(1));
            stc.addView(go);
        } else {
            stc.addView(Ui.text(this, "Тут фото, відео та програми. Тримайте вільними хоча б 20% — тоді телефон працює швидше."));
        }
        content.addView(stc);

        // Battery
        if (bat[0] >= 0) {
            LinearLayout btc = Ui.card(this);
            btc.addView(Ui.title(this, "🔋 Батарея"));
            btc.addView(Ui.colored(this, bat[0] + "%" + (bat[2] == 1 ? "  ⚡ заряджається" : ""),
                    bat[0] <= 15 ? Ui.RED : Ui.GREEN, 26));
            if (bat[1] >= 0) {
                if (bat[1] >= 40) {
                    btc.addView(Ui.colored(this, "🌡 " + bat[1] + " °C — телефон гарячий! Дайте йому відпочити, зніміть чохол. Гарячий телефон гальмує.", Ui.RED, 16));
                } else {
                    btc.addView(Ui.text(this, "🌡 Температура: " + bat[1] + " °C — нормально."));
                }
            }
            content.addView(btc);
        }

        // Uptime
        LinearLayout uc = Ui.card(this);
        uc.addView(Ui.title(this, "🔄 Перезавантаження"));
        if (upDays == 0) {
            uc.addView(Ui.text(this, "Телефон перезавантажували сьогодні. Чудово! 👏"));
        } else {
            uc.addView(Ui.colored(this, "Без перезавантаження: " + upDays + " дн.", upDays >= 3 ? Ui.ORANGE : Ui.DARK, 17));
            if (upDays >= 3) {
                uc.addView(Ui.text(this, "Раз на 2–3 дні варто перезавантажити телефон: затисніть кнопку живлення → «Перезавантажити»."));
            }
        }
        content.addView(uc);
    }

    private void doBoost(Button btn, TextView rocket) {
        btn.setEnabled(false);
        btn.setAlpha(0.6f);
        btn.setText("Прискорюю… ⏳");
        rocket.animate().translationY(-Ui.dp(this, 40)).rotation(-20).setDuration(600)
                .withEndAction(() -> rocket.animate().translationY(0).rotation(0).setDuration(600).start())
                .start();
        final Context app = getApplicationContext();
        new Thread(() -> {
            long[] r = SystemInfo.boost(app);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                String msg;
                if (r[0] > 5L * 1024 * 1024) {
                    msg = "Звільнено " + Ui.size(r[0]) + " оперативної пам'яті.\n\nПеревірено програм: " + r[1] + ".";
                } else {
                    msg = "Фонові програми зупинено (перевірено: " + r[1] + ").\n\nОперативна пам'ять уже була в нормі.";
                }
                msg += "\n\n💡 Якщо телефон усе одно гальмує — перезавантажте його.";
                new AlertDialog.Builder(this)
                        .setTitle("Готово! ✅")
                        .setMessage(msg)
                        .setPositiveButton("Добре", null)
                        .show();
                if (currentTab == 0) showTab(0);
            });
        }).start();
    }

    // ---------------------------------------------------------------- cleaner

    private void showClean() {
        content.addView(Ui.header(this, "Очистка", "Прибираємо сміття з пам'яті 🧹"));

        if (!hasStorageAccess()) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.title(this, "Потрібен дозвіл"));
            c.addView(Ui.text(this, "Щоб знайти непотрібні файли, програмі треба дозволити доступ до пам'яті. "
                    + "Програма нічого не видаляє без вашого підтвердження."));
            Button b = Ui.button(this, "Дозволити доступ", Ui.BLUE);
            b.setOnClickListener(v -> requestStorageAccess());
            c.addView(b);
            content.addView(c);
            return;
        }

        if (scanning) {
            LinearLayout c = Ui.card(this);
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.addView(Ui.center(Ui.title(this, "Шукаю сміття… 🔍")));
            c.addView(new ProgressBar(this));
            c.addView(Ui.center(Ui.text(this, "Це може зайняти до хвилини.")));
            content.addView(c);
            return;
        }

        if (scanResult == null) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.title(this, "Знайти непотрібні файли"));
            c.addView(Ui.text(this, "Програма перевірить: старі файли встановлення, кеш, тимчасові файли, "
                    + "переглянуті статуси WhatsApp, великі відео та старі завантаження."));
            Button b = Ui.button(this, "🔍 Знайти сміття", Ui.GREEN, 20);
            b.setOnClickListener(v -> startScan());
            c.addView(b);
            content.addView(c);
            return;
        }

        renderScanResult();
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageLegacy()) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= 23) {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageLegacy()) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                openSettings(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (hasStorageAccess()) {
                startScan();
            } else {
                Toast.makeText(this, "Без дозволу очистка не працює", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startScan() {
        if (scanning) return;
        scanning = true;
        if (currentTab == 1) showTab(1);
        new Thread(() -> {
            List<JunkScanner.Category> r = JunkScanner.scan();
            runOnUiThread(() -> {
                scanning = false;
                scanResult = r;
                selected.clear();
                for (JunkScanner.Category c : r) {
                    if (c.defaultOn) selected.addAll(c.files);
                }
                if (currentTab == 1) showTab(1);
            });
        }).start();
    }

    private void renderScanResult() {
        long total = 0;
        for (JunkScanner.Category c : scanResult) total += c.size;

        LinearLayout sum = Ui.card(this);
        sum.addView(Ui.center(Ui.title(this, "Знайдено")));
        sum.addView(Ui.center(Ui.colored(this, Ui.size(total), total > 0 ? Ui.ORANGE : Ui.GREEN, 38)));
        selectedLabel = Ui.center(Ui.colored(this, "", Ui.DARK, 17));
        sum.addView(selectedLabel);
        Button del = Ui.button(this, "🗑 Видалити вибране", Ui.RED, 20);
        del.setOnClickListener(v -> confirmDelete());
        sum.addView(del);
        Button again = Ui.button(this, "🔄 Шукати знову", Ui.BLUE);
        again.setOnClickListener(v -> startScan());
        sum.addView(again);
        content.addView(sum);
        updateSelectedLabel();

        boolean any = false;
        for (JunkScanner.Category cat : scanResult) {
            if (cat.files.isEmpty()) continue;
            any = true;
            LinearLayout card = Ui.card(this);

            CheckBox cb = new CheckBox(this);
            cb.setText(cat.name + "  —  " + Ui.size(cat.size));
            cb.setTextSize(18);
            cb.setTextColor(Ui.DARK);
            cb.setTypeface(Typeface.DEFAULT_BOLD);
            cb.setChecked(selected.containsAll(cat.files));
            card.addView(cb);
            card.addView(Ui.text(this, cat.desc + "\nФайлів: " + cat.files.size()));

            final List<CheckBox> children = new ArrayList<>();
            if (cat.perFile) {
                int shown = 0;
                for (File f : cat.files) {
                    if (shown >= 40) {
                        card.addView(Ui.text(this, "…і ще " + (cat.files.size() - 40) + " файлів"));
                        break;
                    }
                    shown++;
                    CheckBox fc = new CheckBox(this);
                    File parent = f.getParentFile();
                    fc.setText(f.getName() + "\n" + Ui.size(f.length()) + (parent != null ? " · папка " + parent.getName() : ""));
                    fc.setTextSize(15);
                    fc.setTextColor(Ui.GRAY);
                    fc.setChecked(selected.contains(f));
                    fc.setOnCheckedChangeListener((view, checked) -> {
                        if (checked) selected.add(f); else selected.remove(f);
                        updateSelectedLabel();
                    });
                    children.add(fc);
                    card.addView(fc);
                }
            }

            cb.setOnCheckedChangeListener((view, checked) -> {
                if (checked) selected.addAll(cat.files); else selected.removeAll(cat.files);
                for (CheckBox fc : children) fc.setChecked(checked);
                updateSelectedLabel();
            });
            content.addView(card);
        }

        if (!any) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.center(Ui.title(this, "Сміття не знайдено 🎉")));
            c.addView(Ui.center(Ui.text(this, "Пам'ять чиста. Якщо місця все одно мало — подивіться вкладку «Додатки», "
                    + "а фото й відео збережіть у Google Фото або на комп'ютер.")));
            content.addView(c);
        }
    }

    private long selectedBytes() {
        long s = 0;
        for (File f : selected) s += f.length();
        return s;
    }

    private void updateSelectedLabel() {
        if (selectedLabel != null) {
            selectedLabel.setText("Вибрано: " + Ui.size(selectedBytes()) + " (" + selected.size() + " файлів)");
        }
    }

    private void confirmDelete() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "Нічого не вибрано", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Видалити файли?")
                .setMessage("Буде видалено " + selected.size() + " файлів (" + Ui.size(selectedBytes()) + ").\n\n"
                        + "Їх не можна буде відновити.")
                .setNegativeButton("Скасувати", null)
                .setPositiveButton("Видалити", (d, w) -> doDelete())
                .show();
    }

    private void doDelete() {
        final List<File> list = new ArrayList<>(selected);
        final AlertDialog progress = new AlertDialog.Builder(this)
                .setMessage("Видаляю… ⏳")
                .setCancelable(false)
                .show();
        new Thread(() -> {
            long freed = 0;
            int count = 0;
            List<String> paths = new ArrayList<>();
            for (File f : list) {
                long len = f.length();
                if (f.delete()) {
                    freed += len;
                    count++;
                    if (paths.size() < 500) paths.add(f.getAbsolutePath());
                }
            }
            final long fr = freed;
            final int cn = count;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                progress.dismiss();
                if (!paths.isEmpty()) {
                    MediaScannerConnection.scanFile(getApplicationContext(),
                            paths.toArray(new String[0]), null, null);
                }
                new AlertDialog.Builder(this)
                        .setTitle("Готово! 🎉")
                        .setMessage("Видалено файлів: " + cn + "\nЗвільнено: " + Ui.size(fr))
                        .setPositiveButton("Чудово", null)
                        .show();
                scanResult = null;
                selected.clear();
                startScan();
            });
        }).start();
    }

    // ---------------------------------------------------------------- apps

    private void showApps() {
        content.addView(Ui.header(this, "Додатки", "Що займає місце 📱"));

        LinearLayout info = Ui.card(this);
        info.addView(Ui.title(this, "Як користуватись"));
        info.addView(Ui.text(this, "Програми впорядковані від найбільшої. Видаліть ті, якими не користуєтесь — "
                + "це найкраще прискорення для телефону.\n\n"
                + "Щоб очистити кеш програми: «Інфо» → внизу «Очистити» → «Очистити кеш».\n"
                + "⚠️ НЕ натискайте «Очистити всі дані» — тоді доведеться знову входити в програму."));
        content.addView(info);

        LinearLayout loading = Ui.card(this);
        loading.setGravity(Gravity.CENTER_HORIZONTAL);
        loading.addView(new ProgressBar(this));
        loading.addView(Ui.center(Ui.text(this, "Завантажую список…")));
        content.addView(loading);

        final int gen = renderGen;
        final Context app = getApplicationContext();
        new Thread(() -> {
            List<AppItem> items = loadApps(app);
            runOnUiThread(() -> {
                if (isFinishing() || gen != renderGen) return;
                content.removeView(loading);
                LinearLayout cnt = Ui.card(this);
                cnt.addView(Ui.center(Ui.colored(this, "Встановлено програм: " + items.size(), Ui.DARK, 18)));
                content.addView(cnt);
                for (AppItem it : items) content.addView(appRow(it));
            });
        }).start();
    }

    private static List<AppItem> loadApps(Context c) {
        PackageManager pm = c.getPackageManager();
        List<AppItem> out = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (ai.packageName.equals(c.getPackageName())) continue;
            AppItem it = new AppItem();
            it.pkg = ai.packageName;
            it.label = String.valueOf(ai.loadLabel(pm));
            try {
                it.icon = ai.loadIcon(pm);
            } catch (Exception e) {
                it.icon = null;
            }
            long s = 0;
            if (ai.sourceDir != null) s += new File(ai.sourceDir).length();
            if (ai.splitSourceDirs != null) {
                for (String p : ai.splitSourceDirs) s += new File(p).length();
            }
            it.size = s;
            it.hint = HEAVY.get(ai.packageName);
            out.add(it);
        }
        Collections.sort(out, (a, b) -> Long.compare(b.size, a.size));
        return out;
    }

    private View appRow(AppItem it) {
        LinearLayout card = Ui.card(this);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(this);
        if (it.icon != null) iv.setImageDrawable(it.icon);
        int s = Ui.dp(this, 48);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(s, s);
        ilp.rightMargin = Ui.dp(this, 14);
        top.addView(iv, ilp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.title(this, it.label));
        col.addView(Ui.text(this, "Розмір програми: " + Ui.size(it.size)));
        top.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
        card.addView(top);

        if (it.hint != null) card.addView(Ui.colored(this, "⚠️ " + it.hint, Ui.ORANGE, 15));

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button info = Ui.button(this, "⚙ Інфо", Ui.BLUE, 15);
        Button del = Ui.button(this, "🗑 Видалити", Ui.RED, 15);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        l1.rightMargin = Ui.dp(this, 6);
        l1.topMargin = Ui.dp(this, 10);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, WRAP, 1f);
        l2.leftMargin = Ui.dp(this, 6);
        l2.topMargin = Ui.dp(this, 10);
        btns.addView(info, l1);
        btns.addView(del, l2);
        card.addView(btns);

        info.setOnClickListener(v -> openAppInfo(it.pkg));
        del.setOnClickListener(v -> uninstall(it.pkg));
        return card;
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        } catch (Exception e) {
            openSettings(Settings.ACTION_APPLICATION_SETTINGS);
        }
    }

    private void uninstall(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
        } catch (Exception e) {
            openAppInfo(pkg);
        }
    }

    // ---------------------------------------------------------------- tips

    private void showTips() {
        content.addView(Ui.header(this, "Поради", "Що реально допомагає 💡"));

        tip("ℹ️ Чесно про цю програму",
                "Жодна програма не зробить зі старого телефону новий: у Redmi 9A лише 2–3 ГБ оперативної пам'яті. "
                        + "Але чиста пам'ять, менше програм і правильні налаштування дають помітну різницю. "
                        + "Ця програма не працює у фоні й сама телефон не гальмує.");

        tip("1️⃣ Перезавантажуйте телефон",
                "Раз на 2–3 дні. Затисніть кнопку живлення → «Перезавантажити». Найпростіший і дуже дієвий спосіб.");

        LinearLayout t2 = tip("2️⃣ Пришвидшіть анімації",
                "Телефон здаватиметься помітно швидшим:\n"
                        + "1) Налаштування → «Про телефон» → 7 разів натисніть «Версія MIUI».\n"
                        + "2) Налаштування → «Додаткові налаштування» → «Для розробників».\n"
                        + "3) Знайдіть «Анімація вікон», «Анімація переходів», «Тривалість анімації» — поставте 0.5x.\n\n"
                        + "Більше нічого там не змінюйте!");
        addBtn(t2, "Відкрити «Про телефон»", Ui.BLUE, () -> openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS));
        addBtn(t2, "Відкрити «Для розробників»", Ui.PURPLE,
                () -> openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));

        LinearLayout t3 = tip("3️⃣ Звільніть місце",
                "Найчастіша причина гальм — заповнена пам'ять. Тримайте вільними хоча б 20%. "
                        + "Відео з WhatsApp, Viber і Telegram — головні «пожирачі» місця. "
                        + "Фото можна зберегти в Google Фото і видалити з телефону.");
        addBtn(t3, "🧹 Перейти до очистки", Ui.GREEN, () -> showTab(1));
        addBtn(t3, "Відкрити «Сховище»", Ui.BLUE, () -> openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));

        LinearLayout t4 = tip("4️⃣ Видаліть зайві програми",
                "Кожна програма може працювати у фоні й забирати пам'ять. Ігри, у які не граєте, "
                        + "зайві магазини, інші «очищувачі» та «антивіруси» — видаляйте.");
        addBtn(t4, "📱 Перейти до списку програм", Ui.GREEN, () -> showTab(2));

        LinearLayout t5 = tip("5️⃣ Обмежте автозапуск",
                "У MIUI програми можуть самі запускатися у фоні. Вимкніть автозапуск для всього, "
                        + "крім месенджерів (Viber, WhatsApp, Telegram), щоб не пропускати повідомлення.");
        addBtn(t5, "Відкрити «Автозапуск»", Ui.BLUE, this::openAutostart);

        LinearLayout t6 = tip("6️⃣ Вимкніть рекламу MIUI",
                "Реклама теж гальмує телефон (назви можуть трохи відрізнятися):\n"
                        + "• Налаштування → «Паролі та безпека» → «Доступ і відкликання» → вимкніть «msa».\n"
                        + "• У програмах «Безпека», «Провідник», «Завантаження», «Музика» → їхні налаштування → "
                        + "вимкніть «Отримувати рекомендації».");
        addBtn(t6, "Відкрити налаштування", Ui.BLUE, () -> openSettings(Settings.ACTION_SETTINGS));

        tip("7️⃣ Легкі версії програм",
                "Замість Facebook — «Facebook Lite», замість Instagram — «Instagram Lite», "
                        + "замість TikTok — «TikTok Lite». Вони працюють набагато швидше на простих телефонах.");

        tip("8️⃣ Прості шпалери, менше віджетів",
                "Живі (рухомі) шпалери та багато віджетів на головному екрані гальмують телефон. "
                        + "Поставте звичайну картинку.");

        tip("9️⃣ Оновлення програм лише по Wi-Fi",
                "Play Маркет → фото профілю → Налаштування → Параметри мережі → Автооновлення програм → "
                        + "«Лише через Wi-Fi». Тоді телефон не оновлюватиме програми в найневдаліший момент.");

        tip("🔟 Якщо нічого не допомагає",
                "Скидання до заводських налаштувань робить телефон майже як новим. АЛЕ спочатку збережіть фото, "
                        + "контакти та листування (резервна копія WhatsApp / Viber)! Краще попросіть когось допомогти.");

        tip("🚫 Чого не робити",
                "Не встановлюйте інші «прискорювачі», «очищувачі» й «охолоджувачі» — вони самі працюють у фоні "
                        + "й гальмують телефон. Не тримайте постійно ввімкненим суворий режим енергозбереження — він знижує швидкість.");
    }

    private LinearLayout tip(String title, String body) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.title(this, title));
        c.addView(Ui.text(this, body));
        content.addView(c);
        return c;
    }

    private void addBtn(LinearLayout parent, String label, int color, Runnable r) {
        Button b = Ui.button(this, label, color, 16);
        b.setOnClickListener(v -> r.run());
        parent.addView(b);
    }

    private void openAutostart() {
        Intent i = new Intent();
        i.setComponent(new ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        try {
            startActivity(i);
        } catch (Exception e) {
            openSettings(Settings.ACTION_APPLICATION_SETTINGS);
        }
    }

    private void openSettings(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, "Не вдалося відкрити налаштування", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
