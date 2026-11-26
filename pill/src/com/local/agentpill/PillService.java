package com.local.agentpill;

import android.view.animation.DecelerateInterpolator;
import android.view.HapticFeedbackConstants;
import android.animation.ValueAnimator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Animator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileWriter;
import java.util.Locale;

public class PillService extends Service {
    private static final long DONE_TIMEOUT_MS = 5000;
    private static final int DONE_ID = 2;
    private static final int BASE_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    private static final int TAB_DP = 34;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable dismiss = () -> { notifyDone(); stopSelf(); };

    private LinearLayout pill, row;
    private TextView label, detail, take, term;
    private View dot, tabDot;
    private WindowManager.LayoutParams params;
    private SharedPreferences prefs;
    private String current = "";
    private int color = 0xFFFFC94D;
    private int dock;  // 0 free, -1 tucked into the left edge, 1 into the right edge
    private long lastSeq;
    private ValueAnimator glide;
    private TextView timer, tabTimer;
    private String stepKey;  // what is being timed; null when nothing is running
    private long stepStart;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (pill == null || stepKey == null) return;
            long s = (SystemClock.elapsedRealtime() - stepStart) / 1000;
            String t = s < 60 ? s + "s" : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
            timer.setText(t);
            tabTimer.setText(t);
            handler.postDelayed(this, 1000 - (SystemClock.elapsedRealtime() - stepStart) % 1000);
        }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        getExternalFilesDir(null);
        prefs = getSharedPreferences("pill", MODE_PRIVATE);
        dock = prefs.getInt("dock", 0);
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel("pill", "Status pill", NotificationManager.IMPORTANCE_MIN));
        startForeground(1, new Notification.Builder(this, "pill")
                .setSmallIcon(android.R.drawable.presence_online)
                .setContentTitle("Agent Pill").build());
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        String state = i == null ? null : i.getStringExtra("state");
        if (state == null) return START_NOT_STICKY;
        if (state.equals("hide") || state.equals("show")) {  // for screenshots
            if (pill != null) pill.setVisibility(state.equals("hide") ? View.INVISIBLE : View.VISIBLE);
            else stopSelf();
            return START_NOT_STICKY;
        }
        long seq = i.getLongExtra("seq", 0);
        if (seq != 0 && seq < lastSeq) return START_NOT_STICKY;  // late, out-of-order update
        if (seq != 0) lastSeq = seq;
        if (state.equals("off")) { stopSelf(); return START_NOT_STICKY; }
        String text = "";
        String b64 = i.getStringExtra("b64");
        if (b64 != null) {
            try { text = new String(Base64.decode(b64, Base64.DEFAULT), "UTF-8"); }
            catch (Exception ignored) { }
        }
        show(state, text);
        return START_NOT_STICKY;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private File override() { return new File(getExternalFilesDir(null), "override"); }

    private Point screen() {
        Point p = new Point();
        getSystemService(WindowManager.class).getDefaultDisplay().getRealSize(p);
        return p;
    }

    private TextView text(float size, int textColor, boolean bold) {
        TextView t = new TextView(this);
        // dip, not sp: a fixed layout keeps the buttons where the touch lock expects them
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
        t.setTextColor(textColor);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private GradientDrawable round(int fill, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private TextView button(int width) {
        TextView t = text(13, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(width), dp(width));
        lp.leftMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    private void build() {
        // full form: row 1 (dot, status, buttons) + summary; tucked form: only tabDot
        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER_HORIZONTAL);
        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, 0, 0);

        dot = new View(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(10), dp(10));
        dlp.rightMargin = dp(10);
        row.addView(dot, dlp);
        label = text(15, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.rightMargin = dp(4);
        row.addView(label, llp);

        timer = text(13, 0xFFB4B4C0, false);
        timer.setFontFeatureSettings("tnum");
        timer.setGravity(Gravity.END);
        row.addView(timer, new LinearLayout.LayoutParams(dp(38), -2));

        take = button(38);
        term = button(38);
        term.setText(">_");
        term.setContentDescription("Terminal");
        term.setBackground(round(0xFF2C2C37, 20));
        take.setOnClickListener(v -> {
            if (current.equals("taken")) { override().delete(); show("work", "Handed back"); }
            else {
                try { override().createNewFile(); } catch (Exception ignored) { }
                show("taken", "");
            }
        });
        term.setOnClickListener(v -> {
            Intent open = getPackageManager().getLaunchIntentForPackage("sh.haven.app");
            if (open != null) startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        row.addView(take);
        row.addView(term);

        detail = text(13, 0xFFB4B4C0, false);
        detail.setSingleLine(false);
        detail.setMaxLines(2);
        detail.setLineSpacing(dp(2), 1f);
        detail.setPadding(dp(10), dp(6), dp(10), dp(4));

        tabDot = new View(this);
        tabTimer = text(10, 0xFFFFFFFF, false);
        tabTimer.setFontFeatureSettings("tnum");
        tabTimer.setPadding(0, dp(4), 0, 0);

        pill.addView(row, new LinearLayout.LayoutParams(-2, dp(40)));
        pill.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        pill.addView(tabDot, new LinearLayout.LayoutParams(dp(14), dp(14)));
        pill.addView(tabTimer, new LinearLayout.LayoutParams(-2, -2));
        pill.setOnTouchListener(drag);

        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                BASE_FLAGS, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = prefs.getInt("y", dp(4));
        try { getSystemService(WindowManager.class).addView(pill, params); }
        catch (Exception e) { pill = null; }
    }

    /** Tucked only when docked and not locked: while locked the buttons must stay reachable. */
    private boolean tucked() { return dock != 0 && !current.equals("touch"); }

    private void applyLayout() {
        boolean tab = tucked();
        row.setVisibility(tab ? View.GONE : View.VISIBLE);
        detail.setVisibility(tab || detail.length() == 0 ? View.GONE : View.VISIBLE);
        tabDot.setVisibility(tab ? View.VISIBLE : View.GONE);
        timer.setVisibility(stepKey == null ? View.GONE : View.VISIBLE);
        tabTimer.setVisibility(tab && stepKey != null ? View.VISIBLE : View.GONE);
        GradientDrawable bg = round(0xFF16161C, tab ? 17 : 26);
        bg.setStroke(dp(1), (color & 0x00FFFFFF) | 0x99000000);
        pill.setBackground(bg);
        int pad = dp(tab ? 0 : 6);
        pill.setPadding(pad, tab ? dp(12) : pad, pad, tab ? dp(12) : pad);
        if (tab) {
            params.width = dp(TAB_DP);
        } else {  // the pill is exactly as wide as row 1; the summary wraps inside that width
            int free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            row.measure(free, View.MeasureSpec.makeMeasureSpec(dp(40), View.MeasureSpec.EXACTLY));
            detail.getLayoutParams().width = row.getMeasuredWidth();
            detail.requestLayout();
            params.width = row.getMeasuredWidth() + 2 * pad;
        }
        int half = (screen().x - params.width) / 2;
        params.x = tab ? dock * half : Math.max(-half, Math.min(half, prefs.getInt("x", 0)));
        getSystemService(WindowManager.class).updateViewLayout(pill, params);
        pill.postDelayed(this::saveRects, 200);
    }

    /**
     * Drag any part that is not a button. The pill follows the finger, even off the side of the screen.
     * Released with more than a third of it outside: tuck into that edge. Otherwise it glides back
     * fully on screen and stays open. A tucked tab opens on tap or when pulled inwards.
     */
    private final View.OnTouchListener drag = new View.OnTouchListener() {
        private float downX, downY;
        private int startX, startY;
        private boolean moved, armed;

        @Override public boolean onTouch(View v, MotionEvent e) {
            float rx = e.getRawX(), ry = e.getRawY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    stopGlide();
                    downX = rx; downY = ry;
                    startX = params.x; startY = params.y;
                    moved = armed = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = rx - downX, dy = ry - downY;
                    if (!moved && Math.hypot(dx, dy) < ViewConfiguration.get(PillService.this).getScaledTouchSlop())
                        return true;
                    moved = true;
                    if (tucked()) {
                        if (-dock * dx > dp(28)) {  // pulled out: open under the finger and keep dragging
                            dock = 0;
                            int x = Math.round(rx - screen().x / 2f);
                            prefs.edit().putInt("x", x).putInt("y", params.y).putInt("dock", 0).apply();
                            applyLayout();
                            downX = rx; downY = ry; startX = params.x; startY = params.y;
                        } else {
                            moveTo(params.x, startY + Math.round(dy));
                        }
                        return true;
                    }
                    moveTo(startX + Math.round(dx), startY + Math.round(dy));
                    boolean now = overshoot() > params.width / 3;
                    if (now != armed) {  // tell the finger that letting go will tuck (or no longer will)
                        armed = now;
                        pill.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        pill.setAlpha(now ? 0.5f : 1f);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    release(!moved && e.getActionMasked() == MotionEvent.ACTION_UP,
                            armed && e.getActionMasked() == MotionEvent.ACTION_UP);
                    return true;
                default:
                    return false;
            }
        }

        private void release(boolean tap, boolean tuck) {
            pill.setAlpha(1f);
            prefs.edit().putInt("y", params.y).apply();
            if (tucked()) {
                if (tap) {  // open flush against the edge it was tucked into
                    int side = dock;
                    dock = 0;
                    prefs.edit().putInt("x", side * screen().x).putInt("dock", 0).apply();
                    applyLayout();
                }
                return;
            }
            if (!moved) return;
            int side = params.x < 0 ? -1 : 1;
            int half = (screen().x - params.width) / 2;
            int rest = Math.max(-half, Math.min(half, params.x));
            if (tuck) {
                prefs.edit().putInt("x", rest).apply();
                glide(side * (half + params.width / 2), () -> {
                    dock = side;
                    prefs.edit().putInt("dock", side).apply();
                    applyLayout();
                });
            } else {
                prefs.edit().putInt("x", rest).apply();
                glide(rest, PillService.this::applyLayout);
            }
        }
    };

    /** How many pixels of the pill are beyond a side edge. */
    private int overshoot() {
        return Math.max(0, Math.abs(params.x) - (screen().x - params.width) / 2);
    }

    private void stopGlide() {
        if (glide == null) return;
        glide.removeAllListeners();
        glide.cancel();
        glide = null;
    }

    private void glide(int toX, Runnable then) {
        stopGlide();
        glide = ValueAnimator.ofInt(params.x, toX);
        glide.setDuration(180);
        glide.setInterpolator(new DecelerateInterpolator());
        glide.addUpdateListener(an -> {
            if (pill == null) return;
            params.x = (int) an.getAnimatedValue();
            getSystemService(WindowManager.class).updateViewLayout(pill, params);
        });
        glide.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator an) { if (pill != null) then.run(); }
        });
        glide.start();
    }

    private void moveTo(int x, int y) {
        Point s = screen();
        params.x = x;  // may go past the sides while dragging
        params.y = Math.max(0, Math.min(Math.max(0, s.y - pill.getHeight() - dp(48)), y));
        getSystemService(WindowManager.class).updateViewLayout(pill, params);
    }

    /** Tell the touch lock where the buttons are: "W H  take x1 y1 x2 y2  terminal x1 y1 x2 y2". */
    private void saveRects() {
        if (pill == null) return;
        String out = "0 0 0 0 0 0 0 0 0 0";  // tucked: no buttons
        if (!tucked() && term.isLaidOut()) {
            Point s = screen();
            StringBuilder b = new StringBuilder(s.x + " " + s.y);
            for (View v : new View[] {take, term}) {
                if (v.getVisibility() != View.VISIBLE) { b.append(" 0 0 0 0"); continue; }
                int[] at = new int[2];
                v.getLocationOnScreen(at);
                b.append(" " + at[0] + " " + at[1] + " " + (at[0] + v.getWidth()) + " " + (at[1] + v.getHeight()));
            }
            out = b.toString();
        }
        try (FileWriter w = new FileWriter(new File(getExternalFilesDir(null), "rects"))) {
            w.write(out);
        } catch (Exception ignored) { }
    }

    private void show(String state, String text) {
        String title;
        String rawText = text;
        switch (state) {
            case "touch": color = 0xFFFF5C6C; title = "Touch locked"; if (text.isEmpty()) text = "Vol key unlocks"; break;
            case "taken": color = 0xFFC08BFF; title = "Taken over"; text = "Agent is blocked"; break;
            case "wait":  color = 0xFF5AA9FF; title = "Your turn"; break;
            case "done":  color = 0xFF5FD38A; title = "Done"; break;
            case "idle":  color = 0xFF8E8E9A; title = "Idle"; text = ""; break;
            default:      color = 0xFFFFC94D; title = "Working"; break;
        }
        current = state;
        stopGlide();
        if (pill != null) pill.setAlpha(1f);
        if (!state.equals("done")) getSystemService(NotificationManager.class).cancel(DONE_ID);
        handler.removeCallbacks(dismiss);  // any newer state cancels a pending dismissal
        if (state.equals("done")) handler.postDelayed(dismiss, DONE_TIMEOUT_MS);
        if (pill == null) build();
        if (pill == null) return;
        // time the current step: restart when its description changes, keep running across lock/unlock
        String key = state.equals("work") || state.equals("touch") ? String.valueOf(rawText) : null;
        if (key == null || !key.equals(stepKey)) stepStart = SystemClock.elapsedRealtime();
        stepKey = key;
        handler.removeCallbacks(tick);
        tick.run();
        label.setText(title);
        detail.setText(text);
        dot.setBackground(round(color, 5));
        tabDot.setBackground(round(color, 7));
        // pause = take over, play = hand back
        take.setText(state.equals("taken") ? "\u25B6\uFE0E" : "\u275A\u275A");
        take.setContentDescription(state.equals("taken") ? "Hand back" : "Take over");
        take.setTextColor(0xFF101014);
        take.setBackground(round(color, 20));
        take.setVisibility(state.equals("wait") || state.equals("done") || state.equals("idle") ? View.GONE : View.VISIBLE);
        // While locked the lock tool reads the buttons itself, so the window lets injected taps through
        // (needs alpha <= 0.8 on Android 12+). Otherwise it is an ordinary touchable window.
        boolean locked = state.equals("touch");
        params.flags = BASE_FLAGS
                | (locked ? WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE : 0);
        params.alpha = locked ? 0.8f : 0.96f;
        applyLayout();
    }

    /** The notification left behind once the "Done" pill goes away. */
    private void notifyDone() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(
                new NotificationChannel("done", "Task finished", NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder b = new Notification.Builder(this, "done")
                .setSmallIcon(android.R.drawable.checkbox_on_background)
                .setColor(0xFF5FD38A)
                .setContentTitle("Agent is done")
                .setContentText("Tap to open the terminal")
                .setShowWhen(true)
                .setAutoCancel(true);
        Intent open = getPackageManager().getLaunchIntentForPackage("sh.haven.app");
        if (open != null) b.setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE));
        nm.notify(DONE_ID, b.build());
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopGlide();
        if (pill != null) getSystemService(WindowManager.class).removeView(pill);
        pill = null;
        super.onDestroy();
    }
}
