package io.finett.droidclaw.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import io.finett.droidclaw.R;
import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.filesystem.WorkspaceManager;

/**
 * Dialog that displays file contents in a modal window.
 * Used for internally viewing files from the file browser.
 */
public class FileViewerDialog extends DialogFragment {
    private static final String ARG_FILE_PATH = "file_path";

    private TextView contentText;
    private ProgressBar loadingIndicator;
    private TextView titleText;
    private VirtualFileSystem virtualFileSystem;
    private String filePath;

    public static FileViewerDialog newInstance(String filePath) {
        FileViewerDialog dialog = new FileViewerDialog();
        Bundle args = new Bundle();
        args.putString(ARG_FILE_PATH, filePath);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            filePath = getArguments().getString(ARG_FILE_PATH, "");
        }

        WorkspaceManager workspaceManager = new WorkspaceManager(requireContext());
        virtualFileSystem = new VirtualFileSystem(workspaceManager);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());

        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_file_viewer, null);

        contentText = view.findViewById(R.id.fileContentText);
        loadingIndicator = view.findViewById(R.id.loadingIndicator);
        titleText = view.findViewById(R.id.fileTitleText);

        // Extract file name from path
        String fileName = filePath.contains("/") ? filePath.substring(filePath.lastIndexOf('/') + 1) : filePath;
        titleText.setText(fileName);

        builder.setView(view)
                .setPositiveButton(R.string.file_viewer_close, (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        // Load file content
        loadFileContent();

        return dialog;
    }

    private void loadFileContent() {
        loadingIndicator.setVisibility(View.VISIBLE);
        contentText.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                VirtualFileSystem.FileReadResult result = virtualFileSystem.readFile(filePath, null, null);
                String content = result.getContent();

                requireActivity().runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    contentText.setVisibility(View.VISIBLE);

                    if (result.isTruncated()) {
                        contentText.setText(content + "\n\n[File truncated - showing first " + result.getLinesRead() + " lines]");
                    } else {
                        contentText.setText(content);
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    contentText.setVisibility(View.VISIBLE);
                    contentText.setText(getString(R.string.file_viewer_error, e.getMessage()));
                });
            }
        }).start();
    }
}
