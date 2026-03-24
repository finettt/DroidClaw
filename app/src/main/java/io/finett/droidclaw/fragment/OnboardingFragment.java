package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.tabs.TabLayout;

import io.finett.droidclaw.R;
import io.finett.droidclaw.util.SettingsManager;

public class OnboardingFragment extends Fragment {
    private static final String ARG_PAGE_INDEX = "page_index";
    private SettingsManager settingsManager;

    private TabLayout tabLayout;
    private Button skipButton;
    private Button nextButton;
    private EditText userNameInput;
    private TextView pageDescription;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tabLayout);
        skipButton = view.findViewById(R.id.skipButton);
        nextButton = view.findViewById(R.id.nextButton);
        userNameInput = view.findViewById(R.id.userNameInput);
        pageDescription = view.findViewById(R.id.pageDescription);

        setupTabs();
        setupListeners();

        // Update UI based on current tab
        updatePageUI(tabLayout.getSelectedTabPosition());
    }

    private void setupTabs() {
        // Add tabs for each onboarding page
        tabLayout.addTab(tabLayout.newTab().setText(R.string.onboarding_welcome_title));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.onboarding_name_label));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.onboarding_complete_title));

        // Listen for tab changes
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updatePageUI(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void updatePageUI(int position) {
        switch (position) {
            case 0:
                pageDescription.setText(R.string.onboarding_welcome_description);
                nextButton.setText(R.string.onboarding_next);
                userNameInput.setVisibility(View.GONE);
                break;
            case 1:
                pageDescription.setText(R.string.onboarding_welcome_description);
                nextButton.setText(R.string.onboarding_next);
                userNameInput.setVisibility(View.VISIBLE);
                userNameInput.setText(settingsManager.getUserName());
                break;
            case 2:
                pageDescription.setText(R.string.onboarding_complete_description);
                nextButton.setText(R.string.onboarding_done);
                userNameInput.setVisibility(View.GONE);
                break;
        }
    }

    private void setupListeners() {
        skipButton.setOnClickListener(v -> completeOnboarding());

        nextButton.setOnClickListener(v -> {
            int currentPosition = tabLayout.getSelectedTabPosition();
            if (currentPosition == 1) {
                // Save user name before moving to next page
                String userName = userNameInput.getText().toString().trim();
                settingsManager.setUserName(userName);
            }

            if (currentPosition == tabLayout.getTabCount() - 1) {
                completeOnboarding();
            } else {
                tabLayout.selectTab(tabLayout.getTabAt(currentPosition + 1));
            }
        });
    }

    private void completeOnboarding() {
        settingsManager.setOnboardingCompleted(true);
        if (getArguments() != null && getArguments().getBoolean("reset", false)) {
            // If resetting, stay on settings but show toast
            // Navigation will be handled by settings
        }
        Navigation.findNavController(requireView()).navigateUp();
    }

    public void resetToFirstPage() {
        tabLayout.selectTab(tabLayout.getTabAt(0));
        userNameInput.setText("");
        updatePageUI(0);
    }
}
