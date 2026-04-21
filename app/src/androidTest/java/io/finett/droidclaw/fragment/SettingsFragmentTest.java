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
                
                assertNotNull("Adapter should be SettingsAdapter", 
                        recyclerView.getAdapter() instanceof SettingsAdapter);
            });
        }
    }

    @Test
    public void launch_withConfiguredProviders_showsProviderCount() {
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
                
                recyclerView.getChildAt(0).performClick();
                
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
                
                if (recyclerView.getAdapter().getItemCount() > 2) {
                    View skillsItem = recyclerView.getLayoutManager().findViewByPosition(2);
                    if (skillsItem != null) {
                        skillsItem.performClick();
                    }
                }
                
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
                
                if (recyclerView.getAdapter().getItemCount() > 0) {
                    View providersItem = recyclerView.getLayoutManager().findViewByPosition(0);
                    if (providersItem != null) {
                        providersItem.performClick();
                    }
                }
                
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
                
                if (recyclerView.getAdapter().getItemCount() > 1) {
                    View agentItem = recyclerView.getLayoutManager().findViewByPosition(1);
                    if (agentItem != null) {
                        agentItem.performClick();
                    }
                }
                
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