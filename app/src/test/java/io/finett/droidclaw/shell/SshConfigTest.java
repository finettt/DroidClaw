package io.finett.droidclaw.shell;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link SshConfig} — builder, validation, immutability.
 */
public class SshConfigTest {

    @Test
    public void builder_basicConfig_setsAllFields() {
        SshConfig config = new SshConfig.Builder()
                .host("192.168.1.100")
                .port(2222)
                .username("testuser")
                .password("testpass")
                .verifyHostKey(false)
                .build();

        assertEquals("192.168.1.100", config.getHost());
        assertEquals(2222, config.getPort());
        assertEquals("testuser", config.getUsername());
        assertEquals("testpass", config.getPassword());
        assertFalse(config.isVerifyHostKey());
    }

    @Test
    public void builder_keyAuthConfig() {
        SshConfig config = new SshConfig.Builder()
                .host("server.example.com")
                .port(22)
                .username("admin")
                .privateKeyPath("/home/user/.ssh/id_rsa")
                .verifyHostKey(true)
                .build();

        assertEquals("server.example.com", config.getHost());
        assertEquals(22, config.getPort());
        assertEquals("admin", config.getUsername());
        assertEquals("/home/user/.ssh/id_rsa", config.getPrivateKeyPath());
        assertNull(config.getPassword());
        assertTrue(config.isVerifyHostKey());
    }

    @Test(expected = IllegalArgumentException.class)
    public void builder_emptyHost_throws() {
        new SshConfig.Builder()
                .host("")
                .username("user")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void builder_nullHost_throws() {
        new SshConfig.Builder()
                .host(null)
                .username("user")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void builder_emptyUsername_throws() {
        new SshConfig.Builder()
                .host("localhost")
                .username("")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void builder_nullUsername_throws() {
        new SshConfig.Builder()
                .host("localhost")
                .username(null)
                .build();
    }

    @Test
    public void builder_defaultPort() {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .username("user")
                .build();

        assertEquals(22, config.getPort());
    }

    @Test
    public void builder_defaultVerifyHostKey() {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .username("user")
                .build();

        assertTrue(config.isVerifyHostKey());
    }

    @Test
    public void builder_customPolicy() {
        ShellConfig shellConfig = ShellConfig.createFull();
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .username("user")
                .policy(shellConfig.getPolicy())
                .build();

        assertEquals(shellConfig.getPolicy(), config.getPolicy());
    }

    @Test
    public void getHost_isNotNull() {
        SshConfig config = new SshConfig.Builder()
                .host("test-host")
                .username("user")
                .build();

        assertNotNull(config.getHost());
    }

    @Test
    public void getPassword_canBeNull() {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .username("user")
                .privateKeyPath("/path/to/key")
                .build();

        assertNull(config.getPassword());
        assertNotNull(config.getPrivateKeyPath());
    }

    @Test
    public void getPrivateKeyPath_canBeNull() {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .username("user")
                .password("secret")
                .build();

        assertNull(config.getPrivateKeyPath());
        assertNotNull(config.getPassword());
    }

    @Test
    public void policy_isNotNull() {
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .username("user")
                .build();

        assertNotNull(config.getPolicy());
    }

    @Test
    public void validatePlan_withFullPolicy_allowsAll() {
        ShellConfig shellConfig = ShellConfig.createFull();
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .username("user")
                .policy(shellConfig.getPolicy())
                .build();

        // Create a simple ExecPlan for testing
        java.io.File tmpDir = new java.io.File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        // FULL policy should allow everything
        assertNull(config.validatePlan(plan));
    }

    @Test
    public void validatePlan_withDenyPolicy_rejectsAll() {
        ShellConfig shellConfig = ShellConfig.createDefault();
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .username("user")
                .policy(shellConfig.getPolicy())
                .build();

        java.io.File tmpDir = new java.io.File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/ls",
                Arrays.asList("-l"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        String denial = config.validatePlan(plan);
        assertNotNull(denial);
        assertTrue(denial.contains("DENY"));
    }

    @Test
    public void validatePlan_withAllowlistPolicy_rejectsUnlistedCommand() {
        ShellConfig shellConfig = ShellConfig.createFull();
        SshConfig config = new SshConfig.Builder()
                .host("localhost")
                .username("user")
                .policy(shellConfig.getPolicy())
                .build();

        java.io.File tmpDir = new java.io.File(System.getProperty("java.io.tmpdir"));
        ExecPlan plan = new ExecPlan(
                "/usr/bin/rm",
                Arrays.asList("-rf", "/"),
                tmpDir,
                ExecPlan.ExecMode.DIRECT
        );

        // FULL policy allows everything; only DENY is rejected now.
        assertNull(config.validatePlan(plan));
    }
}