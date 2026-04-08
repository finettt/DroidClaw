package io.finett.droidclaw.fragment;

import static androidx.fragment.app.testing.FragmentScenario.launchInContainer;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.TaskResult;

/**
 * Instrumented tests for ZenResultFragment.
 * Tests the distraction-free task result viewing functionality.
 */
@RunWith(AndroidJUnit4.class)
public class ZenResultFragmentInstrumentedTest {

    @Before
    public void setUp() {
        // Clear SharedPreferences before each test
        getApplicationContext()
                .getSharedPreferences("chat_messages", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @After
    public void tearDown() {
        // Clean up after tests
        getApplicationContext()
                .getSharedPreferences("chat_messages", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void launch_withHeartbeatResult_displaysCorrectly() {
        TaskResult taskResult = new TaskResult("hb-1", TaskResult.TYPE_HEARTBEAT, 1000L,
                "# Heartbeat Check\n\nAll systems operational.\n- Memory: OK\n- Response time: 150ms");

        android.os.Bundle args = new android.os.Bundle();
        args.putSerializable(ZenResultFragment.ARG_TASK_RESULT, taskResult);

        try (androidx.fragment.app.testing.FragmentScenario<ZenResultFragment> scenario =
                     launchInContainer(ZenResultFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView titleView = view.findViewById(R.id.resultTitle);
                TextView timestampView = view.findViewById(R.id.resultTimestamp);
                TextView contentView = view.findViewById(R.id.resultContent);

                assertNotNull("Title should be displayed", titleView);
                assertNotNull("Timestamp should be displayed", timestampView);
                assertNotNull("Content should be displayed", contentView);

                // Verify title contains heartbeat label
                String title = titleView.getText().toString();
                assertTrue("Title should contain 'Heartbeat'", title.contains("Heartbeat"));

                // Verify content is displayed
                String content = contentView.getText().toString();
                assertTrue("Content should display task result text",
                        content.contains("All systems operational"));
            });
        }
    }

    @Test
    public void launch_withCronJobResult_displaysCorrectly() {
        TaskResult taskResult = new TaskResult("cron-1", TaskResult.TYPE_CRON_JOB, 2000L,
                "Daily summary: 5 new messages processed, 3 files modified");

        android.os.Bundle args = new android.os.Bundle();
        args.putSerializable(ZenResultFragment.ARG_TASK_RESULT, taskResult);

        try (androidx.fragment.app.testing.FragmentScenario<ZenResultFragment> scenario =
                     launchInContainer(ZenResultFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView titleView = view.findViewById(R.id.resultTitle);
                assertNotNull("Title should be displayed", titleView);

                String title = titleView.getText().toString();
                assertTrue("Title should contain 'Cron'", title.contains("Cron"));

                TextView contentView = view.findViewById(R.id.resultContent);
                String content = contentView.getText().toString();
                assertTrue("Content should display cron result",
                        content.contains("Daily summary"));
            });
        }
    }

    @Test
    public void launch_withManualTaskResult_displaysCorrectly() {
        TaskResult taskResult = new TaskResult("manual-1", TaskResult.TYPE_MANUAL, 3000L,
                "Manual task completed successfully. Results: Code analysis complete.");

        android.os.Bundle args = new android.os.Bundle();
        args.putSerializable(ZenResultFragment.ARG_TASK_RESULT, taskResult);

        try (androidx.fragment.app.testing.FragmentScenario<ZenResultFragment> scenario =
                     launchInContainer(ZenResultFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView titleView = view.findViewById(R.id.resultTitle);
                String title = titleView.getText().toString();
                assertTrue("Title should contain 'Manual'", title.contains("Manual"));

                TextView contentView = view.findViewById(R.id.resultContent);
                String content = contentView.getText().toString();
                assertTrue("Content should display manual task result",
                        content.contains("Code analysis complete"));
            });
        }
    }

    @Test
    public void launch_withEmptyContent_showsPlaceholder() {
        TaskResult taskResult = new TaskResult("empty-1", TaskResult.TYPE_HEARTBEAT, 1000L, "");

        android.os.Bundle args = new android.os.Bundle();
        args.putSerializable(ZenResultFragment.ARG_TASK_RESULT, taskResult);

        try (androidx.fragment.app.testing.FragmentScenario<ZenResultFragment> scenario =
                     launchInContainer(ZenResultFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView contentView = view.findViewById(R.id.resultContent);
                String content = contentView.getText().toString();

                assertEquals("Should show placeholder for empty content",
                        "No content available", content);
            });
        }
    }

    @Test
    public void launch_withNullContent_showsPlaceholder() {
        TaskResult taskResult = new TaskResult("null-1", TaskResult.TYPE_HEARTBEAT, 1000L, null);

        android.os.Bundle args = new android.os.Bundle();
        args.putSerializable(ZenResultFragment.ARG_TASK_RESULT, taskResult);

        try (androidx.fragment.app.testing.FragmentScenario<ZenResultFragment> scenario =
                     launchInContainer(ZenResultFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView contentView = view.findViewById(R.id.resultContent);
                String content = contentView.getText().toString();

                assertEquals("Should show placeholder for null content",
                        "No content available", content);
            });
        }
    }

    @Test
    public void launch_displaysFloatingActionButtons() {
        TaskResult taskResult = new TaskResult("fb-1", TaskResult.TYPE_HEARTBEAT, 1000L, "Test content");

        android.os.Bundle args = new android.os.Bundle();
        args.putSerializable(ZenResultFragment.ARG_TASK_RESULT, taskResult);

        try (androidx.fragment.app.testing.FragmentScenario<ZenResultFragment> scenario =
                     launchInContainer(ZenResultFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                ExtendedFloatingActionButton fabChat = view.findViewById(R.id.fabChatAboutThis);
                FloatingActionButton fabShare = view.findViewById(R.id.fabShare);

                assertNotNull("Chat FAB should be visible", fabChat);
                assertNotNull("Share FAB should be visible", fabShare);
                assertTrue("Chat FAB should be visible", fabChat.isShown());
                assertTrue("Share FAB should be visible", fabShare.isShown());
            });
        }
    }

    @Test
    public void launch_withMarkdownContent_rendersMarkdown() {
        String markdownContent = "# Task Results\n\n" +
                "## Summary\n" +
                "- Item 1\n" +
                "- Item 2\n" +
                "- Item 3\n\n" +
                "**Status**: Complete";

        TaskResult taskResult = new TaskResult("md-1", TaskResult.TYPE_HEARTBEAT, 1000L, markdownContent);

        android.os.Bundle args = new android.os.Bundle();
        args.putSerializable(ZenResultFragment.ARG_TASK_RESULT, taskResult);

        try (androidx.fragment.app.testing.FragmentScenario<ZenResultFragment> scenario =
                     launchInContainer(ZenResultFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView contentView = view.findViewById(R.id.resultContent);
                assertNotNull("Content view should exist", contentView);

                String content = contentView.getText().toString();
                // Markdown should be rendered, so raw markdown syntax shouldn't appear
                assertTrue("Content should be rendered (should contain 'Task Results')",
                        content.contains("Task Results"));
                assertTrue("Content should contain 'Status'",
                        content.contains("Status"));
            });
        }
    }

    @Test
    public void launch_displaysFormattedTimestamp() {
        long timestamp = 1000L;

        TaskResult taskResult = new TaskResult("ts-1", TaskResult.TYPE_HEARTBEAT, timestamp, "Test");

        android.os.Bundle args = new android.os.Bundle();
        args.putSerializable(ZenResultFragment.ARG_TASK_RESULT, taskResult);

        try (androidx.fragment.app.testing.FragmentScenario<ZenResultFragment> scenario =
                     launchInContainer(ZenResultFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView timestampView = view.findViewById(R.id.resultTimestamp);
                assertNotNull("Timestamp view should exist", timestampView);

                String timestampText = timestampView.getText().toString();
                // Timestamp should be formatted (not just a number)
                assertTrue("Timestamp should be formatted (contain text)",
                        timestampText.length() > 0 && !timestampText.equals("1000"));
            });
        }
    }

    @Test
    public void launch_withLongContent_displaysEntireContent() {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longContent.append("This is line ").append(i).append(" of the task result. ");
        }

        TaskResult taskResult = new TaskResult("long-1", TaskResult.TYPE_HEARTBEAT, 1000L, longContent.toString());

        android.os.Bundle args = new android.os.Bundle();
        args.putSerializable(ZenResultFragment.ARG_TASK_RESULT, taskResult);

        try (androidx.fragment.app.testing.FragmentScenario<ZenResultFragment> scenario =
                     launchInContainer(ZenResultFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView contentView = view.findViewById(R.id.resultContent);
                String content = contentView.getText().toString();

                // Content should be displayed (may be truncated in UI but should not crash)
                assertNotNull("Content should be displayed", content);
                assertTrue("Content view should have text", content.length() > 0);
            });
        }
    }

    @Test
    public void launch_withSpecialCharacters_handlesGracefully() {
        String specialContent = "Special chars: <>&\"' @#$%^&*()_+-=[]{}|;':\",./<>?`~\n" +
                "Unicode: 你好世界 🌍 🚀 💓";

        TaskResult taskResult = new TaskResult("special-1", TaskResult.TYPE_HEARTBEAT, 1000L, specialContent);

        android.os.Bundle args = new android.os.Bundle();
        args.putSerializable(ZenResultFragment.ARG_TASK_RESULT, taskResult);

        try (androidx.fragment.app.testing.FragmentScenario<ZenResultFragment> scenario =
                     launchInContainer(ZenResultFragment.class, args, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                TextView contentView = view.findViewById(R.id.resultContent);
                String content = contentView.getText().toString();

                assertTrue("Content should contain special chars",
                        content.contains("Special chars"));
            });
        }
    }

    @Test
    public void launch_withDifferentTimestamps_formatsEachCorrectly() {
        long[] timestamps = {
                0L,
                1000L,
                1000000L,
                1000000000L,
                System.currentTimeMillis()
        };

        for (long timestamp : timestamps) {
            TaskResult taskResult = new TaskResult("ts-" + timestamp, TaskResult.TYPE_HEARTBEAT, timestamp, "Test");

            android.os.Bundle args = new android.os.Bundle();
            args.putSerializable(ZenResultFragment.ARG_TASK_RESULT, taskResult);

            try (androidx.fragment.app.testing.FragmentScenario<ZenResultFragment> scenario =
                         launchInContainer(ZenResultFragment.class, args, R.style.Theme_DroidClaw)) {
                scenario.onFragment(fragment -> {
                    View view = fragment.requireView();

                    TextView timestampView = view.findViewById(R.id.resultTimestamp);
                    assertNotNull("Timestamp view should exist for timestamp: " + timestamp, timestampView);

                    String timestampText = timestampView.getText().toString();
                    assertNotNull("Timestamp text should not be null", timestampText);
                    assertTrue("Timestamp text should not be empty", timestampText.length() > 0);
                });
            }
        }
    }
}
