package io.finett.droidclaw.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.finett.droidclaw.web.SsrfGuard.AddressResolver;
import io.finett.droidclaw.web.SsrfGuard.SsrfViolation;
import org.junit.Test;

/**
 * Unit tests for the SSRF gate. Resolution is faked so no real DNS is used.
 */
public class SsrfGuardTest {

    /** Fake resolver backed by a host -> list of raw IPv4/IPv6 dotted strings. */
    static class FakeResolver implements AddressResolver {
        final Map<String, String[]> table = new HashMap<>();

        FakeResolver map(String host, String... ips) {
            table.put(host, ips);
            return this;
        }

        @Override
        public List<InetAddress> resolve(String host) {
            String[] ips = table.get(host);
            List<InetAddress> out = new ArrayList<>();
            if (ips == null) {
                return out;
            }
            for (String ip : ips) {
                try {
                    out.add(InetAddress.getByName(ip));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return out;
        }
    }

    private static SsrfGuard guardWith(FakeResolver resolver) {
        return new SsrfGuard(resolver);
    }

    private static void assertBlocked(SsrfGuard guard, String host) {
        try {
            guard.validateHost(host);
            fail("Expected SsrfViolation for host " + host);
        } catch (SsrfViolation expected) {
            // ok
        }
    }

    private static void assertAllowed(SsrfGuard guard, String host) {
        try {
            guard.validateHost(host);
        } catch (SsrfViolation unexpected) {
            fail("Unexpected SsrfViolation for host " + host + ": " + unexpected.getMessage());
        }
    }

    // --- IPv4 literal ranges ---

    @Test
    public void rejectsLoopbackLiteral() {
        assertBlocked(guardWith(new FakeResolver()), "127.0.0.1");
        assertBlocked(guardWith(new FakeResolver()), "127.5.6.7");
    }

    @Test
    public void rejectsPrivateClassALiteral() {
        assertBlocked(guardWith(new FakeResolver()), "10.0.0.1");
        assertBlocked(guardWith(new FakeResolver()), "10.255.255.255");
    }

    @Test
    public void rejectsPrivateClassBLiteral() {
        assertBlocked(guardWith(new FakeResolver()), "172.16.0.1");
        assertBlocked(guardWith(new FakeResolver()), "172.31.255.255");
        // just outside the /12
        assertAllowed(guardWith(new FakeResolver()), "172.15.0.1");
        assertAllowed(guardWith(new FakeResolver()), "172.32.0.1");
    }

    @Test
    public void rejectsPrivateClassCLiteral() {
        assertBlocked(guardWith(new FakeResolver()), "192.168.1.1");
        assertBlocked(guardWith(new FakeResolver()), "192.168.0.0");
    }

    @Test
    public void rejectsLinkLocalLiteral() {
        assertBlocked(guardWith(new FakeResolver()), "169.254.10.20");
    }

    @Test
    public void rejectsZeroNetLiteral() {
        assertBlocked(guardWith(new FakeResolver()), "0.0.0.0");
        assertBlocked(guardWith(new FakeResolver()), "0.1.2.3");
    }

    @Test
    public void rejectsCgnatLiteral() {
        assertBlocked(guardWith(new FakeResolver()), "100.64.0.1");
        assertBlocked(guardWith(new FakeResolver()), "100.127.255.255");
        // just outside the /10
        assertAllowed(guardWith(new FakeResolver()), "100.63.0.1");
        assertAllowed(guardWith(new FakeResolver()), "100.128.0.1");
    }

    @Test
    public void allowsPublicLiteral() {
        assertAllowed(guardWith(new FakeResolver()), "8.8.8.8");
        assertAllowed(guardWith(new FakeResolver()), "1.1.1.1");
        assertAllowed(guardWith(new FakeResolver()), "93.184.216.34");
    }

    // --- IPv6 literal ranges ---

    @Test
    public void rejectsLoopbackIPv6() {
        assertBlocked(guardWith(new FakeResolver()), "::1");
    }

    @Test
    public void rejectsUniqueLocalIPv6() {
        assertBlocked(guardWith(new FakeResolver()), "fc00::1");
        assertBlocked(guardWith(new FakeResolver()), "fd12:3456::8");
        // just outside /7
        assertAllowed(guardWith(new FakeResolver()), "fe00::1");
    }

    @Test
    public void rejectsLinkLocalIPv6() {
        assertBlocked(guardWith(new FakeResolver()), "fe80::1");
        assertBlocked(guardWith(new FakeResolver()), "fe80::dead:beef");
    }

    @Test
    public void allowsPublicIPv6() {
        assertAllowed(guardWith(new FakeResolver()), "2606:4700:4700::1111");
    }

    // --- IPv4-mapped IPv6 ---

    private static InetAddress ipv4Mapped(int a, int b, int c, int d) throws Exception {
        byte[] b16 = new byte[16];
        b16[10] = (byte) 0xFF;
        b16[11] = (byte) 0xFF;
        b16[12] = (byte) a;
        b16[13] = (byte) b;
        b16[14] = (byte) c;
        b16[15] = (byte) d;
        return InetAddress.getByAddress(b16);
    }

    @Test
    public void rejectsIpv4MappedIpv6() throws Exception {
        assertTrue(SsrfGuard.isBlocked(ipv4Mapped(127, 0, 0, 1)));
        assertTrue(SsrfGuard.isBlocked(ipv4Mapped(10, 0, 0, 5)));
        assertFalse(SsrfGuard.isBlocked(ipv4Mapped(8, 8, 8, 8)));
    }

    // --- DNS resolution via fake resolver ---

    @Test
    public void blocksHostnameResolvingToPrivate() {
        FakeResolver resolver = new FakeResolver()
                .map("private.example", "10.0.0.5");
        assertBlocked(guardWith(resolver), "private.example");
    }

    @Test
    public void allowsHostnameResolvingToPublic() {
        FakeResolver resolver = new FakeResolver()
                .map("example.com", "93.184.216.34");
        assertAllowed(guardWith(resolver), "example.com");
    }

    @Test
    public void blocksMixedResolvedAddresses() {
        // one clean, one private -> blocked because ANY record is blocked
        FakeResolver resolver = new FakeResolver()
                .map("mixed.example", "8.8.8.8", "127.0.0.1");
        assertBlocked(guardWith(resolver), "mixed.example");
    }

    @Test
    public void blocksHostnameThatFailsToResolve() {
        FakeResolver resolver = new FakeResolver()
                .map("nope.example"); // no mapping -> resolves to nothing
        assertBlocked(guardWith(resolver), "nope.example");
    }

    // --- scheme rule ---

    @Test
    public void rejectsNonHttpScheme() {
        SsrfGuard guard = guardWith(new FakeResolver().map("example.com", "8.8.8.8"));
        for (String bad : new String[]{"ftp://example.com/", "file:///etc/passwd",
                "gopher://example.com/", "javascript:alert(1)"}) {
            try {
                guard.validate(URI.create(bad));
                fail("Expected SsrfViolation for " + bad);
            } catch (SsrfViolation expected) {
                // ok
            }
        }
    }

    @Test
    public void allowsHttpAndHttps() {
        SsrfGuard guard = guardWith(new FakeResolver().map("example.com", "8.8.8.8"));
        guard.validate(URI.create("http://example.com/"));
        guard.validate(URI.create("https://example.com/path?q=1"));
    }

    // --- static helper coverage ---

    @Test
    public void isBlockedMatchesForKnownAddresses() throws Exception {
        assertTrue(SsrfGuard.isBlocked(InetAddress.getByName("127.0.0.1")));
        assertFalse(SsrfGuard.isBlocked(InetAddress.getByName("8.8.8.8")));
        assertTrue(SsrfGuard.isBlocked(InetAddress.getByName("192.168.0.1")));
        assertTrue(SsrfGuard.isBlocked(InetAddress.getByName("::1")));
        assertFalse(SsrfGuard.isBlocked(Inet4Address.getByName("1.1.1.1")));
    }
}
