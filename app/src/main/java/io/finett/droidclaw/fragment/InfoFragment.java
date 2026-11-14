package io.finett.droidclaw.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import io.finett.droidclaw.BuildConfig;
import io.finett.droidclaw.R;
import io.finett.droidclaw.update.UpdateChecker;
import io.finett.droidclaw.update.UpdateInfo;
import io.finett.droidclaw.update.UpdatePreferences;
import io.finett.droidclaw.update.UpdatePromptDialog;

public class InfoFragment extends Fragment {

    private static final String GITHUB_URL = "https://github.com/finettt/DroidClaw";

    private TextView textAppVersion;
    private TextView textGitHubLink;

    private MaterialButton buttonCheckUpdates;
    private SwitchMaterial switchAutoUpdate;
    private LinearLayout bannerUpdate;
    private TextView bannerUpdateText;

    private UpdatePreferences updatePrefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        updatePrefs = new UpdatePreferences(requireContext());

        textAppVersion = view.findViewById(R.id.text_app_version);
        textGitHubLink = view.findViewById(R.id.text_github_link);

        buttonCheckUpdates = view.findViewById(R.id.button_check_updates);
        switchAutoUpdate = view.findViewById(R.id.switch_auto_update);
        bannerUpdate = view.findViewById(R.id.banner_update);
        bannerUpdateText = view.findViewById(R.id.banner_update_text);

        textAppVersion.setText(getString(R.string.info_app_version, BuildConfig.APP_VERSION));

        textGitHubLink.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL));
            startActivity(intent);
        });

        // Opt-out toggle: persist the choice, refresh the banner on resume.
        switchAutoUpdate.setOnCheckedChangeListener(
                (buttonView, isChecked) -> updatePrefs.setEnabled(isChecked));

        // A tap on the banner runs a fresh check and shows the release dialog.
        bannerUpdate.setOnClickListener(v -> checkNow(true));

        loadState();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-sync the switch with prefs and re-read the banner in case a startup
        // check stored a newer tag while this screen was paused.
        loadState();
        loadBanner();
    }

    private void loadState() {
        switchAutoUpdate.setOnCheckedChangeListener(null);
        switchAutoUpdate.setChecked(updatePrefs.isEnabled());
        switchAutoUpdate.setOnCheckedChangeListener(
                (buttonView, isChecked) -> updatePrefs.setEnabled(isChecked));
    }

    private void loadBanner() {
        String tag = updatePrefs.getLastKnownNewerTag();
        if (tag == null || tag.isEmpty()) {
            bannerUpdate.setVisibility(View.GONE);
        } else {
            bannerUpdateText.setText(getString(R.string.info_update_banner, tag));
            bannerUpdate.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Run an update check. The network call goes on a background thread via
     * {@link UpdateChecker#checkNowAsync}; UI work is posted back to the main
     * thread. {@code showFailed} gates the failure toast: the Info-screen manual
     * check surfaces failures, while the banner refresh path stays silent.
     *
     * @param showFailed whether to report a failed check to the user
     */
    private void checkNow(boolean showFailed) {
        buttonCheckUpdates.setEnabled(false);
        UpdateChecker.checkNowAsync(requireContext(), result -> {
            requireActivity().runOnUiThread(() -> {
                buttonCheckUpdates.setEnabled(true);
                switch (result.getOutcome()) {
                    case UPDATE: {
                        UpdateInfo info = result.getInfo();
                        updatePrefs.setLastKnownNewerTag(
                                info != null ? info.getTag() : null);
                        loadBanner();
                        if (info != null) {
                            UpdatePromptDialog.show(requireContext(), info);
                        }
                        break;
                    }
                    case UP_TO_DATE:
                        Toast.makeText(requireContext(),
                                R.string.info_updates_up_to_date,
                                Toast.LENGTH_SHORT).show();
                        break;
                    case UNAVAILABLE:
                    case ERROR:
                        if (showFailed) {
                            Toast.makeText(requireContext(),
                                    R.string.info_updates_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                    default:
                        break;
                }
            });
        });
    }
}
