package io.finett.droidclaw.web;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Native Java page fetcher.
 *
 * <p>Downloads an http(s) document with OkHttp and enforces the safety and size
 * limits the agent needs before the body is handed to the HTML extractor:
 *
 * <ul>
 *   <li>15s connect/read/write timeouts;</li>
 *   <li>up to 5 redirects, each hop re-validated by {@link SsrfGuard};</li>
 *   <li>a 2&nbsp;MB body cap enforced while streaming (the connection is aborted,
 *       the full body is never buffered before the cap check);</li>
 *   <li>a content-type allowlist — {@code text/html}, {@code text/plain},
 *       {@code application/json}, {@code application/xhtml+xml};</li>
 *   <li>a fixed {@code DroidClaw} User-Agent.</li>
 * </ul>
 */
public class WebFetcher {

    private static final String TAG = "WebFetcher";

    public static final String USER_AGENT = "DroidClaw/1.0 (+https://github.com/finettt/DroidClaw)";

    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_REDIRECTS = 5;

    /** Hard cap on the response body that will be buffered and returned to the agent. */
    public static final int MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024; // 2 MB

    private static final Set<String> ALLOWED_CONTENT_TYPES = new HashSet<>(Arrays.asList(
        "text/html", "text/plain", "application/json", "application/xhtml+xml"));

    private final OkHttpClient httpClient;
    private final SsrfGuard ssrfGuard;

    public WebFetcher() {
        this(new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build(), new SsrfGuard());
    }

    /** Constructor for tests that inject a configured client and a fake SSRF resolver. */
    public WebFetcher(OkHttpClient httpClient, SsrfGuard ssrfGuard) {
        this.httpClient = httpClient;
        this.ssrfGuard = ssrfGuard;
    }

    /**
     * Fetches {@code rawUrl}, following redirects and enforcing every limit.
     *
     * @throws FetchException if the URL fails the SSRF gate, the status is not 2xx,
     *                        the content-type is disallowed, the body exceeds the cap,
     *                        or the redirect budget is exhausted
     */
    public FetchResult fetch(String rawUrl) throws FetchException {
        String currentUrl = rawUrl;
        String finalUrl = rawUrl;
        Response response = null;
        try {
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                // Re-validate every hop (initial URL and each redirect target).
                ssrfGuard.validate(URI.create(currentUrl));

                Request request = new Request.Builder()
                        .url(currentUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,text/plain,application/json,*/*")
                        .build();

                response = httpClient.newCall(request).execute();
                int code = response.code();

                if (isRedirect(code)) {
                    String location = response.header("Location");
                    response.close();
                    if (location == null || location.isEmpty()) {
                        throw new FetchException(
                                "Redirect without Location header (status " + code + ")");
                    }
                    if (redirect >= MAX_REDIRECTS) {
                        throw new FetchException(
                                "Too many redirects (max " + MAX_REDIRECTS + ")");
                    }
                    currentUrl = resolveLocation(currentUrl, location);
                    continue;
                }

                if (!response.isSuccessful()) {
                    throw new FetchException("HTTP " + code + ": " + response.message());
                }

                finalUrl = currentUrl;
                return parseResponse(response, finalUrl);
            }
            throw new FetchException("Unexpected redirect loop");
        } catch (SsrfGuard.SsrfViolation e) {
            throw new FetchException("Blocked by SSRF guard: " + e.getMessage());
        } catch (IOException e) {
            throw new FetchException("Network error: " + e.getMessage(), e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private static boolean isRedirect(int code) {
        return code >= 300 && code < 400;
    }

    private String resolveLocation(String base, String location) throws FetchException {
        try {
            URI resolved = URI.create(base).resolve(location);
            if (resolved == null) {
                throw new FetchException("Cannot resolve redirect target: " + location);
            }
            return resolved.toString();
        } catch (Exception e) {
            throw new FetchException("Cannot resolve redirect: " + location + " -> " + e.getMessage());
        }
    }

    private FetchResult parseResponse(Response response, String finalUrl) throws FetchException {
        String contentType = normalizeContentType(response.header("Content-Type"));
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new FetchException("Disallowed content-type: " + contentType);
        }

        byte[] body = streamWithCap(response);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", contentType);
        String contentLength = response.header("Content-Length");
        if (contentLength != null) {
            headers.put("content-length", contentLength);
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Fetched " + body.length + " bytes from " + finalUrl + " (" + contentType + ")");
        }
        return new FetchResult(finalUrl, response.code(), contentType, headers, body);
    }

    /**
     * Streams the response body in chunks, aborting the connection as soon as the
     * byte count would exceed {@link #MAX_DOWNLOAD_BYTES}. The full body is never
     * buffered before the cap is checked.
     */
    private byte[] streamWithCap(Response response) throws FetchException {
        InputStream is = response.body() != null ? response.body().byteStream() : null;
        if (is == null) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int read;
        try {
            while ((read = is.read(buf)) != -1) {
                out.write(buf, 0, read);
                if (out.size() > MAX_DOWNLOAD_BYTES) {
                    is.close();
                    throw new FetchException("Response exceeds the "
                            + MAX_DOWNLOAD_BYTES + "-byte download cap");
                }
            }
        } catch (IOException e) {
            throw new FetchException("Failed to read response body: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    private static String normalizeContentType(String header) {
        if (header == null || header.trim().isEmpty()) {
            return "";
        }
        // Drop parameters such as "; charset=utf-8".
        int semi = header.indexOf(';');
        String type = (semi >= 0 ? header.substring(0, semi) : header).trim();
        return type.toLowerCase(Locale.ROOT);
    }

    /** Public factory for building a result (used by tests and callers). */
    public static FetchResult of(String finalUrl, int status, String contentType, byte[] body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", contentType);
        return new FetchResult(finalUrl, status, contentType, headers, body);
    }

    /** Immutable snapshot of a successful fetch. */
    public static class FetchResult {
        private final String finalUrl;
        private final int status;
        private final String contentType;
        private final Map<String, String> headers;
        private final byte[] body;

        FetchResult(String finalUrl, int status, String contentType,
                    Map<String, String> headers, byte[] body) {
            this.finalUrl = finalUrl;
            this.status = status;
            this.contentType = contentType;
            this.headers = headers;
            this.body = body;
        }

        public String getFinalUrl() {
            return finalUrl;
        }

        public int getStatus() {
            return status;
        }

        public String getContentType() {
            return contentType;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public byte[] getBody() {
            return body;
        }

        public String getBodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
