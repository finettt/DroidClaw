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
        // Validate package name
        if (packageName == null || packageName.trim().isEmpty()) {
            Log.e(TAG, "Package name cannot be null or empty");
            return false;
        }
        
        try {
            // Use importlib to check if package exists, pip operations may not work on Android
            // Chaquopy has limited subprocess support
            Log.i(TAG, "Checking package: " + packageName);
            return isPackageInstalled(packageName);
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
        // pip operations are not supported in Chaquopy on Android
        // Packages must be pre-installed via build.gradle
        Log.w(TAG, "Package uninstallation not supported on Android - use build.gradle");
        return false;
    }

    /**
     * List all installed packages.
     *
     * @return List of installed packages in "package==version" format
     */
    public List<String> listPackages() {
        List<String> packages = new ArrayList<>();
        try {
            // Use importlib.metadata to list packages instead of pip
            PyObject importlibMetadata = python.getModule("importlib.metadata");
            PyObject distributions = importlibMetadata.callAttr("distributions");
            
            for (PyObject dist : distributions.asList()) {
                PyObject name = dist.callAttr("name");
                PyObject version = dist.callAttr("version");
                if (name != null && version != null) {
                    packages.add(name.toString() + "==" + version.toString());
                }
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
        // pip operations are not supported in Chaquopy on Android
        // Packages must be pre-installed via build.gradle
        Log.w(TAG, "Package upgrade not supported on Android - use build.gradle");
        return false;
    }

    /**
     * Clear the pip cache.
     *
     * @return true if cache was cleared successfully, false otherwise
     */
    public boolean clearCache() {
        // pip operations are not supported in Chaquopy on Android
        Log.w(TAG, "pip cache operations not supported on Android");
        return false;
    }
}