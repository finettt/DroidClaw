package io.finett.droidclaw.fragment;

import static androidx.navigation.Navigation.setViewNavController;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.navigation.testing.TestNavHostController;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.R;
import io.finett.droidclaw.util.SettingsManager;

@RunWith(AndroidJUnit4.class)
public class OnboardingFragmentTest {

    private static final String SETTINGS_PREFS = "droidclaw_settings";

    @Before
    public void setUp() {
        // Clear settings before each test
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }

    @Test
    public void launch_displaysWelcomeSection() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);

                View sectionWelcome = fragment.requireView().findViewById(R.id.section_welcome);
                View sectionName = fragment.requireView().findViewById(R.id.section_name);
                View sectionProvider = fragment.requireView().findViewById(R.id.section_provider);

                assertEquals("Welcome section should be visible", 
                        View.VISIBLE, sectionWelcome.getVisibility());
                assertEquals("Name section should be hidden", 
                        View.GONE, sectionName.getVisibility());
                assertEquals("Provider section should be hidden", 
                        View.GONE, sectionProvider.getVisibility());
            });
        }
    }

    @Test
    public void welcomeSection_hasNextAndSkipButtons() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);

                Button btnNext = fragment.requireView().findViewById(R.id.btn_next_1);
                Button btnSkip = fragment.requireView().findViewById(R.id.btn_skip_1);

                assertNotNull("Next button should exist", btnNext);
                assertNotNull("Skip button should exist", btnSkip);
                assertTrue("Next button should be enabled", btnNext.isEnabled());
                assertTrue("Skip button should be enabled", btnSkip.isEnabled());
            });
        }
    }

    @Test
    public void clickNext_transitionsToNameSection() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);

                Button btnNext = fragment.requireView().findViewById(R.id.btn_next_1);
                btnNext.performClick();
            });

            // Wait for animation outside of onFragment
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            scenario.onFragment(fragment -> {
                View sectionWelcome = fragment.requireView().findViewById(R.id.section_welcome);
                View sectionName = fragment.requireView().findViewById(R.id.section_name);

                assertEquals("Welcome section should be hidden after transition",
                        View.GONE, sectionWelcome.getVisibility());
                assertEquals("Name section should be visible after transition",
                        View.VISIBLE, sectionName.getVisibility());
            });
        }
    }

    @Test
    public void nameSection_validationWorks() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);
                fragment.requireView().findViewById(R.id.btn_next_1).performClick();
            });

            // Wait for animation
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                TextInputLayout tilName = fragment.requireView().findViewById(R.id.til_name);
                TextInputEditText etName = fragment.requireView().findViewById(R.id.et_name);
                Button btnNext2 = fragment.requireView().findViewById(R.id.btn_next_2);

                // Try to proceed without entering name
                btnNext2.performClick();

                // Should show error
                assertNotNull("Error should be set when name is empty", tilName.getError());

                // Enter name and try again
                etName.setText("Test User");
                btnNext2.performClick();
            });

            // Wait for animation
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            scenario.onFragment(fragment -> {
                // Should transition to provider section
                View sectionProvider = fragment.requireView().findViewById(R.id.section_provider);
                assertEquals("Provider section should be visible after valid name",
                        View.VISIBLE, sectionProvider.getVisibility());
            });
        }
    }

    @Test
    public void nameSection_savesNameWhenProceeding() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);
                fragment.requireView().findViewById(R.id.btn_next_1).performClick();
            });

            // Wait for animation
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            scenario.onFragment(fragment -> {
                TextInputEditText etName = fragment.requireView().findViewById(R.id.et_name);
                Button btnNext2 = fragment.requireView().findViewById(R.id.btn_next_2);

                etName.setText("John Doe");
                btnNext2.performClick();
            });

            // Verify name was saved
            SettingsManager settingsManager = new SettingsManager(getApplicationContext());
            assertEquals("John Doe", settingsManager.getUserName());
        }
    }

    @Test
    public void providerSection_validationWorks() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);
                navigateToProviderSection(fragment);
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                TextInputLayout tilProviderName = fragment.requireView().findViewById(R.id.til_provider_name);
                TextInputLayout tilBaseUrl = fragment.requireView().findViewById(R.id.til_base_url);
                TextInputLayout tilApiKey = fragment.requireView().findViewById(R.id.til_api_key);
                Button btnGetStarted = fragment.requireView().findViewById(R.id.btn_get_started);

                // Try to proceed without filling fields
                btnGetStarted.performClick();

                // Should show errors
                assertNotNull("Provider name error should be set", tilProviderName.getError());
                assertNotNull("Base URL error should be set", tilBaseUrl.getError());
                assertNotNull("API key error should be set", tilApiKey.getError());
            });
        }
    }

    @Test
    public void providerSection_invalidUrlShowsError() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);
                navigateToProviderSection(fragment);
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                TextInputEditText etProviderName = fragment.requireView().findViewById(R.id.et_provider_name);
                TextInputEditText etBaseUrl = fragment.requireView().findViewById(R.id.et_base_url);
                TextInputEditText etApiKey = fragment.requireView().findViewById(R.id.et_api_key);
                TextInputLayout tilBaseUrl = fragment.requireView().findViewById(R.id.til_base_url);
                Button btnGetStarted = fragment.requireView().findViewById(R.id.btn_get_started);

                etProviderName.setText("Test Provider");
                etBaseUrl.setText("invalid-url");
                etApiKey.setText("test-key");

                btnGetStarted.performClick();

                assertNotNull("Invalid URL error should be set", tilBaseUrl.getError());
            });
        }
    }

    @Test
    public void providerSection_validDataSavesAndCompletes() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                TestNavHostController navController = attachNavController(fragment, R.id.onboardingFragment);
                navigateToProviderSection(fragment);
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                TextInputEditText etProviderName = fragment.requireView().findViewById(R.id.et_provider_name);
                TextInputEditText etBaseUrl = fragment.requireView().findViewById(R.id.et_base_url);
                TextInputEditText etApiKey = fragment.requireView().findViewById(R.id.et_api_key);
                Button btnGetStarted = fragment.requireView().findViewById(R.id.btn_get_started);

                etProviderName.setText("OpenAI");
                etBaseUrl.setText("https://api.openai.com/v1");
                etApiKey.setText("sk-test-key");

                btnGetStarted.performClick();
            });

            // Wait for navigation
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Verify onboarding was marked as completed
            SettingsManager settingsManager = new SettingsManager(getApplicationContext());
            assertTrue("Onboarding should be marked as completed",
                    settingsManager.isOnboardingCompleted());
            assertEquals("Provider should be saved", 1, settingsManager.getProviderCount());
        }
    }

    @Test
    public void skipButton_completesOnboardingWithoutData() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);
                Button btnSkip = fragment.requireView().findViewById(R.id.btn_skip_1);
                btnSkip.performClick();
            });

            // Wait for navigation
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Verify onboarding was marked as completed without saving data
            SettingsManager settingsManager = new SettingsManager(getApplicationContext());
            assertTrue("Onboarding should be marked as completed after skip",
                    settingsManager.isOnboardingCompleted());
            assertEquals("No providers should be saved after skip",
                    0, settingsManager.getProviderCount());
        }
    }

    @Test
    public void apiTypeDropdown_hasDefaultValue() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);
                navigateToProviderSection(fragment);
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                AutoCompleteTextView actvApiType = fragment.requireView().findViewById(R.id.actv_api_type);
                
                assertEquals("API type should default to openai-completions",
                        "openai-completions", actvApiType.getText().toString());
            });
        }
    }

    @Test
    public void nameInput_clearsErrorOnTyping() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);
                fragment.requireView().findViewById(R.id.btn_next_1).performClick();
            });

            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                TextInputLayout tilName = fragment.requireView().findViewById(R.id.til_name);
                TextInputEditText etName = fragment.requireView().findViewById(R.id.et_name);
                Button btnNext2 = fragment.requireView().findViewById(R.id.btn_next_2);

                // Trigger validation error
                btnNext2.performClick();
                assertNotNull("Error should be shown", tilName.getError());

                // Type in the field
                etName.setText("A");
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onFragment(fragment -> {
                TextInputLayout tilName = fragment.requireView().findViewById(R.id.til_name);
                // Error should be cleared
                assertEquals("Error should be cleared when typing", null, tilName.getError());
            });
        }
    }

    @Test
    public void recreate_maintainsCurrentSection() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);
                fragment.requireView().findViewById(R.id.btn_next_1).performClick();
            });

            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Recreate fragment
            scenario.recreate();

            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);

                // Should show welcome section again after recreate
                View sectionWelcome = fragment.requireView().findViewById(R.id.section_welcome);
                assertEquals("Should reset to welcome section after recreate",
                        View.VISIBLE, sectionWelcome.getVisibility());
            });
        }
    }

    @Test
    public void providerSection_allFieldsPresent() {
        try (FragmentScenario<OnboardingFragment> scenario =
                     FragmentScenario.launchInContainer(OnboardingFragment.class, null, R.style.Theme_DroidClaw)) {
            scenario.onFragment(fragment -> {
                attachNavController(fragment, R.id.onboardingFragment);

                navigateToProviderSection(fragment);

                assertNotNull("Provider name field should exist", 
                        fragment.requireView().findViewById(R.id.et_provider_name));
                assertNotNull("Base URL field should exist", 
                        fragment.requireView().findViewById(R.id.et_base_url));
                assertNotNull("API key field should exist", 
                        fragment.requireView().findViewById(R.id.et_api_key));
                assertNotNull("API type dropdown should exist", 
                        fragment.requireView().findViewById(R.id.actv_api_type));
                assertNotNull("Get Started button should exist", 
                        fragment.requireView().findViewById(R.id.btn_get_started));
                assertNotNull("Skip button should exist", 
                        fragment.requireView().findViewById(R.id.btn_skip_3));
            });
        }
    }

    private void navigateToProviderSection(OnboardingFragment fragment) {
        // Navigate to name section
        fragment.requireView().findViewById(R.id.btn_next_1).performClick();

        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Fill name and navigate to provider section
        TextInputEditText etName = fragment.requireView().findViewById(R.id.et_name);
        etName.setText("Test User");
        fragment.requireView().findViewById(R.id.btn_next_2).performClick();

        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private TestNavHostController attachNavController(OnboardingFragment fragment, int destinationId) {
        TestNavHostController navController = new TestNavHostController(fragment.requireContext());
        navController.setGraph(R.navigation.nav_graph);
        navController.setCurrentDestination(destinationId);
        setViewNavController(fragment.requireView(), navController);
        return navController;
    }
}