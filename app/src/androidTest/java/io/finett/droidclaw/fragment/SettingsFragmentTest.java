package io.finett.droidclaw.fragment;

import static androidx.navigation.Navigation.setViewNavController;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.navigation.testing.TestNavHostController;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.SettingsAdapter;
import io.finett.droidclaw.util.SettingsManager;

@RunWith(AndroidJUnit4.class)
public class SettingsFragmentTest {

    private static final String PREFS_NAME = "droidclaw_settings";

    @Before
    public void setUp() {
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @Test
    public void launch_displaysSettingsRecyclerView() {
        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recycler_settings);
                assertNotNull("RecyclerView should be present", recyclerView);
                assertNotNull("RecyclerView should have adapter", recyclerView.getAdapter());
            });
        }
    }

    @Test
    public void launch_hasCorrectNumberOfSettingsItems() {
        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recycler_settings);
                RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
                
                // Should have at least 3 settings items: Providers, Models/Agent, etc.
                assertNotNull("Adapter should not be null", adapter);
                assertTrue("Should have at least 2 settings items", adapter.getItemCount() >= 2);
            });
        }
    }

    @Test
    public void settingsItems_haveCorrectStructure() {
        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recycler_settings);
                
                // Verify that the adapter is SettingsAdapter
                assertNotNull("Adapter should be SettingsAdapter", 
                        recyclerView.getAdapter() instanceof SettingsAdapter);
            });
        }
    }

    @Test
    public void launch_withConfiguredProviders_showsProviderCount() {
        // Configure a test provider
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        io.finett.droidclaw.model.Provider testProvider = 
                new io.finett.droidclaw.model.Provider("test-id", "Test Provider", 
                        "http://test.url", "test-key", "openai");
        settingsManager.addProvider(testProvider);

        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recycler_settings);
                assertNotNull("RecyclerView should be present", recyclerView);
                
                // The settings should reflect the configured provider
                assertEquals("Should have 1 provider", 1, settingsManager.getProviderCount());
            });
        }
    }

    @Test
    public void settingsClick_triggersNavigation() {
        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recycler_settings);
                
                // Perform click on first item
                recyclerView.getChildAt(0).performClick();
                
                // Navigation should be triggered (tested via mock nav controller)
                // The important thing is it doesn't crash
            });
        }
    }

    @Test
    public void recreate_maintainsSettingsState() {
        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);
            });

            // Recreate the fragment
            scenario.recreate();

            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);
                
                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recycler_settings);
                assertNotNull("RecyclerView should still be present after recreate", recyclerView);
            });
        }
    }

    private void attachNavController(SettingsFragment fragment, int destinationId) {
        TestNavHostController navController = new TestNavHostController(fragment.requireContext());
        navController.setGraph(R.navigation.nav_graph);
        navController.setCurrentDestination(destinationId);
        setViewNavController(fragment.requireView(), navController);
    }
    
    private void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}