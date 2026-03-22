package io.finett.droidclaw.python;

import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager for pip package operations at runtime.
 * Provides methods to install, list, and check Python packages.
 */
public class PipManager {
    private static final String TAG = "PipManager";

    private final Python python;

    /**
     * Creates a PipManager with an initialized Python instance.
     *
     * @param python The Python instance to use for pip operations
     */
    public PipManager(Python python) {
        this.python = python;
    }

    /**
     * Install a package using pip.
     *
     * @param packageName Name of the package to install
     * @return true if installation was successful, false otherwise
     */
    public boolean installPackage(String packageName) {
        try {
            PyObject subprocess = python.getModule("subprocess");
            PyObject result = subprocess.callAttr(
                    "run",
                    new String[]{"pip", "install", packageName},
                    "capture_output", true,
                    "text", true
            );

            int returnCode = result.get("returncode").toInt();
            if (returnCode == 0) {
                Log.i(TAG, "Successfully installed package: " + packageName);
                return true;
            } else {
                String stderr = result.get("stderr").toString();
                Log.e(TAG, "Failed to install package " + packageName + ": " + stderr);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error installing package " + packageName, e);
            return false;
        }
    }

    /**
     * Install a specific version of a package.
     *
     * @param packageName Name of the package
     * @param version     Version specification (e.g., ">=2.0", "==1.5.0")
     * @return true if installation was successful, false otherwise
     */
    public boolean installPackage(String packageName, String version) {
        String packageSpec = packageName + version;
        return installPackage(packageSpec);
    }

    /**
     * Uninstall a package using pip.
     *
     * @param packageName Name of the package to uninstall
     * @return true if uninstallation was successful, false otherwise
     */
    public boolean uninstallPackage(String packageName) {
        try {
            PyObject subprocess = python.getModule("subprocess");
            PyObject result = subprocess.callAttr(
                    "run",
                    new String[]{"pip", "uninstall", "-y", packageName},
                    "capture_output", true,
                    "text", true
            );

            int returnCode = result.get("returncode").toInt();
            if (returnCode == 0) {
                Log.i(TAG, "Successfully uninstalled package: " + packageName);
                return true;
            } else {
                String stderr = result.get("stderr").toString();
                Log.e(TAG, "Failed to uninstall package " + packageName + ": " + stderr);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error uninstalling package " + packageName, e);
            return false;
        }
    }

    /**
     * List all installed packages.
     *
     * @return List of installed packages in "package==version" format
     */
    public List<String> listPackages() {
        List<String> packages = new ArrayList<>();
        try {
            PyObject subprocess = python.getModule("subprocess");
            PyObject result = subprocess.callAttr(
                    "run",
                    new String[]{"pip", "list", "--format=freeze"},
                    "capture_output", true,
                    "text", true
            );

            if (result.get("returncode").toInt() == 0) {
                String output = result.get("stdout").toString();
                for (String line : output.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        packages.add(trimmed);
                    }
                }
            } else {
                String stderr = result.get("stderr").toString();
                Log.e(TAG, "Failed to list packages: " + stderr);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing packages", e);
        }
        return packages;
    }

    /**
     * Check if a package is installed.
     *
     * @param packageName Name of the package to check
     * @return true if the package is installed, false otherwise
     */
    public boolean isPackageInstalled(String packageName) {
        try {
            PyObject importlib = python.getModule("importlib.util");
            PyObject spec = importlib.callAttr("find_spec", packageName);
            return spec != null;
        } catch (Exception e) {
            // If we can't find the spec, the package is not installed
            return false;
        }
    }

    /**
     * Get the version of an installed package.
     *
     * @param packageName Name of the package
     * @return Version string, or null if not installed or version cannot be determined
     */
    public String getPackageVersion(String packageName) {
        try {
            PyObject importlib = python.getModule("importlib.metadata");
            PyObject version = importlib.callAttr("version", packageName);
            return version.toString();
        } catch (Exception e) {
            // Package not found or version not available
            return null;
        }
    }

    /**
     * Upgrade a package to the latest version.
     *
     * @param packageName Name of the package to upgrade
     * @return true if upgrade was successful, false otherwise
     */
    public boolean upgradePackage(String packageName) {
        try {
            PyObject subprocess = python.getModule("subprocess");
            PyObject result = subprocess.callAttr(
                    "run",
                    new String[]{"pip", "install", "--upgrade", packageName},
                    "capture_output", true,
                    "text", true
            );

            int returnCode = result.get("returncode").toInt();
            if (returnCode == 0) {
                Log.i(TAG, "Successfully upgraded package: " + packageName);
                return true;
            } else {
                String stderr = result.get("stderr").toString();
                Log.e(TAG, "Failed to upgrade package " + packageName + ": " + stderr);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error upgrading package " + packageName, e);
            return false;
        }
    }

    /**
     * Clear the pip cache.
     *
     * @return true if cache was cleared successfully, false otherwise
     */
    public boolean clearCache() {
        try {
            PyObject subprocess = python.getModule("subprocess");
            PyObject result = subprocess.callAttr(
                    "run",
                    new String[]{"pip", "cache", "purge"},
                    "capture_output", true,
                    "text", true
            );

            int returnCode = result.get("returncode").toInt();
            if (returnCode == 0) {
                Log.i(TAG, "Successfully cleared pip cache");
                return true;
            } else {
                String stderr = result.get("stderr").toString();
                Log.e(TAG, "Failed to clear pip cache: " + stderr);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing pip cache", e);
            return false;
        }
    }
}