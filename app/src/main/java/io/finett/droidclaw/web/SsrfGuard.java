package io.finett.droidclaw.web;

import android.util.Log;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-Side Request Forgery (SSRF) guard.
 *
 * <p>Rejects a URL when its host is an http(s) address that resolves to (or is
 * literally) a private, loopback, link-local, shared, or "this" network address.
 * The check runs against every resolved DNS record, so a hostname that resolves to
 * a mix of clean and private addresses is rejected.
 *
 * <p>Pure Java: resolution happens through the {@link AddressResolver} interface so
 * tests can inject a fake resolver instead of hitting real DNS.
 */
public class SsrfGuard {

    private static final String TAG = "SsrfGuard";

    /** Resolves a hostname (or literal IP) to the set of addresses it maps to. */
    public interface AddressResolver {
        /**
         * @param host a hostname or literal IP address
         * @return the resolved addresses, or an empty list if the host cannot be resolved
         */
        List<InetAddress> resolve(String host);
    }

    /** Raised when a URL is rejected by the guard. */
    public static class SsrfViolation extends RuntimeException {
        public SsrfViolation(String message) {
            super(message);
        }
    }

    private final AddressResolver resolver;

    public SsrfGuard() {
        this(host -> {
            try {
                return java.util.Arrays.asList(InetAddress.getAllByName(host));
            } catch (Exception ignored) {
                return java.util.Collections.emptyList();
            }
        });
    }

    public SsrfGuard(AddressResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Validates a URL against the SSRF gate.
     *
     * @throws SsrfViolation if the scheme is not http(s) or the host resolves to a blocked address
     */
    public void validate(URI uri) {
        if (uri == null) {
            throw new SsrfViolation("URL is null");
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new SsrfViolation("Only http and https schemes are allowed, got: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw new SsrfViolation("URL has no host");
        }
        validateHost(host);
    }

    /**
     * Validates a single host against the blocked-address rules.
     *
     * <p>If the host is a literal IP in a blocked range it is rejected immediately, without DNS.
     * Otherwise every resolved DNS record is checked; if any record is blocked the host is
     * rejected.
     *
     * @throws SsrfViolation if the host is, or resolves to, a blocked address
     */
    public void validateHost(String host) {
        InetAddress literal = parseIpLiteral(host);
        if (literal != null) {
            // A pure IP literal is fully determined here — validate it directly, no DNS.
            if (isBlocked(literal)) {
                throw new SsrfViolation("Host '" + host + "' is a blocked address " + host);
            }
            return;
        }

        // A hostname: resolve via DNS and check every record.
        List<InetAddress> addresses = resolve(host);
        for (InetAddress addr : addresses) {
            if (isBlocked(addr)) {
                throw new SsrfViolation(
                        "Host '" + host + "' resolves to a blocked address " + addr.getHostAddress());
            }
        }
        if (addresses.isEmpty()) {
            throw new SsrfViolation("Could not resolve host '" + host + "'");
        }
    }

    /**
     * Parses {@code host} as a pure IP literal (IPv4 or IPv6) without performing a DNS
     * lookup. Returns {@code null} for hostnames or unparsable input, so the caller can
     * fall through to DNS resolution.
     */
    private static InetAddress parseIpLiteral(String host) {
        if (host == null) {
            return null;
        }
        // IPv4 dotted-quad: four numeric octets. Pure string check, no DNS.
        if (host.indexOf('.') >= 0) {
            String[] parts = host.split("\\.", -1);
            if (parts.length == 4) {
                try {
                    byte[] b = new byte[4];
                    for (int i = 0; i < 4; i++) {
                        int octet = Integer.parseInt(parts[i]);
                        if (octet < 0 || octet > 255) {
                            return null;
                        }
                        b[i] = (byte) octet;
                    }
                    return InetAddress.getByAddress(b);
                } catch (Exception e) {
                    return null;
                }
            }
            // Has dots but not a dotted-quad, so it is a hostname, not a literal.
            return null;
        }
        // IPv6: only strings with a colon can be IPv6 literals. A syntactically valid
        // literal is parsed by getByName without DNS; an unparsable one throws.
        if (host.indexOf(':') >= 0) {
            try {
                return InetAddress.getByName(host);
            } catch (Exception e) {
                return null;
            }
        }
        // No dots and no colons -> a bare hostname. Never perform DNS here.
        return null;
    }

    private List<InetAddress> resolve(String host) {
        try {
            return new ArrayList<>(resolver.resolve(host));
        } catch (Exception e) {
            Log.w(TAG, "DNS resolution failed for " + host + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Returns {@code true} if the address falls in a blocked range (private, loopback,
     * link-local, shared, or "this" network), for either IPv4 or IPv6. IPv4-mapped IPv6
     * addresses are unwrapped first.
     */
    public static boolean isBlocked(InetAddress addr) {
        if (addr == null) {
            return false;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            return isBlockedIpv4(b);
        }
        if (b.length == 16) {
            if (isIpv4Mapped(b)) {
                return isBlockedIpv4(new byte[]{b[12], b[13], b[14], b[15]});
            }
            return isBlockedIpv6(b);
        }
        return false;
    }

    private static boolean isBlockedIpv4(byte[] b) {
        int b0 = b[0] & 0xFF;
        int b1 = b[1] & 0xFF;
        // 0.0.0.0/8 — "this" network / unspecified
        if (b0 == 0) {
            return true;
        }
        // 10.0.0.0/8 — private
        if (b0 == 10) {
            return true;
        }
        // 127.0.0.0/8 — loopback
        if (b0 == 127) {
            return true;
        }
        // 169.254.0.0/16 — link-local
        if (b0 == 169 && b1 == 254) {
            return true;
        }
        // 172.16.0.0/12 — private
        if (b0 == 172 && (b1 & 0xF0) == 16) {
            return true;
        }
        // 192.168.0.0/16 — private
        if (b0 == 192 && b1 == 168) {
            return true;
        }
        // 100.64.0.0/10 — carrier-grade NAT / shared address space
        if (b0 == 100 && (b1 & 0xC0) == 64) {
            return true;
        }
        return false;
    }

    private static boolean isBlockedIpv6(byte[] b) {
        // ::1 — loopback (all zeros, last byte 1)
        boolean allZero = true;
        for (int i = 0; i < 15; i++) {
            if (b[i] != 0) {
                allZero = false;
                break;
            }
        }
        if (allZero && (b[15] & 0xFF) == 1) {
            return true;
        }
        // fc00::/7 — unique local address
        if ((b[0] & 0xFE) == 0xfc) {
            return true;
        }
        // fe80::/10 — link-local
        if ((b[0] & 0xFF) == 0xfe && (b[1] & 0xC0) == 0x80) {
            return true;
        }
        return false;
    }

    private static boolean isIpv4Mapped(byte[] b) {
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) {
                return false;
            }
        }
        return (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
    }
}
