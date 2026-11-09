package io.finett.droidclaw.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class EffortTest {

    @Test
    public void fromString_medAndMediumBothResolveToMedium() {
        assertSame(Effort.MEDIUM, Effort.fromString("med"));
        assertSame(Effort.MEDIUM, Effort.fromString("medium"));
        assertSame(Effort.MEDIUM, Effort.fromString(" MEDIUM "));
    }

    @Test
    public void fromString_resolvesEveryLevel() {
        assertSame(Effort.LOW, Effort.fromString("low"));
        assertSame(Effort.HIGH, Effort.fromString("high"));
        assertSame(Effort.XHIGH, Effort.fromString("xhigh"));
        assertSame(Effort.MAX, Effort.fromString("max"));
    }

    @Test
    public void fromString_unknownValueThrows() {
        try {
            Effort.fromString("superhuman");
            fail("expected IllegalArgumentException for unknown effort");
        } catch (IllegalArgumentException expected) {
            // expected: unknown values fail loudly rather than silently defaulting
        }
    }

    @Test
    public void fromString_nullAndBlankThrow() {
        try {
            Effort.fromString(null);
            fail("expected IllegalArgumentException for null effort");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            Effort.fromString("   ");
            fail("expected IllegalArgumentException for blank effort");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void fromMaxIterations_boundariesResolveCorrectly() {
        assertSame(Effort.LOW, Effort.fromMaxIterations(8));
        assertSame(Effort.MEDIUM, Effort.fromMaxIterations(9));
        assertSame(Effort.MEDIUM, Effort.fromMaxIterations(20));
        assertSame(Effort.HIGH, Effort.fromMaxIterations(21));
        assertSame(Effort.HIGH, Effort.fromMaxIterations(40));
        assertSame(Effort.XHIGH, Effort.fromMaxIterations(41));
        assertSame(Effort.XHIGH, Effort.fromMaxIterations(80));
        assertSame(Effort.MAX, Effort.fromMaxIterations(81));
        assertSame(Effort.MAX, Effort.fromMaxIterations(200));
    }

    @Test
    public void wireNameAndBudget_matchExpectedTokens() {
        assertSame("medium", Effort.MEDIUM.wire());
        assertSame("med", Effort.MEDIUM.persistedName());
        assertEquals(20, Effort.MEDIUM.turnBudget());

        assertSame("high", Effort.HIGH.wire());
        assertSame("low", Effort.LOW.wire());
        assertSame("xhigh", Effort.XHIGH.wire());
        assertSame("max", Effort.MAX.wire());
    }

    @Test
    public void isUnlimited_onlyForMax() {
        assertFalse(Effort.LOW.isUnlimited());
        assertFalse(Effort.MEDIUM.isUnlimited());
        assertFalse(Effort.HIGH.isUnlimited());
        assertFalse(Effort.XHIGH.isUnlimited());
        assertTrue(Effort.MAX.isUnlimited());
        assertEquals(0, Effort.MAX.turnBudget());
    }

    @Test
    public void getDefaultIsMedium() {
        assertSame(Effort.MEDIUM, Effort.getDefault());
    }
}
