package io.finett.droidclaw.util;

import static org.junit.Assert.*;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.json.JSONObject;

import java.lang.reflect.Method;

/**
 * Unit tests for the shell-approval-mode migration in {@link SettingsManager}:
 * a missing key falls back from the older relaxed/full sandbox modes to
 * {@code ask_all}, and an explicit value is preserved (or defaulted when invalid).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SettingsManagerApprovalMigrationTest {

    private SettingsManager settingsManager;
    private Context context;

    /** Invokes the package-private {@code resolveShellApprovalMode(JSONObject)}. */
    private String resolve(JSONObject agentJson) throws Exception {
        Method m = SettingsManager.class.getDeclaredMethod(
                "resolveShellApprovalMode", JSONObject.class);
        m.setAccessible(true);
        return (String) m.invoke(settingsManager, agentJson);
    }

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        settingsManager = new SettingsManager(context);
    }

    @Test
    public void migration_missingKey_relaxed_fallsBackToAskAll() throws Exception {
        JSONObject json = new JSONObject()
                .put("sandboxMode", "relaxed");
        assertEquals("ask_all", resolve(json));
    }

    @Test
    public void migration_missingKey_full_fallsBackToAskAll() throws Exception {
        JSONObject json = new JSONObject().put("sandboxMode", "full");
        assertEquals("ask_all", resolve(json));
    }

    @Test
    public void migration_missingKey_strict_staysAuto() throws Exception {
        JSONObject json = new JSONObject().put("sandboxMode", "strict");
        assertEquals("auto", resolve(json));
    }

    @Test
    public void migration_omittedSandboxDefaultsToAuto() throws Exception {
        assertEquals("auto", resolve(new JSONObject()));
    }

    @Test
    public void migration_explicitAuto_isPreserved() throws Exception {
        JSONObject json = new JSONObject().put("shellApprovalMode", "auto");
        assertEquals("auto", resolve(json));
    }

    @Test
    public void migration_explicitAskAll_isPreserved() throws Exception {
        JSONObject json = new JSONObject().put("shellApprovalMode", "ask_all");
        assertEquals("ask_all", resolve(json));
    }

    @Test
    public void migration_explicitFullAccess_isPreserved() throws Exception {
        JSONObject json = new JSONObject().put("shellApprovalMode", "full_access");
        assertEquals("full_access", resolve(json));
    }

    @Test
    public void migration_invalidValue_defaultsToAuto() throws Exception {
        JSONObject json = new JSONObject().put("shellApprovalMode", "bogus");
        assertEquals("auto", resolve(json));
    }

    @Test
    public void migration_explicitBeatsRelaxed_sandbox() throws Exception {
        // An explicit value wins over the sandbox-mode fallback.
        JSONObject json = new JSONObject()
                .put("sandboxMode", "relaxed")
                .put("shellApprovalMode", "auto");
        assertEquals("auto", resolve(json));
    }
}
