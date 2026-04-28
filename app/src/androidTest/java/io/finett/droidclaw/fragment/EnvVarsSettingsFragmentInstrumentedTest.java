package io.finett.droidclaw.fragment;

import static androidx.navigation.Navigation.setViewNavController;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.navigation.testing.TestNavHostController;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.R;
import io.finett.droidclaw.util.SettingsManager;

@RunWith(AndroidJUnit4.class)
public class EnvVarsSettingsFragmentInstrumentedTest {

    private static final String PREFS_NAME = "droidclaw_settings";

    @Before
    public void setUp() {
        // Clear preferences before each test for a clean state
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @Test
    public void launch_displaysRecyclerViewAndFab() {
        try (FragmentScenario<EnvVarsSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(
                             EnvVarsSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                RecyclerView recyclerView = view.findViewById(R.id.recycler_env_vars);
                assertNotNull("RecyclerView should be present", recyclerView);

                FloatingActionButton fab = view.findViewById(R.id.fab_add_env_var);
                assertNotNull("FAB should be present", fab);
                assertEquals("FAB should be visible", View.VISIBLE, fab.getVisibility());
            });
        }
    }

    @Test
    public void launch_withNoVars_showsEmptyState() {
        try (FragmentScenario<EnvVarsSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(
                             EnvVarsSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                View emptyState = view.findViewById(R.id.text_empty_state);
                assertNotNull("Empty state view should be present", emptyState);
                assertEquals("Empty state should be visible when no vars set",
                        View.VISIBLE, emptyState.getVisibility());

                RecyclerView recyclerView = view.findViewById(R.id.recycler_env_vars);
                assertEquals("RecyclerView should be hidden when no vars set",
                        View.GONE, recyclerView.getVisibility());
            });
        }
    }

    @Test
    public void launch_withPreexistingVar_showsInList() {
        // Pre-populate a var before launching the fragment
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        settingsManager.setEnvVar("SEARXNG_URL", "https://searx.example.com");

        try (FragmentScenario<EnvVarsSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(
                             EnvVarsSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                View view = fragment.requireView();

                RecyclerView recyclerView = view.findViewById(R.id.recycler_env_vars);
                assertEquals("RecyclerView should be visible when vars exist",
                        View.VISIBLE, recyclerView.getVisibility());

                assertNotNull("Adapter should be present", recyclerView.getAdapter());
                assertEquals("Adapter should show 1 item", 1,
                        recyclerView.getAdapter().getItemCount());

                View emptyState = view.findViewById(R.id.text_empty_state);
                assertEquals("Empty state should be hidden when vars exist",
                        View.GONE, emptyState.getVisibility());
            });
        }
    }

    @Test
    public void launch_withMultipleVars_showsAllInList() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        settingsManager.setEnvVar("SEARXNG_URL", "https://searx.example.com");
        settingsManager.setEnvVar("CUSTOM_VAR", "custom_value");
        settingsManager.setEnvVar("API_TOKEN", "secret123");

        try (FragmentScenario<EnvVarsSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(
                             EnvVarsSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                RecyclerView recyclerView = fragment.requireView()
                        .findViewById(R.id.recycler_env_vars);

                assertNotNull("Adapter should not be null", recyclerView.getAdapter());
                assertEquals("Should show all 3 env vars", 3,
                        recyclerView.getAdapter().getItemCount());
            });
        }
    }

    @Test
    public void fabClick_doesNotCrash() {
        try (FragmentScenario<EnvVarsSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(
                             EnvVarsSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                FloatingActionButton fab = fragment.requireView()
                        .findViewById(R.id.fab_add_env_var);
                assertNotNull(fab);
                // Clicking the FAB should open a dialog without crashing
                fab.performClick();
                // Verify fragment is still alive
                assertNotNull("Fragment should still be attached", fragment.requireView());
            });
        }
    }

    @Test
    public void recyclerView_hasLinearLayoutManager() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        settingsManager.setEnvVar("TEST_KEY", "test_value");

        try (FragmentScenario<EnvVarsSettingsFragment> scenario =
                     FragmentScenario.launchInContainer(
                             EnvVarsSettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                RecyclerView recyclerView = fragment.requireView()
                        .findViewById(R.id.recycler_env_vars);
                assertNotNull("LayoutManager should be set",
                        recyclerView.getLayoutManager());
            });
        }
    }

    @Test
    public void settingsManager_setAndRemoveEnvVar_persistsCorrectly() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        settingsManager.setEnvVar("SEARXNG_URL", "https://searx.example.com");
        settingsManager.setEnvVar("OTHER_KEY", "other_value");

        assertEquals("Should have 2 env vars", 2, settingsManager.getAllEnvVars().size());
        assertEquals("SEARXNG_URL value should match",
                "https://searx.example.com", settingsManager.getEnvVar("SEARXNG_URL"));

        settingsManager.removeEnvVar("OTHER_KEY");
        assertEquals("Should have 1 env var after removal", 1,
                settingsManager.getAllEnvVars().size());
        assertNull("Removed key should return null", settingsManager.getEnvVar("OTHER_KEY"));
    }

    @Test
    public void settingsManager_updateExistingVar_overwritesValue() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        settingsManager.setEnvVar("SEARXNG_URL", "https://old.example.com");
        settingsManager.setEnvVar("SEARXNG_URL", "https://new.example.com");

        assertEquals("Value should be updated", "https://new.example.com",
                settingsManager.getEnvVar("SEARXNG_URL"));
        assertEquals("Should still have only 1 env var", 1,
                settingsManager.getAllEnvVars().size());
    }

    @Test
    public void settingsManager_persistsAcrossInstances() {
        SettingsManager sm1 = new SettingsManager(getApplicationContext());
        sm1.setEnvVar("PERSIST_KEY", "persist_value");

        // Create a new instance simulating app restart
        SettingsManager sm2 = new SettingsManager(getApplicationContext());
        assertEquals("Env var should persist across SettingsManager instances",
                "persist_value", sm2.getEnvVar("PERSIST_KEY"));
    }

    private void assertNull(String message, Object value) {
        if (value != null) {
            throw new AssertionError(message + ": expected null but was " + value);
        }
    }
}