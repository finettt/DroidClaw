package io.finett.droidclaw.shell;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class ProotRuntimeInstrumentedTest {
    private static final String TAG = "ProotRuntimeTest";

    @Test
    public void packagedRuntimeIsAvailableAndReportsVersion() {
        Context context = ApplicationProvider.getApplicationContext();
        String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
        ProotRuntime runtime = new ProotRuntime(context);

        assertTrue("libproot.so must be inside nativeLibraryDir",
                ProotRuntime.locate(context).getParent().equals(nativeLibraryDir));
        assertTrue("PRoot must be available and executable", runtime.isAvailable());
        for (String name : new String[] {"libproot.so", "libtalloc.so", "libandroid-shmem.so"}) {
            assertTrue(name + " must be present in nativeLibraryDir",
                    new File(nativeLibraryDir, name).isFile());
        }

        String version = runtime.version();
        Log.i(TAG, "version() returned: " + version);
        System.out.println(TAG + ": version() returned: " + version);
        assertTrue("version() must identify PRoot: " + version,
                version.toLowerCase(Locale.ROOT).contains("proot"));
    }
}
