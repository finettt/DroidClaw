package io.finett.droidclaw.shell.distro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Unit tests for {@link DistroSpec}: the android-to-Linux ABI mapping and the
 * pinned arm64/amd64 image coordinates (url, sha256, size).
 */
public class DistroSpecTest {

    @Test
    public void osAbi_mapsKnownAndroidAbis() throws Exception {
        assertEquals("arm64", DistroSpec.osAbi("arm64-v8a"));
        assertEquals("arm64", DistroSpec.osAbi("aarch64"));
        assertEquals("amd64", DistroSpec.osAbi("x86_64"));
        assertEquals("amd64", DistroSpec.osAbi("amd64"));
    }

    @Test
    public void osAbi_rejectsUnsupportedAndroidAbi() {
        try {
            DistroSpec.osAbi("armeabi-v7a");
            fail("expected DistroException for unsupported ABI");
        } catch (DistroException expected) {
            assertTrue(expected.getMessage().contains("arm64-v8a"));
        }
    }

    @Test
    public void osAbi_rejectsNull() {
        try {
            DistroSpec.osAbi(null);
            fail("expected DistroException for null ABI");
        } catch (DistroException expected) {
            // acceptable
        }
    }

    @Test
    public void entryForAbi_returnsPinnedEntries() throws Exception {
        assertSame(DistroSpec.ARM64, DistroSpec.entryForAbi("arm64"));
        assertSame(DistroSpec.AMD64, DistroSpec.entryForAbi("amd64"));
    }

    @Test
    public void entryForAbi_rejectsUnknown() {
        try {
            DistroSpec.entryForAbi("riscv64");
            fail("expected DistroException for unknown os abi");
        } catch (DistroException expected) {
            // acceptable
        }
    }

    @Test
    public void arm64_pinMatchesCanonicalSha256() {
        DistroSpec.Entry entry = DistroSpec.ARM64;
        assertEquals("ubuntu", DistroSpec.NAME);
        assertEquals("24.04.5", DistroSpec.VERSION);
        assertEquals("noble", DistroSpec.CODENAME);
        assertEquals(
                "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/"
                        + "ubuntu-base-24.04.5-base-arm64.tar.gz",
                entry.url);
        assertEquals("a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2", entry.sha256);
        assertEquals(29936675L, entry.size);
    }

    @Test
    public void amd64_pinMatchesCanonicalSha256() {
        DistroSpec.Entry entry = DistroSpec.AMD64;
        assertEquals(
                "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/"
                        + "ubuntu-base-24.04.5-base-amd64.tar.gz",
                entry.url);
        assertEquals("e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86", entry.sha256);
        assertEquals(30028293L, entry.size);
    }
}
