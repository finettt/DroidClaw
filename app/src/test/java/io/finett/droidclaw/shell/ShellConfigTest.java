package io.finett.droidclaw.shell;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ShellConfig} after the allowlist/policy hardening rewrite.
 */
public class ShellConfigTest {

    private static String findExe(String name) {
        for (String dir : ShellConfig.DEFAULT_TRUSTED_DIRS) {
            File candidate = new File(dir, name);
            if (candidate.exists() && candidate.canExecute()) {
                return candidate.getAbsolutePath();
            }
        }
        throw new IllegalStateException("Executable not found in trusted dirs: " + name);
    }

    private static ExecPlan plan(String exeName, String... argv) {
        return new ExecPlan(
                findExe(exeName),
                Arrays.asList(argv),
                new File("/tmp"),
                ExecPlan.ExecMode.DIRECT
        );
    }

    private static ExecPlan absolutePlan(String absoluteExePath, String... argv) {
        return new ExecPlan(
                absoluteExePath,
                Arrays.asList(argv),
                new File("/tmp"),
                ExecPlan.ExecMode.DIRECT
        );
    }

    @Test
    public void createDefault_deniesAllExecution() {
        ShellConfig config = ShellConfig.createDefault();

        assertEquals(30, config.getTimeoutSeconds());
        assertEquals(1024 * 1024, config.getMaxOutputSize());
        assertEquals(ExecPolicy.SecurityLevel.DENY, config.getPolicy().getSecurity());
        assertEquals(ExecPolicy.AskMode.OFF, config.getPolicy().getAsk());
        assertEquals(ExecPlan.ExecMode.DIRECT, config.getDefaultMode());

        String denial = config.validatePlan(plan("ls", "-l"));
        assertNotNull(denial);
        assertTrue(denial.contains("DENY"));
    }

    @Test
    public void createFull_usesFullPolicyAndAlwaysAsk() {
        ShellConfig config = ShellConfig.createFull();

        assertEquals(ExecPolicy.SecurityLevel.FULL, config.getPolicy().getSecurity());
        assertEquals(ExecPolicy.AskMode.ALWAYS, config.getPolicy().getAsk());

        String rmPath;
        try {
            rmPath = findExe("rm");
        } catch (IllegalStateException e) {
            rmPath = "/system/bin/rm";
        }
        assertNull(config.validatePlan(absolutePlan(rmPath, "-rf", "/")));
        assertTrue(config.requiresApproval(true));
    }

    @Test
    public void builder_timeoutSeconds_setsTimeout() {
        ShellConfig config = new ShellConfig.Builder()
                .timeoutSeconds(60)
                .build();

        assertEquals(60, config.getTimeoutSeconds());
    }

    @Test
    public void builder_invalidTimeout_throws() {
        try {
            new ShellConfig.Builder()
                    .timeoutSeconds(0)
                    .build();
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("positive"));
        }
    }

    @Test
    public void builder_invalidMaxOutputSize_throws() {
        try {
            new ShellConfig.Builder()
                    .maxOutputSize(0)
                    .build();
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("positive"));
        }
    }

    @Test
    public void validatePlan_fullPolicy_allowsAllCommands() {
        ShellConfig config = ShellConfig.createFull();

        String denial = config.validatePlan(plan("ls", "-l"));

        assertNull(denial);
    }

    @Test
    public void validatePlan_deniedPolicy_isDenied() {
        ShellConfig config = ShellConfig.createDefault();

        String denial = config.validatePlan(plan("rm", "-rf", "/"));

        assertNotNull(denial);
        assertTrue(denial.contains("DENY"));
    }

    @Test
    public void requiresApproval_alwaysAskPolicy_returnsTrue() {
        ShellConfig config = new ShellConfig.Builder()
                .policy(ExecPolicy.allowlistAlwaysAsk())
                .build();

        assertTrue(config.requiresApproval(true));
        assertTrue(config.requiresApproval(false));
    }

    @Test
    public void requiresApproval_onMiss_returnsFalseWhenAllowed_trueWhenDenied() {
        ShellConfig config = new ShellConfig.Builder()
                .policy(ExecPolicy.allowlist())
                .build();

        assertFalse(config.requiresApproval(true));
        assertTrue(config.requiresApproval(false));
    }

    @Test
    public void trustedDirs_areImmutable() {
        ShellConfig config = ShellConfig.createFull();

        try {
            config.getTrustedDirs().add("/tmp");
            fail("trustedDirs should be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void shellApprovalMode_defaultIsAskAll() {
        ShellConfig config = ShellConfig.createDefault();
        assertEquals("ask_all", config.getShellApprovalMode());
    }

    @Test
    public void shellApprovalMode_builderOverridesDefault() {
        ShellConfig config = new ShellConfig.Builder()
                .policy(ExecPolicy.allowlist())
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