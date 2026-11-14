package io.finett.droidclaw.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;

import io.finett.droidclaw.update.UpdateChecker.HttpResponse;
import io.finett.droidclaw.update.UpdateChecker.Outcome;
import io.finett.droidclaw.update.UpdateChecker.Result;
import io.finett.droidclaw.update.UpdateChecker.UpdateNetworkException;
import io.finett.droidclaw.update.UpdateChecker.ResponseFetcher;

import org.junit.Test;

/** Behavioural tests for {@link UpdateChecker} using a fake transport (no network). */
public class UpdateCheckerTest {

    /** Builds a small release JSON object without hand-escaped string literals. */
    private static JsonObject releaseJson(String tag, boolean prerelease) {
        JsonObject o = new JsonObject();
        o.addProperty("tag_name", tag);
        o.addProperty("prerelease", prerelease);
        return o;
    }

    /** Returns canned responses / exceptions instead of opening a socket. */
    private static final class FakeFetcher implements ResponseFetcher {
        private final HttpResponse response;
        private final UpdateNetworkException error;

        FakeFetcher(int status, String body) {
            this.response = new HttpResponse(status, body);
            this.error = null;
        }

        FakeFetcher(UpdateNetworkException error) {
            this.response = null;
            this.error = error;
        }

        @Override
        public HttpResponse fetch(String url) throws UpdateNetworkException {
            if (url == null || !UpdateChecker.RELEASES_LATEST_URL.equals(url)) {
                throw new AssertionError("unexpected URL: " + url);
            }
            if (error != null) {
                throw error;
            }
            return response;
        }

        static FakeFetcher stable(String tag) {
            return new FakeFetcher(200, releaseJson(tag, false).toString());
        }

        static FakeFetcher release(String tag, boolean prerelease) {
            return new FakeFetcher(200, releaseJson(tag, prerelease).toString());
        }
    }

    @Test
    public void http404_isNoUpdateNotError() {
        UpdateChecker checker = new UpdateChecker(new FakeFetcher(404, ""), "2026.10.10");

        Result r = checker.checkNow();

        assertEquals(Outcome.UP_TO_DATE, r.getOutcome());
        assertEquals(null, r.getInfo());
    }

    @Test
    public void http403_isUnavailable() {
        UpdateChecker checker = new UpdateChecker(new FakeFetcher(403, ""), "2026.10.10");

        Result r = checker.checkNow();

        assertEquals(Outcome.UNAVAILABLE, r.getOutcome());
        assertEquals("GitHub rate limit", r.getMessage());
    }

    @Test
    public void http429_isUnavailable() {
        UpdateChecker checker = new UpdateChecker(new FakeFetcher(429, ""), "2026.10.10");
        assertEquals(Outcome.UNAVAILABLE, checker.checkNow().getOutcome());
    }

    @Test
    public void otherNon2xx_isError() {
        UpdateChecker checker = new UpdateChecker(new FakeFetcher(500, ""), "2026.10.10");
        assertEquals(Outcome.ERROR, checker.checkNow().getOutcome());
    }

    @Test
    public void networkError_isError() {
        UpdateChecker checker = new UpdateChecker(
                new FakeFetcher(new UpdateNetworkException("socket closed")), "2026.10.10");
        assertEquals(Outcome.ERROR, checker.checkNow().getOutcome());
    }

    @Test
    public void newerStable_isUpdateAndNewerThanCurrent() {
        UpdateChecker checker = new UpdateChecker(FakeFetcher.stable("2026.11.07"), "2026.10.10");

        Result r = checker.checkNow();

        assertEquals(Outcome.UPDATE, r.getOutcome());
        assertEquals("2026.11.07", r.getInfo().getTag());
        assertTrue(checker.isNewerThanCurrent(r.getInfo()));
    }

    @Test
    public void equalStable_isUpdateButNotNewer() {
        UpdateChecker checker = new UpdateChecker(FakeFetcher.stable("2026.10.10"), "2026.10.10");

        Result r = checker.checkNow();

        assertEquals(Outcome.UPDATE, r.getOutcome());
        assertFalse(checker.isNewerThanCurrent(r.getInfo()));
    }

    @Test
    public void olderStable_isUpdateButNotNewer() {
        UpdateChecker checker = new UpdateChecker(FakeFetcher.stable("2026.09.01"), "2026.10.10");
        Result r = checker.checkNow();
        assertEquals(Outcome.UPDATE, r.getOutcome());
        assertFalse(checker.isNewerThanCurrent(r.getInfo()));
    }

    @Test
    public void prereleaseResolvesToNoUpdate() {
        UpdateChecker checker = new UpdateChecker(FakeFetcher.release("2026.12.01", true), "2026.10.10");
        assertEquals(Outcome.UP_TO_DATE, checker.checkNow().getOutcome());
    }

    @Test
    public void devBuild_checkNowWorksButNeverNewer() {
        // APP_VERSION is a git hash / "dev": checkNow still returns the release,
        // but isNewerThanCurrent is always false for non-CalVer current versions.
        UpdateChecker checker = new UpdateChecker(FakeFetcher.stable("2026.11.07"), "dev");

        Result r = checker.checkNow();
        assertEquals(Outcome.UPDATE, r.getOutcome());
        assertFalse(checker.isNewerThanCurrent(r.getInfo()));

        UpdateChecker hashChecker = new UpdateChecker(FakeFetcher.stable("2026.11.07"), "a1b2c3d");
        assertFalse(hashChecker.isNewerThanCurrent(hashChecker.checkNow().getInfo()));
    }

    @Test
    public void emptyBody_isNoUpdate() {
        UpdateChecker checker = new UpdateChecker(new FakeFetcher(200, ""), "2026.10.10");
        assertEquals(Outcome.UP_TO_DATE, checker.checkNow().getOutcome());
    }

    @Test
    public void isOfferable_calVerNewer_isOfferable() {
        UpdateChecker checker = new UpdateChecker(FakeFetcher.stable("2026.11.07"), "2026.10.10");

        Result r = checker.checkNow();

        assertTrue(checker.isOfferable(r.getInfo()));
    }

    @Test
    public void isOfferable_calVerEqual_isNotOfferable() {
        UpdateChecker checker = new UpdateChecker(FakeFetcher.stable("2026.10.10"), "2026.10.10");

        Result r = checker.checkNow();

        assertFalse(checker.isOfferable(r.getInfo()));
    }

    @Test
    public void isOfferable_calVerOlder_isNotOfferable() {
        UpdateChecker checker = new UpdateChecker(FakeFetcher.stable("2026.09.01"), "2026.10.10");

        Result r = checker.checkNow();

        assertFalse(checker.isOfferable(r.getInfo()));
    }

    @Test
    public void isOfferable_devBuild_isOfferable() {
        // A non-CalVer current version cannot be compared, so any release is offered.
        UpdateChecker checker = new UpdateChecker(FakeFetcher.stable("2026.11.07"), "dev");

        Result r = checker.checkNow();

        assertTrue(checker.isOfferable(r.getInfo()));
    }

    @Test
    public void isOfferable_null_isNotOfferable() {
        UpdateChecker checker = new UpdateChecker(new FakeFetcher(200, ""), "2026.10.10");

        assertFalse(checker.isOfferable(null));
    }

}
