package ua.mama.helper;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;

/** Small helpers for building a big, simple, friendly UI in code. */
public final class Ui {
    public static final int GREEN = 0xFF16A34A;
    public static final int BLUE = 0xFF2563EB;
    public static final int ORANGE = 0xFFEA580C;
    public static final int RED = 0xFFDC2626;
    public static final int PURPLE = 0xFF7C3AED;
    public static final int BG = 0xFFF1F5F9;
    public static final int DARK = 0xFF0F172A;
    public static final int GRAY = 0xFF475569;

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    public static LinearLayout header(Context c, String title, String subtitle) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{GREEN, 0xFF0EA5E9});
        float r = dp(c, 28);
        g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        box.setBackground(g);
        box.setPadding(dp(c, 22), dp(c, 24), dp(c, 22), dp(c, 26));

        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(28);
        t.setTypeface(Typeface.DEFAULT_BOLD);

        TextView s = new TextView(c);
        s.setText(subtitle);
        s.setTextColor(0xE6FFFFFF);
        s.setTextSize(17);

        box.addView(t);
        box.addView(s);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.bottomMargin = dp(c, 8);
        box.setLayoutParams(lp);
        return box;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(rounded(Color.WHITE, dp(c, 20)));
        int p = dp(c, 18);
        l.setPadding(p, p, p, p);
        l.setElevation(dp(c, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView title(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(20);
        t.setTextColor(DARK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView text(Context c, CharSequence s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(16);
        t.setTextColor(GRAY);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(0, dp(c, 6), 0, 0);
        return t;
    }

    public static TextView colored(Context c, String s, int color, float size) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(c, 4), 0, 0);
        return t;
    }

    public static TextView center(TextView t) {
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    public static Button button(Context c, String s, int color) {
        return button(c, s, color, 18);
    }

    public static Button button(Context c, String s, int color, float size) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(size);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(rounded(color, dp(c, 16)));
        b.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(c, 12);
        b.setLayoutParams(lp);
        return b;
    }

    public static ProgressBar bar(Context c, int percent, int color) {
        ProgressBar p = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        p.setMax(100);
        p.setProgress(Math.max(0, Math.min(100, percent)));
        p.setProgressTintList(ColorStateList.valueOf(color));
        p.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFCBD5E1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(c, 16));
        lp.topMargin = dp(c, 10);
        lp.bottomMargin = dp(c, 4);
        p.setLayoutParams(lp);
        return p;
    }

    public static String size(long bytes) {
        if (bytes < 0) bytes = 0;
        double v = bytes;
        double gb = 1024d * 1024 * 1024;
        double mb = 1024d * 1024;
        if (v >= gb) return String.format(Locale.US, "%.1f ГБ", v / gb);
        if (v >= mb) return String.format(Locale.US, "%.0f МБ", v / mb);
        if (v >= 1024) return String.format(Locale.US, "%.0f КБ", v / 1024d);
        return bytes + " Б";
    }
}
