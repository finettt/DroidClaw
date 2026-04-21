package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.SettingsAdapter;
import io.finett.droidclaw.util.SettingsManager;

public class SettingsFragment extends Fragment {

    private static final String ITEM_PROVIDERS = "providers";
    private static final String ITEM_AGENT = "agent";
    private static final String ITEM_HEARTBEAT = "heartbeat";
    private static final String ITEM_CRON_JOBS = "cron_jobs";
    private static final String ITEM_SKILLS = "skills";
    private static final String ITEM_INFO = "info";
    private static final String ITEM_RESET_ONBOARDING = "reset_onboarding";

    private RecyclerView recyclerView;
    private SettingsAdapter adapter;
    private SettingsManager settingsManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_settings);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new SettingsAdapter();
        adapter.setOnSettingsItemClickListener(this::handleSettingsItemClick);
        recyclerView.setAdapter(adapter);

        loadSettingsItems();
    }

    @Override
    public void onResume() {
        super.onResume();

        loadSettingsItems();
    }

    private void loadSettingsItems() {
        List<SettingsAdapter.SettingsItem> items = new ArrayList<>();


        int providerCount = settingsManager.getProviderCount();
        String providerSubtitle = getString(R.string.settings_providers_subtitle, providerCount);
        items.add(new SettingsAdapter.SettingsItem(
                ITEM_PROVIDERS,
                R.drawable.ic_settings_provider,
                getString(R.string.settings_providers),
                providerSubtitle,
                true
        ));


        items.add(new SettingsAdapter.SettingsItem(
                ITEM_AGENT,
                R.drawable.ic_settings_agent,
                getString(R.string.settings_agent),
                getString(R.string.settings_agent_subtitle),
                true
        ));


        items.add(new SettingsAdapter.SettingsItem(
                ITEM_HEARTBEAT,
                R.drawable.ic_settings_heartbeat,
                getString(R.string.settings_heartbeat),
                getString(R.string.settings_heartbeat_subtitle),
                true
        ));


        items.add(new SettingsAdapter.SettingsItem(
                ITEM_CRON_JOBS,
                R.drawable.ic_settings_cron,
                getString(R.string.cron_jobs),
                getString(R.string.settings_cron_jobs_subtitle),
                true
        ));


        items.add(new SettingsAdapter.SettingsItem(
                ITEM_SKILLS,
                R.drawable.ic_tool_generic,
                getString(R.string.settings_skills),
                getString(R.string.settings_skills_subtitle),
                true
        ));


        items.add(new SettingsAdapter.SettingsItem(
                ITEM_INFO,
                R.drawable.ic_action_info,
                getString(R.string.settings_info),
                getString(R.string.settings_info_subtitle),
                true
        ));


        items.add(new SettingsAdapter.SettingsItem(
                ITEM_RESET_ONBOARDING,
                R.drawable.ic_settings_reset,
                getString(R.string.settings_reset_onboarding),
                getString(R.string.settings_reset_onboarding_subtitle),
                false
        ));

        adapter.setItems(items);
    }

    private void handleSettingsItemClick(SettingsAdapter.SettingsItem item) {
        switch (item.getId()) {
            case ITEM_PROVIDERS:
                Navigation.findNavController(requireView())
                        .navigate(R.id.action_settingsFragment_to_providersListFragment);
                break;
            case ITEM_AGENT:
                Navigation.findNavController(requireView())
                        .navigate(R.id.action_settingsFragment_to_agentSettingsFragment);
                break;
            case ITEM_HEARTBEAT:
                Navigation.findNavController(requireView())
                        .navigate(R.id.action_settingsFragment_to_heartbeatSettingsFragment);
                break;
            case ITEM_CRON_JOBS:
                Navigation.findNavController(requireView())
                        .navigate(R.id.cronJobListFragment);
                break;
            case ITEM_SKILLS:
                Navigation.findNavController(requireView())
                        .navigate(R.id.action_settingsFragment_to_skillsBrowserFragment);
                break;
            case ITEM_INFO:
                Navigation.findNavController(requireView())
                        .navigate(R.id.action_settingsFragment_to_infoFragment);
                break;
            case ITEM_RESET_ONBOARDING:
                showResetOnboardingConfirmation();
                break;
        }
    }

    private void showResetOnboardingConfirmation() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_reset_onboarding)
                .setMessage(R.string.settings_reset_onboarding_confirm)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    settingsManager.resetOnboarding();
        
                    Navigation.findNavController(requireView())
                            .navigate(R.id.action_settingsFragment_to_onboardingFragment);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}