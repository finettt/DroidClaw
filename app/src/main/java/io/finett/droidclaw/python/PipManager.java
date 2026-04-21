package io.finett.droidclaw.python;

import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.util.ArrayList;
import java.util.List;

public class PipManager {
    private static final String TAG = "PipManager";

    private final Python python;

    public PipManager(Python python) {
        this.python = python;
    }

    public boolean installPackage(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            Log.e(TAG, "Package name cannot be null or empty");
            return false;
        }
        
        try {
            // Chaquopy has limited subprocess support; fall back to checking if the package is importable
            Log.i(TAG, "Checking package: " + packageName);
            return isPackageInstalled(packageName);
        } catch (Exception e) {
            Log.e(TAG, "Error installing package " + packageName, e);
            return false;
        }
    }

    public boolean installPackage(String packageName, String version) {
        String packageSpec = packageName + version;
        return installPackage(packageSpec);
    }

    public boolean uninstallPackage(String packageName) {
        // Chaquopy does not support pip uninstall; packages must be managed via build.gradle
        Log.w(TAG, "Package uninstallation not supported on Android - use build.gradle");
        return false;
    }

    public List<String> listPackages() {
        List<String> packages = new ArrayList<>();
        try {
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

    public boolean isPackageInstalled(String packageName) {
        try {
            PyObject importlib = python.getModule("importlib.util");
            PyObject spec = importlib.callAttr("find_spec", packageName);
            return spec != null;
        } catch (Exception e) {
            return false;
        }
    }

    public String getPackageVersion(String packageName) {
        try {
            PyObject importlib = python.getModule("importlib.metadata");
            PyObject version = importlib.callAttr("version", packageName);
            return version.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean upgradePackage(String packageName) {
        // Chaquopy does not support pip upgrade; packages must be managed via build.gradle
        Log.w(TAG, "Package upgrade not supported on Android - use build.gradle");
        return false;
    }

    public boolean clearCache() {
        Log.w(TAG, "pip cache operations not supported on Android");
        return false;
    }
}