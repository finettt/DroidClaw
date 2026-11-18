package io.finett.droidclaw.shell.distro;

/**
 * Raised when a Linux distro bootstrap step fails.
 *
 * <p>The message is intended to be actionable for the user: it states what went
 * wrong and, where possible, what to do about it (free disk, check the network,
 * raise the limit). Bootstrap code treats any {@link DistroException} as a
 * terminal failure that aborts the run and leaves the partial install removed.
 */
public class DistroException extends Exception {

    public DistroException(String message) {
        super(message);
    }

    public DistroException(String message, Throwable cause) {
        super(message, cause);
    }
}
