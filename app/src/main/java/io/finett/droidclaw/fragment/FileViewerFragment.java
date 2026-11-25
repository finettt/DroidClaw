package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.finett.droidclaw.MainActivity;
import io.finett.droidclaw.R;
import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.util.MarkdownRenderer;
import io.finett.droidclaw.util.SyntaxHighlighter;

/**
 * Full-screen fragment that displays file contents from the agent's workspace.
 * Markdown files are rendered with formatting; all other text files are shown
 * in a monospace font.
 */
public class FileViewerFragment extends Fragment {
    private static final String ARG_FILE_PATH = "file_path";

    private TextView contentText;
    private ProgressBar loadingIndicator;
    private ScrollView contentScroll;
    private VirtualFileSystem virtualFileSystem;
    private String filePath;

    public static FileViewerFragment newInstance(String filePath) {
        FileViewerFragment fragment = new FileViewerFragment();
        Bundle args = new Bundle();
        args.putString(ARG_FILE_PATH, filePath);
        fragment.setArguments(args);
        return fragment;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        contentText = view.findViewById(R.id.fileContentText);
        loadingIndicator = view.findViewById(R.id.loadingIndicator);
        contentScroll = view.findViewById(R.id.contentScroll);

        setTitle();
        loadFileContent();
    }

    private void setTitle() {
        String name = filePath.contains("/")
                ? filePath.substring(filePath.lastIndexOf('/') + 1)
                : filePath;
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setToolbarTitle(name);
        }
    }

    private void loadFileContent() {
        loadingIndicator.setVisibility(View.VISIBLE);
        contentScroll.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                VirtualFileSystem.FileReadResult result = virtualFileSystem.readFile(filePath, null, null);
                final String content = result.isTruncated()
                        ? result.getContent() + "\n\n[File truncated - showing first " + result.getLinesRead() + " lines]"
                        : result.getContent();

                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    loadingIndicator.setVisibility(View.GONE);
                    contentScroll.setVisibility(View.VISIBLE);

                    if (filePath.toLowerCase().endsWith(".md")) {
                        MarkdownRenderer.render(requireContext(), contentText, content);
                    } else {
                        contentText.setTypeface(android.graphics.Typeface.MONOSPACE);
                        contentText.setTextSize(13f);
                        contentText.setText(SyntaxHighlighter.highlight(
                                requireContext(), extensionOf(filePath), content));
                    }
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    loadingIndicator.setVisibility(View.GONE);
                    contentScroll.setVisibility(View.VISIBLE);
                    contentText.setText(getString(R.string.file_viewer_error, e.getMessage()));
                });
            }
        }).start();
    }

    private static String extensionOf(String path) {
        String name = path.contains("/")
                ? path.substring(path.lastIndexOf('/') + 1)
                : path;
        int dot = name.lastIndexOf('.');
        return (dot >= 0 && dot < name.length() - 1) ? name.substring(dot + 1) : "";
    }
}
