package io.finett.droidclaw.shell.distro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link DistroVerifier}: streaming SHA-256 hashing and the
 * match/mismatch decision used before unpacking.
 */
public class DistroVerifierTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void hash_matchesKnownVector() throws Exception {
        // SHA-256 of "abc", a well-known test vector.
        File f = tmp.newFile("abc");
        Files.write(f.toPath(), "abc".getBytes(StandardCharsets.UTF_8));

        String hash = DistroVerifier.hash(f);

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
    }

    @Test
    public void verify_acceptsMatchingDigest() throws Exception {
        File f = tmp.newFile("ok");
        Files.write(f.toPath(), "hello world".getBytes(StandardCharsets.UTF_8));
        String hash = DistroVerifier.hash(f);

        new DistroVerifier().verify(f, hash); // should not raise
    }

    @Test
    public void verify_acceptsUppercaseExpectedDigest() throws Exception {
        File f = tmp.newFile("ok-upper");
        Files.write(f.toPath(), "hello world".getBytes(StandardCharsets.UTF_8));
        String hash = DistroVerifier.hash(f).toUpperCase();

        new DistroVerifier().verify(f, hash); // case-insensitive
    }

    @Test
    public void verify_rejectsMismatchedDigest() throws Exception {
        File f = tmp.newFile("bad");
        Files.write(f.toPath(), "hello world".getBytes(StandardCharsets.UTF_8));

        try {
            new DistroVerifier().verify(f, "deadbeef");
            fail("expected DistroException on digest mismatch");
        } catch (DistroException expected) {
            assertTrue(expected.getMessage().contains("SHA-256 mismatch"));
        }
    }

    @Test
    public void verify_rejectsMissingFile() throws Exception {
        try {
            new DistroVerifier().verify(new File("/no/such/tarball.tar.gz"), "abc");
            fail("expected DistroException for missing file");
        } catch (DistroException expected) {
            assertTrue(expected.getMessage().contains("missing"));
        }
    }
}
