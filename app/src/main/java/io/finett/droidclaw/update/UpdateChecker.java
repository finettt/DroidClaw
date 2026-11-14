package io.finett.droidclaw.update;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import io.finett.droidclaw.BuildConfig;

/**
 * Fetches DroidClaw's latest release and compares it against the running build.
 *
 * <p>This is the only class that touches the network. Every other piece is
 * pure or takes its input through {@link #UpdateChecker(ResponseFetcher, String)},
 * so unit tests inject a fake {@link ResponseFetcher} and never open a socket.
 */
public class UpdateChecker {

    private static final String TAG = "UpdateChecker";

    /**
     * The GitHub releases endpoint for this app, hardcoded.
     *
     * <p>This is application infrastructure, not an agent tool: the target repo
     * is fixed by design and there is no user-configurable URL. It therefore
     * does <em>not</em> run through {@link io.finett.droidclaw.web.SsrfGuard}
     * (the same reasoning as the fixed LAN endpoint in #162). The host is
     * nonetheless https-only with a fixed, public value.
     */
    public static final String RELEASES_LATEST_URL =
            "https://api.github.com/repos/finettt/DroidClaw/releases/latest";

    /** 1&nbsp;MB body cap, mirroring the network layer the agent uses. */
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final int TIMEOUT_MS = 15_000;

    /** The shape of the outcome from {@link #checkNow()}. */
    public enum Outcome {
        /** A stable release is available; {@link Result#getInfo()} carries it. */
        UPDATE,
        /** No newer stable release to offer (no releases yet, equal, or older). */
        UP_TO_DATE,
        /** GitHub rate-limited the request (HTTP 403/429); the caller decides. */
        UNAVAILABLE,
        /** A transport or parse failure. */
        ERROR
    }

    /** An immutable outcome of {@link #checkNow()}. */
    public static final class Result {
        private final Outcome outcome;
        private final UpdateInfo info;
        private final String message;

        Result(Outcome outcome, UpdateInfo info, String message) {
            this.outcome = outcome;
            this.info = info;
            this.message = message;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        /** Non-null only when {@link #getOutcome()} is {@link Outcome#UPDATE}. */
        public UpdateInfo getInfo() {
            return info;
        }

        /** Non-null only for {@link Outcome#UNAVAILABLE} and {@link Outcome#ERROR}. */
        public String getMessage() {
            return message;
        }
    }

    private final ResponseFetcher fetcher;
    private final String appVersion;
    private final LatestReleaseParser parser;

    public UpdateChecker() {
        this(new OkHttpReleaseFetcher(BuildConfig.APP_VERSION), BuildConfig.APP_VERSION);
    }

    /**
     * @param fetcher    the injected transport (production uses {@link OkHttpReleaseFetcher})
     * @param appVersion the running build's version, used by {@link #isNewerThanCurrent(UpdateInfo)}
     */
    public UpdateChecker(ResponseFetcher fetcher, String appVersion) {
        this.fetcher = fetcher;
        this.appVersion = appVersion;
        this.parser = new LatestReleaseParser();
    }

    /**
     * Fetches the latest release and resolves it to a {@link Result}.
     *
     * <p>HTTP 404 becomes {@link Outcome#UP_TO_DATE} (no full releases yet),
     * HTTP 403/429 becomes {@link Outcome#UNAVAILABLE}, and a stable release
     * becomes {@link Outcome#UPDATE}. Prereleases and drafts are not offered as
     * updates, so they resolve to {@link Outcome#UP_TO_DATE}.
     */
    public Result checkNow() {
        HttpResponse response;
        try {
            response = fetcher.fetch(RELEASES_LATEST_URL);
        } catch (UpdateNetworkException e) {
            Log.w(TAG, "Update check failed: " + e.getMessage());
            return new Result(Outcome.ERROR, null, e.getMessage());
        }

        int code = response.getStatus();
        if (code == 404) {
            return new Result(Outcome.UP_TO_DATE, null, null);
        }
        if (code == 403 || code == 429) {
            return new Result(Outcome.UNAVAILABLE, null, "GitHub rate limit");
        }
        if (!response.isSuccessful()) {
            return new Result(Outcome.ERROR, null, "HTTP " + code);
        }

        LatestReleaseParser.ParsedRelease parsed = parser.parse(response.getBody());
        UpdateInfo info = parsed.getInfo();
        if (info == null || info.isPrerelease()) {
            // No stable release to offer (no tag, a draft+prerelease, or a prerelease).
            return new Result(Outcome.UP_TO_DATE, null, null);
        }
        return new Result(Outcome.UPDATE, info, null);
    }

    /**
     * @param info a release fetched by {@link #checkNow()}
     * @return {@code true} only if the running build has a CalVer version and the
     *         release tag is numerically newer than it; dev/local builds
     *         (non-CalVer {@code APP_VERSION}) always return {@code false}
     */
    public boolean isNewerThanCurrent(UpdateInfo info) {
        if (info == null) {
            return false;
        }
        if (!UpdateVersions.isCalVer(appVersion)) {
            return false;
        }
        return UpdateVersions.compare(info.getTag(), appVersion) > 0;
    }

    /**
     * A single GET's worth of HTTP result: the status code and the (capped) body.
     * Transport failures surface as {@link UpdateNetworkException}, never here.
     */
    public static final class HttpResponse {
        private final int status;
        private final String body;

        HttpResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public int getStatus() {
            return status;
        }

        public String getBody() {
            return body;
        }

        public boolean isSuccessful() {
            return status >= 200 && status < 300;
        }
    }

    /** Raised when the request never produces an HTTP response (DNS, timeout, socket). */
    public static final class UpdateNetworkException extends Exception {
        public UpdateNetworkException(String message) {
            super(message);
        }
    }

    /** Transport abstraction so tests can supply canned responses without a socket. */
    @FunctionalInterface
    public interface ResponseFetcher {
        HttpResponse fetch(String url) throws UpdateNetworkException;
    }

    /** Production {@link ResponseFetcher} backed by OkHttp. */
    static final class OkHttpReleaseFetcher implements ResponseFetcher {
        private final OkHttpClient client;
        private final String userAgent;

        OkHttpReleaseFetcher(String appVersion) {
            this.client = new OkHttpClient.Builder()
                    .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build();
            this.userAgent = "DroidClaw/" + appVersion;
        }

        @Override
        public HttpResponse fetch(String url) throws UpdateNetworkException {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .build();
            Response response = null;
            try {
                response = client.newCall(request).execute();
                int code = response.code();
                String body = streamWithCap(response);
                return new HttpResponse(code, body);
            } catch (IOException e) {
                throw new UpdateNetworkException("Network error: " + e.getMessage());
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }

        private String streamWithCap(Response response) throws IOException {
            InputStream is = response.body() != null ? response.body().byteStream() : null;
            if (is == null) {
                return "";
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                out.write(buf, 0, read);
                if (out.size() > MAX_BODY_BYTES) {
                    is.close();
                    throw new IOException("Response exceeds the " + MAX_BODY_BYTES + "-byte cap");
                }
            }
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
