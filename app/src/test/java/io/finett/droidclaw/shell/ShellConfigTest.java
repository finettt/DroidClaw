package io.finett.droidclaw.shell;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Unit tests for ShellConfig.
 */
public class ShellConfigTest {

    @Test
    public void testDefaultConfig() {
        ShellConfig config = ShellConfig.createDefault();
        
        assertTrue(config.isEnabled());
        assertEquals(30, config.getTimeoutSeconds());
        assertEquals(1024 * 1024, config.getMaxOutputSize());
        assertFalse(config.isRequireApproval());
        assertNotNull(config.getBlockedCommands());
        assertFalse(config.getBlockedCommands().isEmpty());
        assertTrue(config.getAllowedCommands().isEmpty());
    }

    @Test
    public void testBuilderTimeout() {
        ShellConfig config = new ShellConfig.Builder()
                .timeoutSeconds(60)
                .build();
        
        assertEquals(60, config.getTimeoutSeconds());
    }

    @Test
    public void testBuilderInvalidTimeout() {
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
    public void testBuilderNegativeTimeout() {
        try {
            new ShellConfig.Builder()
                    .timeoutSeconds(-5)
                    .build();
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("positive"));
        }
    }

    @Test
    public void testBuilderEnabled() {
        ShellConfig config = new ShellConfig.Builder()
                .enabled(false)
                .build();
        
        assertFalse(config.isEnabled());
    }

    @Test
    public void testBuilderRequireApproval() {
        ShellConfig config = new ShellConfig.Builder()
                .requireApproval(true)
                .build();
        
        assertTrue(config.isRequireApproval());
    }

    @Test
    public void testAddBlockedCommand() {
        ShellConfig config = new ShellConfig.Builder()
                .addBlockedCommand("rm")
                .build();
        
        assertTrue(config.getBlockedCommands().contains("rm"));
    }

    @Test
    public void testAddBlockedCommands() {
        Set<String> blocked = new HashSet<>(Arrays.asList("rm", "mkfs", "dd"));
        ShellConfig config = new ShellConfig.Builder()
                .addBlockedCommands(blocked)
                .build();
        
        assertEquals(3, config.getBlockedCommands().size());
        assertTrue(config.getBlockedCommands().contains("rm"));
        assertTrue(config.getBlockedCommands().contains("mkfs"));
        assertTrue(config.getBlockedCommands().contains("dd"));
    }

    @Test
    public void testAddAllowedCommand() {
        ShellConfig config = new ShellConfig.Builder()
                .addAllowedCommand("echo")
                .build();
        
        assertTrue(config.getAllowedCommands().contains("echo"));
    }

    @Test
    public void testAddAllowedCommands() {
        Set<String> allowed = new HashSet<>(Arrays.asList("echo", "pwd", "ls"));
        ShellConfig config = new ShellConfig.Builder()
                .addAllowedCommands(allowed)
                .build();
        
        assertEquals(3, config.getAllowedCommands().size());
    }

    @Test
    public void testIsCommandAllowed_AllowedWhenNoRestrictions() {
        ShellConfig config = ShellConfig.createDefault();
        
        assertTrue(config.isCommandAllowed("echo test"));
        assertTrue(config.isCommandAllowed("ls -la"));
        assertTrue(config.isCommandAllowed("pwd"));
    }

    @Test
    public void testIsCommandAllowed_BlockedCommand() {
        ShellConfig config = new ShellConfig.Builder()
                .addBlockedCommand("rm -rf")
                .enabled(true)
                .build();
        
        assertFalse(config.isCommandAllowed("rm -rf /"));
        assertFalse(config.isCommandAllowed("rm -rf /home"));
    }

    @Test
    public void testIsCommandAllowed_BlockedCommandByFirstToken() {
        ShellConfig config = new ShellConfig.Builder()
                .addBlockedCommand("rm")
                .enabled(true)
                .build();
        
        assertFalse(config.isCommandAllowed("rm -rf /"));
        assertFalse(config.isCommandAllowed("rm -f file.txt"));
    }

    @Test
    public void testIsCommandAllowed_WhitelistOnly() {
        ShellConfig config = new ShellConfig.Builder()
                .addAllowedCommand("echo")
                .addAllowedCommand("pwd")
                .enabled(true)
                .build();
        
        assertTrue(config.isCommandAllowed("echo test"));
        assertTrue(config.isCommandAllowed("pwd"));
        assertFalse(config.isCommandAllowed("ls"));
        assertFalse(config.isCommandAllowed("cat file.txt"));
    }

    @Test
    public void testIsCommandAllowed_BothWhitelistAndBlocklist() {
        ShellConfig config = new ShellConfig.Builder()
                .addAllowedCommand("echo")
                .addAllowedCommand("rm")
                .addBlockedCommand("rm -rf /")
                .enabled(true)
                .build();
        
        assertTrue(config.isCommandAllowed("echo test"));
        assertTrue(config.isCommandAllowed("rm file.txt"));
        assertFalse(config.isCommandAllowed("rm -rf /"));
        assertFalse(config.isCommandAllowed("ls"));
    }

    @Test
    public void testIsCommandAllowed_Disabled() {
        ShellConfig config = new ShellConfig.Builder()
                .enabled(false)
                .build();
        
        assertFalse(config.isCommandAllowed("echo test"));
    }

    @Test
    public void testDefaultBlockedCommands() {
        ShellConfig config = ShellConfig.createDefault();
        
        // Check some of the dangerous commands are blocked
        assertTrue(config.getBlockedCommands().contains("rm -rf /"));
        assertTrue(config.getBlockedCommands().contains("mkfs"));
    }

    @Test
    public void testBlockedCommandsAreImmutable() {
        ShellConfig config = ShellConfig.createDefault();
        Set<String> blocked = config.getBlockedCommands();
        
        try {
            blocked.add("new_command");
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected - sets should be unmodifiable
        }
    }

    @Test
    public void testAllowedCommandsAreImmutable() {
        ShellConfig config = new ShellConfig.Builder()
                .addAllowedCommand("echo")
                .build();
        
        Set<String> allowed = config.getAllowedCommands();
        
        try {
            allowed.add("ls");
            fail("Should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected - sets should be unmodifiable
        }
    }

    @Test
    public void testCommandWithWhitespace() {
        ShellConfig config = new ShellConfig.Builder()
                .addBlockedCommand("rm -rf")
                .enabled(true)
                .build();
        
        // Command with extra whitespace should still be blocked
        assertFalse(config.isCommandAllowed("   rm -rf /   "));
    }

    @Test
    public void testMaxOutputSize() {
        ShellConfig config = new ShellConfig.Builder()
                .maxOutputSize(2048)
                .build();
        
        assertEquals(2048, config.getMaxOutputSize());
    }

    @Test
    public void testBuilderInvalidMaxOutputSize() {
        try {
            new ShellConfig.Builder()
                    .maxOutputSize(0)
                    .build();
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("positive"));
        }
    }
}