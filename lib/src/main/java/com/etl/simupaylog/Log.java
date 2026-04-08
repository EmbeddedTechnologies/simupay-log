package com.etl.simupaylog;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drop-in replacement for {@link android.util.Log} that forwards log entries
 * to the SimuPay web dashboard in real time.
 *
 * <h3>Usage</h3>
 * Replace:
 * <pre>import android.util.Log;</pre>
 * with:
 * <pre>import com.etl.simupaylog.Log;</pre>
 * All existing {@code Log.i}, {@code Log.d}, {@code Log.e} etc. calls work unchanged.
 *
 * <h3>Configuration</h3>
 * Call once at app startup (e.g. in {@code Application.onCreate}):
 * <pre>
 *   Log.setSimulatorUrl("https://simupay.co.uk/api/log");
 *   Log.setTerminalId(myTid);   // included in every log entry for dashboard filtering
 * </pre>
 * Set URL to {@code null} (the default) to disable remote forwarding.
 */
public final class Log {

    private static final String SELF_TAG = "SimuLog";

    /** URL of the simulator POST /api/log endpoint. Null = remote forwarding disabled. */
    private static volatile String sSimulatorUrl = "https://simupay.co.uk/api/log";

    /** Terminal ID appended to every remote log entry. */
    private static volatile String sTerminalId = null;

    /** Background thread for non-blocking HTTP posts. */
    private static final ExecutorService sExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "SimuLog");
                t.setDaemon(true);
                return t;
            });

    private Log() {}

    // ── Configuration ───────────────────────────────────────────────────────

    /**
     * Set the SimuPay log endpoint URL.
     *
     * @param url full URL e.g. {@code "https://simupay.co.uk/api/log"},
     *            or {@code null} to disable remote forwarding.
     */
    public static void setSimulatorUrl(String url) {
        sSimulatorUrl = url;
    }

    public static String getSimulatorUrl() {
        return sSimulatorUrl;
    }

    /**
     * Set the terminal ID included in every remote log entry so the
     * SimuPay dashboard can filter logs by terminal.
     *
     * @param tid terminal ID string (e.g. {@code "12341234"})
     */
    public static void setTerminalId(String tid) {
        sTerminalId = tid;
    }

    public static String getTerminalId() {
        return sTerminalId;
    }

    // ── Log methods (mirror android.util.Log) ───────────────────────────────

    public static int v(String tag, String msg) {
        int result = android.util.Log.v(tag, msg);
        post("V", tag, msg);
        return result;
    }

    public static int v(String tag, String msg, Throwable tr) {
        int result = android.util.Log.v(tag, msg, tr);
        post("V", tag, msg + "\n" + android.util.Log.getStackTraceString(tr));
        return result;
    }

    public static int d(String tag, String msg) {
        int result = android.util.Log.d(tag, msg);
        post("D", tag, msg);
        return result;
    }

    public static int d(String tag, String msg, Throwable tr) {
        int result = android.util.Log.d(tag, msg, tr);
        post("D", tag, msg + "\n" + android.util.Log.getStackTraceString(tr));
        return result;
    }

    public static int i(String tag, String msg) {
        int result = android.util.Log.i(tag, msg);
        post("I", tag, msg);
        return result;
    }

    public static int i(String tag, String msg, Throwable tr) {
        int result = android.util.Log.i(tag, msg, tr);
        post("I", tag, msg + "\n" + android.util.Log.getStackTraceString(tr));
        return result;
    }

    public static int w(String tag, String msg) {
        int result = android.util.Log.w(tag, msg);
        post("W", tag, msg);
        return result;
    }

    public static int w(String tag, String msg, Throwable tr) {
        int result = android.util.Log.w(tag, msg, tr);
        post("W", tag, msg + "\n" + android.util.Log.getStackTraceString(tr));
        return result;
    }

    public static int w(String tag, Throwable tr) {
        int result = android.util.Log.w(tag, tr);
        post("W", tag, android.util.Log.getStackTraceString(tr));
        return result;
    }

    public static int e(String tag, String msg) {
        int result = android.util.Log.e(tag, msg);
        post("E", tag, msg);
        return result;
    }

    public static int e(String tag, String msg, Throwable tr) {
        int result = android.util.Log.e(tag, msg, tr);
        post("E", tag, msg + "\n" + android.util.Log.getStackTraceString(tr));
        return result;
    }

    // ── Image logging ────────────────────────────────────────────────────────

    /**
     * Send a bitmap to the SimuPay dashboard as an image log entry.
     * The bitmap is JPEG-compressed (quality 80) and base64-encoded before posting.
     *
     * @param tag     log tag
     * @param message caption shown alongside the image in the dashboard
     * @param bitmap  the image to send; if null, a plain text log is posted instead
     */
    public static void img(String tag, String message, Bitmap bitmap) {
        android.util.Log.i(tag, message != null ? message : "[image]");
        if (bitmap != null) {
            postImage(tag, message != null ? message : "", bitmap);
        } else {
            post("I", tag, message != null ? message : "[image — null bitmap]");
        }
    }

    /** Overload with no caption message. */
    public static void img(String tag, Bitmap bitmap) {
        img(tag, "", bitmap);
    }

    // ── Screenshot logging ──────────────────────────────────────────────────

    /**
     * Capture a screenshot of the given Activity and send it to the SimuPay
     * dashboard. Uses {@link PixelCopy} (API 26+) for hardware-accelerated
     * capture with a fallback to {@link View#draw(Canvas)} for software rendering.
     *
     * <p>Must be called from the <strong>UI thread</strong> or with a valid
     * Activity whose window is visible.</p>
     *
     * @param tag      log tag
     * @param message  caption shown alongside the screenshot in the dashboard
     * @param activity the activity to capture; must not be null or finishing
     */
    public static void screen(String tag, String message, Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            post("W", tag, "[screen — activity unavailable]");
            return;
        }
        android.util.Log.i(tag, message != null ? message : "[screenshot]");
        captureScreen(tag, message != null ? message : "", activity);
    }

    /** Overload with no caption message. */
    public static void screen(String tag, Activity activity) {
        screen(tag, "", activity);
    }

    /**
     * Capture a screenshot of a specific View and send it to the dashboard.
     *
     * @param tag     log tag
     * @param message caption
     * @param view    the view to capture
     */
    public static void screen(String tag, String message, View view) {
        if (view == null || view.getWidth() == 0 || view.getHeight() == 0) {
            post("W", tag, "[screen — view unavailable or zero-sized]");
            return;
        }
        android.util.Log.i(tag, message != null ? message : "[screenshot]");
        Bitmap bmp = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        view.draw(canvas);
        postImage(tag, message != null ? message : "", bmp);
    }

    public static String getStackTraceString(Throwable tr) {
        return android.util.Log.getStackTraceString(tr);
    }

    public static boolean isLoggable(String tag, int level) {
        return android.util.Log.isLoggable(tag, level);
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private static void captureScreen(String tag, String message, Activity activity) {
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        int w = decorView.getWidth();
        int h = decorView.getHeight();
        if (w == 0 || h == 0) {
            post("W", tag, "[screen — window has zero size]");
            return;
        }

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);

        try {
            // PixelCopy works with hardware-accelerated windows (API 26+)
            Handler handler = new Handler(Looper.getMainLooper());
            PixelCopy.request(window, bmp, (result) -> {
                if (result == PixelCopy.SUCCESS) {
                    postImage(tag, message, bmp);
                } else {
                    // Fallback to canvas draw
                    android.util.Log.w(SELF_TAG, "PixelCopy failed (code " + result + "), falling back to canvas draw");
                    fallbackCapture(tag, message, decorView, bmp);
                }
            }, handler);
        } catch (Exception e) {
            // PixelCopy unavailable or failed — fallback
            android.util.Log.w(SELF_TAG, "PixelCopy exception, falling back to canvas draw: " + e.getMessage());
            fallbackCapture(tag, message, decorView, bmp);
        }
    }

    private static void fallbackCapture(String tag, String message, View view, Bitmap bmp) {
        try {
            Canvas canvas = new Canvas(bmp);
            view.draw(canvas);
            postImage(tag, message, bmp);
        } catch (Exception e) {
            android.util.Log.e(SELF_TAG, "Screenshot fallback failed: " + e.getMessage());
            post("E", tag, "[screen — capture failed: " + e.getMessage() + "]");
        }
    }

    private static void postImage(String tag, String message, Bitmap bitmap) {
        final String url = sSimulatorUrl;
        if (url == null || url.isEmpty()) return;

        final String tid = sTerminalId;

        sExecutor.execute(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                JSONObject body = new JSONObject();
                body.put("level",       "I");
                body.put("tag",         tag     != null ? tag     : "");
                body.put("message",     message != null ? message : "");
                body.put("image",       b64);
                body.put("timestamp",   Instant.now().toString());
                if (tid != null && !tid.isEmpty()) body.put("terminal_id", tid);

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Content-Length", String.valueOf(payload.length));
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                conn.getResponseCode();
                conn.disconnect();

            } catch (Exception ignored) {
                android.util.Log.e(SELF_TAG, "Image logging failure: " + ignored.getMessage());
            }
        });
    }

    private static void post(String level, String tag, String message) {
        final String url = sSimulatorUrl;
        if (url == null || url.isEmpty()) return;

        final String tid = sTerminalId;

        sExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("level",     level);
                body.put("tag",       tag     != null ? tag     : "");
                body.put("message",   message != null ? message : "");
                body.put("timestamp", Instant.now().toString());
                if (tid != null && !tid.isEmpty()) {
                    body.put("terminal_id", tid);
                }

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Content-Length", String.valueOf(payload.length));
                conn.setDoOutput(true);
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                conn.getResponseCode();
                conn.disconnect();

            } catch (Exception ignored) {
                android.util.Log.e(SELF_TAG, "Logging failure: " + ignored.getMessage());
            }
        });
    }
}
