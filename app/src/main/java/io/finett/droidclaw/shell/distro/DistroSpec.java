package io.finett.droidclaw.shell.distro;

import android.content.Context;
import android.os.Build;

/**
 * The pinned Linux distribution that the bootstrap installs.
 *
 * <p>A single base image is chosen per device ABI from
 * {@link Build#SUPPORTED_ABIS}. The android ABI is mapped to the Ubuntu base
 * image ABI ({@code arm64-v8a} -> {@code arm64}, {@code x86_64} -> {@code amd64});
 * anything else is rejected with a {@link DistroException} because no image is
 * published for it.
 */
public final class DistroSpec {

    private static final String TAG = "DistroSpec";

    /** Display name of the distribution. */
    public static final String NAME = "ubuntu";
    /** Pinned release version. */
    public static final String VERSION = "24.04.5";
    /** Ubuntu codename for this release, used only when writing a fallback sources file. */
    public static final String CODENAME = "noble";

    /** Directory the image is unpacked into, under {@code filesDir/distro}. */
    public static final String IMAGE_DIR = "ubuntu";
    /** Name of the downloaded tarball, kept next to the unpacked image. */
    public static final String TARBALL_NAME = "ubuntu.tar.gz";
    /** Marker file written on success; its presence means the image is ready. */
    public static final String MARKER_FILE = ".droidclaw-ready";

    private static final String RELEASE_URL =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/";

    /** One downloadable base image: the tarball URL, its expected SHA-256, and its size. */
    public static final class Entry {
        public final String url;
        public final String sha256;
        public final long size;

        public Entry(String url, String sha256, long size) {
            this.url = url;
            this.sha256 = sha256;
            this.size = size;
        }
    }

    /** Ubuntu base image for the arm64 ABI. */
    public static final Entry ARM64 = new Entry(
            RELEASE_URL + "ubuntu-base-24.04.5-base-arm64.tar.gz",
            "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2",
            29936675L);

    /** Ubuntu base image for the amd64 ABI. */
    public static final Entry AMD64 = new Entry(
            RELEASE_URL + "ubuntu-base-24.04.5-base-amd64.tar.gz",
            "e77b6f10c2590cef872b33ee9f635a0e3fd1f57fb074c0e52b5c7f56147a0c86",
            30028293L);

    private DistroSpec() {
    }

    /**
     * Map an android ABI to the Ubuntu base image ABI.
     *
     * @param androidAbi a single android ABI, e.g. {@code arm64-v8a} or {@code x86_64}
     * @return {@code "arm64"} or {@code "amd64"}
     * @throws DistroException when no base image is published for this ABI
     */
    public static String osAbi(String androidAbi) throws DistroException {
        if (androidAbi == null) {
            throw new DistroException("No android ABI reported by the device");
        }
        switch (androidAbi) {
            case "arm64-v8a":
            case "aarch64":
                return "arm64";
            case "x86_64":
            case "amd64":
                return "amd64";
            default:
                throw new DistroException(
                        "No Ubuntu base image for ABI " + androidAbi
                                + "; supported abis are arm64-v8a and x86_64");
        }
    }

    /**
     * Resolve the base image entry for an ABI.
     *
     * @param osAbi {@code "arm64"} or {@code "amd64"}
     */
    public static Entry entryForAbi(String osAbi) throws DistroException {
        if ("arm64".equals(osAbi)) {
            return ARM64;
        }
        if ("amd64".equals(osAbi)) {
            return AMD64;
        }
        throw new DistroException("No Ubuntu base image for ABI " + osAbi);
    }

    /**
     * Resolve the base image for the device's first supported ABI.
     *
     * @param context any application context
     * @throws DistroException when no supported ABI is reported, or no image exists for it
     */
    public static Entry detect(Context context) throws DistroException {
        String[] abis = Build.SUPPORTED_ABIS;
        if (abis == null || abis.length == 0) {
            throw new DistroException("No supported ABIS reported by the device");
        }
        return entryForAbi(osAbi(abis[0]));
    }

    @Override
    public String toString() {
        return "DistroSpec{name=" + NAME + ", version=" + VERSION + "}";
    }
}
