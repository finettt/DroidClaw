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
        assertTrue(config.getAllowlist().isEmpty());
        assertEquals(ExecPlan.ExecMode.DIRECT, config.getDefaultMode());

        String denial = config.validatePlan(plan("ls", "-l"));
        assertNotNull(denial);
        assertTrue(denial.contains("DENY"));
    }

    @Test
    public void createAllowlistDefault_usesAllowlistPolicy() {
        ShellConfig config = ShellConfig.createAllowlistDefault();

        assertEquals(ExecPolicy.SecurityLevel.ALLOWLIST, config.getPolicy().getSecurity());
        assertEquals(ExecPolicy.AskMode.ON_MISS, config.getPolicy().getAsk());
        assertFalse(config.getAllowlist().isEmpty());
        assertEquals(ExecPlan.ExecMode.DIRECT, config.getDefaultMode());
        assertTrue(config.getTrustedDirs().contains("/system/bin"));
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
    public void validatePlan_allowlistedCommand_returnsNull() {
        ShellConfig config = ShellConfig.createAllowlistDefault();

        String denial = config.validatePlan(plan("ls", "-l"));

        assertNull(denial);
    }

    @Test
    public void validatePlan_unlistedCommand_isDenied() {
        ShellConfig config = ShellConfig.createAllowlistDefault();

        String denial = config.validatePlan(plan("rm", "-rf", "/"));

        assertNotNull(denial);
        assertTrue(denial.contains("not in allowlist"));
    }

    @Test
    public void validatePlan_deniedFlag_isRejected() {
        ShellConfig config = ShellConfig.createAllowlistDefault();

        String denial = config.validatePlan(plan("find", ".", "-exec", "rm", "{}", ";"));

        assertNotNull(denial);
        assertTrue(denial.contains("denied"));
        assertTrue(denial.contains("-exec"));
    }

    @Test
    public void validatePlan_unknownFlagOnRestrictedCommand_isRejected() {
        ShellConfig config = ShellConfig.createAllowlistDefault();

        String denial = config.validatePlan(plan("head", "-z", "file.txt"));

        assertNotNull(denial);
        assertTrue(denial.contains("allowlist"));
    }

    @Test
    public void validatePlan_positionalArgLimit_isEnforced() {
        ShellConfig config = ShellConfig.createAllowlistDefault();

        String denial = config.validatePlan(plan("cat", "a.txt", "b.txt"));

        assertNotNull(denial);
        assertTrue(denial.contains("Too many positional arguments"));
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
        ShellConfig config = ShellConfig.createAllowlistDefault();

        assertFalse(config.requiresApproval(true));
        assertTrue(config.requiresApproval(false));
    }

    @Test
    public void trustedDirs_areImmutable() {
        ShellConfig config = ShellConfig.createAllowlistDefault();

        try {
            config.getTrustedDirs().add("/tmp");
            fail("trustedDirs should be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void allowlist_isImmutable() {
        ShellConfig config = ShellConfig.createAllowlistDefault();

        try {
            config.getAllowlist().clear();
            fail("allowlist should be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}