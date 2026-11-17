package io.finett.droidclaw.shell;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Locates and inspects the PRoot executable packaged with the application. */
public final class ProotRuntime {
    private static final String TAG = "ProotRuntime";
    private static final String PROOT_LIBRARY = "libproot.so";

    private final File executable;
    private final String nativeLibraryDir;

    public ProotRuntime(Context context) {
        Context applicationContext = context.getApplicationContext();
        nativeLibraryDir = applicationContext.getApplicationInfo().nativeLibraryDir;
        executable = locate(applicationContext);
    }

    /** Return the packaged PRoot executable path for this application. */
    public static File locate(Context context) {
        String libraryDir = context.getApplicationInfo().nativeLibraryDir;
        File executable = new File(libraryDir, PROOT_LIBRARY);
        Log.d(TAG, "locate: nativeLibraryDir=" + libraryDir
                + " executable=" + executable.getAbsolutePath());
        return executable;
    }

    /** Return whether the packaged PRoot executable exists and can be executed. */
    public boolean isAvailable() {
        boolean available = executable.isFile() && executable.canExecute();
        Log.d(TAG, "isAvailable: path=" + executable.getAbsolutePath()
                + " isFile=" + executable.isFile()
                + " canExecute=" + executable.canExecute()
                + " available=" + available);
        return available;
    }

    /** Run PRoot with --version and return its combined standard output and error. */
    public String version() {
        if (!isAvailable()) {
            Log.e(TAG, "version: PRoot is not available at " + executable.getAbsolutePath());
            return "";
        }

        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    executable.getAbsolutePath(), "--version");
            builder.environment().put("LD_LIBRARY_PATH", nativeLibraryDir);
            builder.redirectErrorStream(true);
            Log.d(TAG, "version: command=" + builder.command()
                    + " LD_LIBRARY_PATH=" + nativeLibraryDir);
            process = builder.start();
            String output = readOutput(process.getInputStream());
            int exitCode = process.waitFor();
            Log.d(TAG, "version: exitCode=" + exitCode + " output=" + output);
            return output;
        } catch (IOException e) {
            Log.e(TAG, "version: failed to execute " + executable.getAbsolutePath(), e);
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "version: interrupted while waiting for PRoot", e);
            return "";
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static String readOutput(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
    }
}
