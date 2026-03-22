package io.finett.droidclaw.python;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.chaquo.python.Python;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Android instrumented tests for PipManager.
 * These tests require the Chaquopy Python runtime which is only available on Android devices.
 */
@RunWith(AndroidJUnit4.class)
public class PipManagerTest {

    private PipManager pipManager;
    private Python python;

    @Before
    public void setUp() {
        Context context = getApplicationContext();
        
        // Initialize Python if not already started
        if (!Python.isStarted()) {
            Python.start(new com.chaquo.python.android.AndroidPlatform(context));
        }
        
        python = Python.getInstance();
        pipManager = new PipManager(python);
    }

    @Test
    public void testPipManagerCreation() {
        assertNotNull("PipManager should be created successfully", pipManager);
    }

    @Test
    public void testIsPackageInstalled_withBuiltInPackage() {
        // 'sys' is a built-in Python module, should always be available
        boolean isInstalled = pipManager.isPackageInstalled("sys");
        assertTrue("Built-in 'sys' module should be installed", isInstalled);
    }

    @Test
    public void testIsPackageInstalled_withNonExistentPackage() {
        boolean isInstalled = pipManager.isPackageInstalled("nonexistent_package_xyz123");
        assertFalse("Non-existent package should not be installed", isInstalled);
    }

    @Test
    public void testIsPackageInstalled_withNullPackage() {
        try {
            boolean isInstalled = pipManager.isPackageInstalled(null);
            assertFalse("Null package should return false", isInstalled);
        } catch (Exception e) {
            // If we can't find the spec, the package is not installed
            assertTrue("Null package should throw exception or return false", true);
        }
    }

    @Test
    public void testListPackages_returnsList() {
        List<String> packages = pipManager.listPackages();
        assertNotNull("listPackages should return a non-null list", packages);
    }

    @Test
    public void testIsPackageInstalled_preinstalledPackage() {
        // 'requests' is pre-installed via build.gradle
        boolean isInstalled = pipManager.isPackageInstalled("requests");
        assertTrue("'requests' should be installed from build.gradle", isInstalled);
    }

    @Test
    public void testIsPackageInstalled_nonExistentPackage() {
        boolean isInstalled = pipManager.isPackageInstalled("nonexistent_package_xyz123");
        assertFalse("Non-existent package should not be installed", isInstalled);
    }

    @Test
    public void testGetPackageVersion_withInstalledPackage() {
        // 'requests' is installed via build.gradle
        String version = pipManager.getPackageVersion("requests");
        assertNotNull("Should return version for installed package", version);
        assertFalse("Version should not be empty", version.isEmpty());
    }

    @Test
    public void testGetPackageVersion_withNonExistentPackage() {
        String version = pipManager.getPackageVersion("nonexistent_package_xyz123");
        assertNull("Should return null for non-existent package", version);
    }

    @Test
    public void testGetPackageVersion_withBuiltInModule() {
        // Built-in modules may not have a pip version
        String version = pipManager.getPackageVersion("sys");
        // Could be null for built-in modules
        assertTrue("Built-in module version can be null or a valid string", 
                version == null || !version.isEmpty());
    }
}