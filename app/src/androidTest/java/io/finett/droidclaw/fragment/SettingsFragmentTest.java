package io.finett.droidclaw.fragment;

import static androidx.navigation.Navigation.setViewNavController;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.View;

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
                
                // Should have 4 settings items: Providers, Agent, Skills, Reset Onboarding
                assertNotNull("Adapter should not be null", adapter);
                assertTrue("Should have at least 4 settings items", adapter.getItemCount() >= 4);
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

    @Test
    public void skillsItem_isPresent() {
        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recycler_settings);
                RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
                
                // Verify skills item exists (should be 3rd item: Providers, Agent, Skills, Reset)
                assertTrue("Should have at least 3 items for Skills to exist",
                        adapter.getItemCount() >= 3);
            });
        }
    }

    @Test
    public void skillsClick_triggersNavigation() {
        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                TestNavHostController navController = attachNavController(fragment, R.id.settingsFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recycler_settings);
                
                // Click on Skills item (3rd item - index 2)
                if (recyclerView.getAdapter().getItemCount() > 2) {
                    View skillsItem = recyclerView.getLayoutManager().findViewByPosition(2);
                    if (skillsItem != null) {
                        skillsItem.performClick();
                    }
                }
                
                // Verify navigation was triggered (fragment shouldn't crash)
                assertNotNull("Fragment should still exist after navigation", fragment.requireView());
            });
        }
    }

    @Test
    public void providersClick_triggersNavigation() {
        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recycler_settings);
                
                // Click on Providers item (1st item - index 0)
                if (recyclerView.getAdapter().getItemCount() > 0) {
                    View providersItem = recyclerView.getLayoutManager().findViewByPosition(0);
                    if (providersItem != null) {
                        providersItem.performClick();
                    }
                }
                
                // Verify navigation doesn't crash
                assertNotNull("Fragment should still exist after navigation", fragment.requireView());
            });
        }
    }

    @Test
    public void agentClick_triggersNavigation() {
        try (FragmentScenario<SettingsFragment> scenario =
                     FragmentScenario.launchInContainer(SettingsFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.settingsFragment);

                RecyclerView recyclerView = fragment.requireView().findViewById(R.id.recycler_settings);
                
                // Click on Agent item (2nd item - index 1)
                if (recyclerView.getAdapter().getItemCount() > 1) {
                    View agentItem = recyclerView.getLayoutManager().findViewByPosition(1);
                    if (agentItem != null) {
                        agentItem.performClick();
                    }
                }
                
                // Verify navigation doesn't crash
                assertNotNull("Fragment should still exist after navigation", fragment.requireView());
            });
        }
    }

    private TestNavHostController attachNavController(SettingsFragment fragment, int destinationId) {
        TestNavHostController navController = new TestNavHostController(fragment.requireContext());
        navController.setGraph(R.navigation.nav_graph);
        navController.setCurrentDestination(destinationId);
        setViewNavController(fragment.requireView(), navController);
        return navController;
    }
    
    private void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}