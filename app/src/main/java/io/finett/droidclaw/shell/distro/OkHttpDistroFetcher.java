package io.finett.droidclaw.shell.distro;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Production {@link DistroFetcher} backed by OkHttp.
 *
 * <p>The client uses a 15&nbsp;second connect timeout and a 60&nbsp;second
 * read-idle timeout (the download is idle between packets, not stalled). A 404
 * or any other non-success status becomes a typed {@link DistroException};
 * transport failures (DNS, socket, timeout) do too.
 */
public final class OkHttpDistroFetcher implements DistroFetcher {

    private static final String TAG = "OkHttpDistroFetcher";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_IDLE_TIMEOUT_MS = 60_000;

    private final OkHttpClient client;

    public OkHttpDistroFetcher() {
        this(new OkHttpClient.Builder());
    }

    public OkHttpDistroFetcher(OkHttpClient.Builder builder) {
        this.client = builder
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_IDLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public long download(String url, OutputStream out, ProgressListener listener)
            throws DistroException, IOException {
        Request request = new Request.Builder().url(url).build();
        Response response = null;
        try {
            response = client.newCall(request).execute();
            int code = response.code();
            if (code == 404) {
                throw new DistroException("Base image not found at " + url + " (HTTP 404)");
            }
            if (!response.isSuccessful()) {
                throw new DistroException(
                        "Base image download failed: HTTP " + code + " for " + url);
            }
            return streamBody(response, url, out, listener);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private long streamBody(Response response, String url, OutputStream out, ProgressListener listener)
            throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            throw new IOException("Empty response body");
        }
        long total = body.contentLength();
        byte[] buffer = new byte[8192];
        long written = 0;
        int read;
        try (java.io.InputStream in = body.byteStream()) {
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                written += read;
                if (listener != null) {
                    listener.onProgress(written, total);
                }
            }
        }
        Log.d(TAG, "Downloaded " + written + " bytes from " + url);
        return written;
    }
}
