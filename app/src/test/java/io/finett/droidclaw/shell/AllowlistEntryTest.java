package io.finett.droidclaw.shell;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link AllowlistEntry} argv policy enforcement.
 */
public class AllowlistEntryTest {

    @Test
    public void matches_correctPath_returnsTrue() {
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/ls").build();
        assertTrue(e.matches("/system/bin/ls"));
    }

    @Test
    public void matches_differentPath_returnsFalse() {
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/ls").build();
        assertFalse(e.matches("/system/bin/rm"));
    }

    @Test
    public void matches_wildcard_matchesAnything() {
        AllowlistEntry e = new AllowlistEntry.Builder("*").build();
        assertTrue(e.matches("/any/path/at/all"));
        assertTrue(e.matches("/system/bin/ls"));
    }

    @Test
    public void validateArgv_noRestrictions_allowsAll() {
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/echo").build();
        assertNull(e.validateArgv(Arrays.asList("hello", "world")));
    }

    @Test
    public void validateArgv_allowedFlag_isPermitted() {
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/ls")
                .allowFlags("-l", "-a")
                .build();
        assertNull(e.validateArgv(Arrays.asList("-l")));
        assertNull(e.validateArgv(Arrays.asList("-a")));
    }

    @Test
    public void validateArgv_unknownFlagWhenAllowlistNonEmpty_isDenied() {
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/ls")
                .allowFlags("-l", "-a")
                .build();
        String denial = e.validateArgv(Arrays.asList("-z"));
        assertNotNull(denial);
        assertTrue(denial.contains("allowlist"));
        assertTrue(denial.contains("-z"));
    }

    @Test
    public void validateArgv_deniedFlag_isRejected() {
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/find")
                .denyFlags("-exec", "-delete")
                .build();

        String denial = e.validateArgv(Arrays.asList(".", "-exec", "rm", "{}", ";"));
        assertNotNull(denial);
        assertTrue(denial.contains("-exec"));
    }

    @Test
    public void validateArgv_deniedFlagWithValueSuffix_isRejected() {
        // e.g. --format=long → strip =long → --format → check against denied
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/ls")
                .denyFlags("--format")
                .build();

        String denial = e.validateArgv(Arrays.asList("--format=long"));
        assertNotNull(denial);
        assertTrue(denial.contains("--format"));
    }

    @Test
    public void validateArgv_inlineEvalFlag_isDeniedWhenDenyInlineEval() {
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/python")
                .denyInlineEval()
                .build();

        String denial = e.validateArgv(Arrays.asList("-c", "import os; os.system('id')"));
        assertNotNull(denial);
        assertTrue(denial.contains("-c"));
    }

    @Test
    public void validateArgv_evalLongForm_isDeniedWhenDenyInlineEval() {
        AllowlistEntry e = new AllowlistEntry.Builder("/usr/bin/node")
                .denyInlineEval()
                .build();

        assertNotNull(e.validateArgv(Arrays.asList("--eval", "process.exit()")));
        assertNotNull(e.validateArgv(Arrays.asList("-e", "console.log('x')")));
    }

    @Test
    public void validateArgv_maxPositionalArgs_enforced() {
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/cat")
                .maxPositionalArgs(1)
                .build();

        assertNull(e.validateArgv(Arrays.asList("file.txt")));

        String denial = e.validateArgv(Arrays.asList("file1.txt", "file2.txt"));
        assertNotNull(denial);
        assertTrue(denial.contains("Too many positional"));
        assertTrue(denial.contains("1"));
    }

    @Test
    public void validateArgv_maxPositionalArgsZero_preventsAnyPositional() {
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/pwd")
                .maxPositionalArgs(0)
                .build();

        assertNull(e.validateArgv(Collections.emptyList()));

        String denial = e.validateArgv(Arrays.asList("anything"));
        assertNotNull(denial);
        assertTrue(denial.contains("Too many positional"));
    }

    @Test
    public void validateArgv_flagsNotCountedAsPositional() {
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/ls")
                .maxPositionalArgs(0)
                .allowFlags("-l", "-a")
                .build();

        // Flags should not be counted as positional args
        assertNull(e.validateArgv(Arrays.asList("-l", "-a")));

        // But actual positional args should be rejected
        String denial = e.validateArgv(Arrays.asList("-l", "/home"));
        assertNotNull(denial);
    }

    @Test
    public void builder_nullExePath_throws() {
        try {
            new AllowlistEntry.Builder(null);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void builder_emptyExePath_throws() {
        try {
            new AllowlistEntry.Builder("");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void allowedFlagsSet_isUnmodifiable() {
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/ls")
                .allowFlags("-l")
                .build();
        try {
            e.getAllowedFlags().add("-z");
            fail("Should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void deniedFlagsSet_isUnmodifiable() {
        AllowlistEntry e = new AllowlistEntry.Builder("/system/bin/find")
                .denyFlags("-exec")
                .build();
        try {
            e.getDeniedFlags().add("-delete");
            fail("Should be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }
}