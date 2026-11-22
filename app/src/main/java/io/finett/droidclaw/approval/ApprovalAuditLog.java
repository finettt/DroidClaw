package io.finett.droidclaw.approval;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Append-only audit trail for shell commands, one line per command in every approval mode
 * (ask_all, auto, full_access). Never records tool output bodies — only the command string,
 * the disposition (verdict or dialog/judge outcome), and the exit code when known.
 *
 * <p>File: {@code <filesDir>/audit/shell.log}. One line per command, so the log is grep-friendly
 * and cannot be poisoned by command output.
 */
public final class ApprovalAuditLog {

    private static final String TAG = "ApprovalAudit";
    private static final String DIR = "audit";
    private static final String FILE = "shell.log";

    private final File logFile;
    private final Object lock = new Object();

    /**
     * Resolve the audit log path without touching the filesystem; the directory is
     * created on the first {@link #log} call so construction never does I/O.
     */
    public ApprovalAuditLog(Context context) {
        this.logFile = new File(new File(context.getFilesDir(), DIR), FILE);
    }

    /**
     * Record one shell command.
     *
     * @param mode        shell-approval mode: ask_all / auto / full_access
     * @param command     the command string (control characters are sanitised to keep one line)
     * @param disposition verdict or outcome, e.g. allow / deny / dialog-approved /
     *                    dialog-denied / silent
     * @param exitCode    process exit code, or {@code null} when the command was not run
     */
    public void log(String mode, String command, String disposition, Integer exitCode) {
        synchronized (lock) {
            File auditDir = logFile.getParentFile();
            if (auditDir != null && !auditDir.exists() && !auditDir.mkdirs()) {
                Log.w(TAG, "Could not create audit directory: " + auditDir.getAbsolutePath());
            }
            try (Writer w = new BufferedWriter(new FileWriter(logFile, true))) {
                w.write(format(mode, command, disposition, exitCode));
                w.write("\n");
            } catch (IOException e) {
                Log.e(TAG, "Failed to write shell audit log", e);
            }
        }
    }

    private String format(String mode, String command, String disposition, Integer exitCode) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.currentTimeMillis()).append(" | ");
        sb.append("mode=").append(sanitize(mode));
        sb.append(" | cmd=").append(sanitize(command));
        sb.append(" | disposition=").append(sanitize(disposition));
        sb.append(" | exit=").append(exitCode == null ? "-" : String.valueOf(exitCode));
        return sb.toString();
    }

    /** Collapse whitespace/newlines to single spaces so each command stays on one log line. */
    private static String sanitize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(' ');
            } else if (c < 0x20) {
                // drop other control characters (incl. NUL) to avoid log injection
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
