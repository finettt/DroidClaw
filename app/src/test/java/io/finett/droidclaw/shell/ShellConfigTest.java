package io.finett.droidclaw.shell;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ShellConfig} after the DIRECT/allowlist machinery was dropped:
 * the single DENY gate in {@link #validatePlan(ExecPlan)} plus the shell-approval MODE.
 */
public class ShellConfigTest {

    /** Standard bin dirs searched at test time so the tests run on Android and Linux CI. */
    private static final List<String> BIN_DIRS = Arrays.asList(
            "/system/bin", "/system/xbin", "/usr/bin", "/bin", "/usr/sbin", "/sbin");

    private static File tmpDir() {
        return new File(System.getProperty("java.io.tmpdir"));
    }

    private static ExecPlan plan(String rawCommand) {
        return new ExecPlan(rawCommand, tmpDir());
    }

    @Test
    public void createDefault_deniesAllExecution() {
        ShellConfig config = ShellConfig.createDefault();

        assertEquals(30, config.getTimeoutSeconds());
        assertEquals(1024 * 1024, config.getMaxOutputSize());
        assertEquals(ExecPolicy.SecurityLevel.DENY, config.getPolicy().getSecurity());
        assertEquals(ExecPolicy.AskMode.OFF, config.getPolicy().getAsk());

        String denial = config.validatePlan(plan("ls -l"));
        assertNotNull(denial);
        assertTrue(denial.contains("DENY"));
    }

    @Test
    public void createFull_usesFullPolicyAndAlwaysAsk() {
        ShellConfig config = ShellConfig.createFull();

        assertEquals(ExecPolicy.SecurityLevel.FULL, config.getPolicy().getSecurity());
        assertEquals(ExecPolicy.AskMode.ALWAYS, config.getPolicy().getAsk());

        // FULL policy allows everything; only DENY is rejected.
        assertNull(config.validatePlan(plan("rm -rf /")));
    }

    @Test
    public void builder_timeoutSeconds_setsTimeout() {
        ShellConfig config = new ShellConfig.Builder().timeoutSeconds(60).build();
        assertEquals(60, config.getTimeoutSeconds());
    }

    @Test
    public void builder_invalidTimeout_throws() {
        try {
            new ShellConfig.Builder().timeoutSeconds(0).build();
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("positive"));
        }
    }

    @Test
    public void builder_invalidMaxOutputSize_throws() {
        try {
            new ShellConfig.Builder().maxOutputSize(0).build();
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("positive"));
        }
    }

    @Test
    public void validatePlan_fullPolicy_allowsAllCommands() {
        ShellConfig config = ShellConfig.createFull();
        assertNull(config.validatePlan(plan("ls -l")));
    }

    @Test
    public void validatePlan_deniedPolicy_isDenied() {
        ShellConfig config = ShellConfig.createDefault();
        String denial = config.validatePlan(plan("rm -rf /"));
        assertNotNull(denial);
        assertTrue(denial.contains("DENY"));
    }

    @Test
    public void shellApprovalMode_defaultIsAskAll() {
        ShellConfig config = ShellConfig.createDefault();
        assertEquals("ask_all", config.getShellApprovalMode());
    }

    @Test
    public void shellApprovalMode_builderOverridesDefault() {
        ShellConfig config = new ShellConfig.Builder()
                .policy(ExecPolicy.full())
                .shellApprovalMode("full_access")
                .build();
        assertEquals("full_access", config.getShellApprovalMode());
    }

    @Test
    public void shellApprovalMode_defaultBuilderValueIsAskAll() {
        ShellConfig config = new ShellConfig.Builder()
                .policy(ExecPolicy.deny())
                .build();
        assertEquals("ask_all", config.getShellApprovalMode());
    }
}
