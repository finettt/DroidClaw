package io.finett.droidclaw.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

import io.finett.droidclaw.web.SsrfGuard.AddressResolver;
import io.finett.droidclaw.web.WebFetcher.FetchResult;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Behavioural tests for {@link WebFetcher} against a real (loopback) HTTP server.
 * The SSRF gate is wired to a permissive resolver so the {@code localhost} host used
 * by {@link MockWebServer} is allowed through without real DNS. A redirect to a literal
 * private IP is still blocked because that path is decided without DNS.
 */
public class WebFetcherTest {

    /** Resolves every hostname to a public address so no host is blocked by DNS. */
    static class PermissiveResolver implements AddressResolver {
        @Override
        public List<InetAddress> resolve(String host) {
            try {
                return Collections.singletonList(InetAddress.getByName("8.8.8.8"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private MockWebServer server;
    private WebFetcher fetcher;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        SsrfGuard guard = new SsrfGuard(new PermissiveResolver());
        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(false)
                .build();
        fetcher = new WebFetcher(client, guard);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void fetchReturnsBodyAndMetadata() throws FetchException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><head><title>Hi</title></head><body><p>Hello world</p></body></html>"));

        FetchResult r = fetcher.fetch(server.url("/page").toString());

        assertNotNull(r);
        assertEquals(200, r.getStatus());
        assertTrue(r.getContentType().toLowerCase().startsWith("text/html"));
        assertTrue(r.getBodyAsString().contains("Hello world"));
        assertTrue(r.getBodyAsString().contains("<title>Hi</title>"));
        assertEquals(server.url("/page").toString(), r.getFinalUrl());
    }

    @Test
    public void fetchFollowsRedirectAndUpdatesFinalUrl() throws FetchException {
        server.enqueue(new MockResponse()
                .setResponseCode(302)
                .setHeader("Location", server.url("/target").toString()));
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/plain")
                .setBody("final destination"));

        String startUrl = server.url("/start").toString();
        FetchResult r = fetcher.fetch(startUrl);

        assertEquals(200, r.getStatus());
        assertEquals(server.url("/target").toString(), r.getFinalUrl());
        assertEquals("final destination", r.getBodyAsString());
    }

    @Test
    public void redirectToPrivateIpIsBlockedBySsrfGuard() {
        // First hop is a public loopback redirect; the target is a literal private IP.
        server.enqueue(new MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "http://10.0.0.1/secret"));
        server.enqueue(new MockResponse().setBody("should never be fetched"));

        try {
            fetcher.fetch(server.url("/start").toString());
            fail("Expected FetchException for redirect to private IP");
        } catch (FetchException expected) {
            assertTrue("SSRF reason should surface, was: " + expected.getMessage(),
                    expected.getMessage().toLowerCase().contains("ssrf")
                            || expected.getMessage().toLowerCase().contains("blocked"));
        }
    }

    @Test
    public void disallowedContentTypeIsRejected() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/octet-stream")
                .setBody("binary junk"));

        try {
            fetcher.fetch(server.url("/file").toString());
            fail("Expected FetchException for disallowed content type");
        } catch (FetchException expected) {
            assertTrue("content-type reason should surface, was: " + expected.getMessage(),
                    expected.getMessage().toLowerCase().contains("content-type")
                            || expected.getMessage().toLowerCase().contains("disallow"));
        }
    }

    @Test
    public void nonSuccessStatusIsRejected() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        try {
            fetcher.fetch(server.url("/error").toString());
            fail("Expected FetchException for 500 response");
        } catch (FetchException expected) {
            assertTrue("HTTP status should surface, was: " + expected.getMessage(),
                    expected.getMessage().contains("500"));
        }
    }

    @Test
    public void oversizedBodyExceedsDownloadCap() {
        StringBuilder big = new StringBuilder();
        // Well over the 2 MB cap.
        for (int i = 0; i < (WebFetcher.MAX_DOWNLOAD_BYTES / 4096) + 64; i++) {
            big.append("x".repeat(4096));
        }
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody(big.toString()));

        try {
            fetcher.fetch(server.url("/big").toString());
            fail("Expected FetchException when body exceeds the download cap");
        } catch (FetchException expected) {
            assertTrue("cap reason should surface, was: " + expected.getMessage(),
                    expected.getMessage().toLowerCase().contains("cap"));
        }
    }

    @Test
    public void factoryReturnsBodyAndHeaders() {
        byte[] body = "snapshot".getBytes();
        FetchResult r = WebFetcher.of("https://example.com/", 200, "text/html", body);
        assertEquals("https://example.com/", r.getFinalUrl());
        assertEquals(200, r.getStatus());
        assertEquals("text/html", r.getContentType());
        assertEquals("snapshot", r.getBodyAsString());
        assertEquals("text/html", r.getHeaders().get("content-type"));
    }
}
