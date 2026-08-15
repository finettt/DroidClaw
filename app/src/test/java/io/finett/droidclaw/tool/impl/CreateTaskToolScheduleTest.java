package io.finett.droidclaw.tool.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for the schedule grammar accepted by {@link CreateTaskTool}.
 * The scheduler (CronJobScheduler) must treat every weekly format accepted
 * here as a real time-of-day schedule; see CronJobSchedulerTest for the
 * matching side.
 */
public class CreateTaskToolScheduleTest {

    // --- daily@HH:MM ---

    @Test
    public void dailyAtTime_valid() {
        assertTrue(CreateTaskTool.isValidSchedule("daily@08:00"));
        assertTrue(CreateTaskTool.isValidSchedule("daily@8:05"));
        assertTrue(CreateTaskTool.isValidSchedule("daily@23:59"));
    }

    @Test
    public void dailyAtTime_invalid() {
        assertFalse(CreateTaskTool.isValidSchedule("daily@24:00"));
        assertFalse(CreateTaskTool.isValidSchedule("daily@08:60"));
        assertFalse(CreateTaskTool.isValidSchedule("daily@8:5"));
    }

    // --- weekly@day@HH:MM : three-letter codes (agent-facing format) ---

    @Test
    public void weeklyThreeLetterDay_valid() {
        assertTrue(CreateTaskTool.isValidSchedule("weekly@MON@09:00"));
        assertTrue(CreateTaskTool.isValidSchedule("weekly@TUE@09:00"));
        assertTrue(CreateTaskTool.isValidSchedule("weekly@WED@09:00"));
        assertTrue(CreateTaskTool.isValidSchedule("weekly@THU@09:00"));
        assertTrue(CreateTaskTool.isValidSchedule("weekly@FRI@09:00"));
        assertTrue(CreateTaskTool.isValidSchedule("weekly@SAT@09:00"));
        assertTrue(CreateTaskTool.isValidSchedule("weekly@SUN@09:00"));
    }

    @Test
    public void weeklyThreeLetterDay_caseInsensitive() {
        assertTrue(CreateTaskTool.isValidSchedule("weekly@mon@09:00"));
        assertTrue(CreateTaskTool.isValidSchedule("weekly@Fri@18:30"));
    }

    // --- weekly@day@HH:MM : full names (CronJobEditorDialog format) ---

    @Test
    public void weeklyFullName_valid() {
        assertTrue(CreateTaskTool.isValidSchedule("weekly@monday@09:00"));
        assertTrue(CreateTaskTool.isValidSchedule("weekly@sunday@23:59"));
        assertTrue(CreateTaskTool.isValidSchedule("weekly@FRIDAY@18:00"));
    }

    @Test
    public void weekly_invalidDay_rejected() {
        assertFalse(CreateTaskTool.isValidSchedule("weekly@funday@09:00"));
        assertFalse(CreateTaskTool.isValidSchedule("weekly@mon@25:00"));
        assertFalse(CreateTaskTool.isValidSchedule("weekly@monday"));
    }

    // --- other formats unchanged ---

    @Test
    public void plainAndCustomFormats_valid() {
        assertTrue(CreateTaskTool.isValidSchedule("hourly"));
        assertTrue(CreateTaskTool.isValidSchedule("daily"));
        assertTrue(CreateTaskTool.isValidSchedule("weekly"));
        assertTrue(CreateTaskTool.isValidSchedule("every_6_hours"));
        assertTrue(CreateTaskTool.isValidSchedule("3600000"));
    }

    @Test
    public void invalidFormats_rejected() {
        assertFalse(CreateTaskTool.isValidSchedule(null));
        assertFalse(CreateTaskTool.isValidSchedule(""));
        assertFalse(CreateTaskTool.isValidSchedule("whenever"));
        assertFalse(CreateTaskTool.isValidSchedule("every_6_parsecs"));
    }
}
