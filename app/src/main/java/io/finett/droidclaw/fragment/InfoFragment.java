package io.finett.droidclaw.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.finett.droidclaw.BuildConfig;
import io.finett.droidclaw.R;

public class InfoFragment extends Fragment {

    private static final String GITHUB_URL = "https://github.com/finettt/DroidClaw";

    private TextView textAppVersion;
    private TextView textGitHubLink;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textAppVersion = view.findViewById(R.id.text_app_version);
        textGitHubLink = view.findViewById(R.id.text_github_link);

        // Display app version
        textAppVersion.setText(getString(R.string.info_app_version, BuildConfig.APP_VERSION));

        // Open GitHub on click
        textGitHubLink.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL));
            startActivity(intent);
        });
    }
}
