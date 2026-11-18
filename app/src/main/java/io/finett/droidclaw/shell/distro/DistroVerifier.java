package io.finett.droidclaw.shell.distro;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Verifies the SHA-256 of a downloaded tarball before it is unpacked.
 *
 * <p>The file is hashed in a streaming pass so a 30&nbsp;MB tarball never has to
 * be held in memory. A mismatch is a {@link DistroException}, which the manager
 * turns into a terminal FAILED state.
 */
public final class DistroVerifier {

    private static final String TAG = "DistroVerifier";
    private static final int BUFFER_BYTES = 64 * 1024;

    /**
     * Hash {@code file} and compare it to {@code expected} (case-insensitive hex).
     *
     * @param file         the tarball to hash
     * @param expected     the expected lowercase-or-uppercase hex digest
     * @throws DistroException when the digests differ or the file is missing
     * @throws IOException     on a read failure
     */
    public void verify(File file, String expected) throws DistroException, IOException {
        if (file == null || !file.isFile()) {
            throw new DistroException("Tarball missing: " + file);
        }
        String actual = hash(file);
        if (!equalsIgnoreCase(actual, expected)) {
            throw new DistroException(
                    "SHA-256 mismatch: expected " + expected + " but computed " + actual);
        }
        Log.d(TAG, "Verified SHA-256 for " + file);
    }

    /** Return the lowercase hex SHA-256 of {@code file}, hashing in a single stream. */
    public static String hash(File file) throws DistroException, IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    private static MessageDigest newDigest() throws DistroException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new DistroException("SHA-256 is not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return b != null && a.equalsIgnoreCase(b);
    }
}
