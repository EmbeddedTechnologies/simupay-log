package com.etl.simupaylog;

import android.graphics.Bitmap;
import android.util.Base64;

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
 *   Log.setSimulatorUrl("https://sim.embeddedc.co.uk/api/log");
 *   Log.setTerminalId(myTid);   // included in every log entry for dashboard filtering
 * </pre>
 * Set URL to {@code null} (the default) to disable remote forwarding.
 */
public final class Log {

    private static final String SELF_TAG = "SimuLog";

    /** URL of the simulator POST /api/log endpoint. Null = remote forwarding disabled. */
    private static volatile String sSimulatorUrl = "https://sim.embeddedc.co.uk/api/log";

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
     * @param url full URL e.g. {@code "https://sim.embeddedc.co.uk/api/log"},
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

    public static String getStackTraceString(Throwable tr) {
        return android.util.Log.getStackTraceString(tr);
    }

    public static boolean isLoggable(String tag, int level) {
        return android.util.Log.isLoggable(tag, level);
    }

    // ── Internal ────────────────────────────────────────────────────────────

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
