package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.finett.droidclaw.R;
import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.filesystem.WorkspaceManager;

/**
 * Fragment that allows browsing the agent's virtual file system.
 * Users can navigate directories and view file metadata.
 */
public class FileBrowserFragment extends Fragment {
    private static final String TAG = "FileBrowserFragment";
    private static final String ARG_CURRENT_PATH = "current_path";

    private TextView titleText;
    private TextView pathText;
    private TextView emptyText;
    private RecyclerView fileList;
    private FileBrowserAdapter adapter;
    private VirtualFileSystem virtualFileSystem;
    private String currentPath = ".";
    private final List<VirtualFileSystem.FileInfo> navigationStack = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WorkspaceManager workspaceManager = new WorkspaceManager(requireContext());
        virtualFileSystem = new VirtualFileSystem(workspaceManager);

        if (savedInstanceState != null) {
            currentPath = savedInstanceState.getString(ARG_CURRENT_PATH, ".");
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ARG_CURRENT_PATH, currentPath);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        titleText = view.findViewById(R.id.titleText);
        pathText = view.findViewById(R.id.pathText);
        emptyText = view.findViewById(R.id.emptyText);
        fileList = view.findViewById(R.id.fileList);

        setupRecyclerView();
        loadDirectory(currentPath);
    }

    private void setupRecyclerView() {
        adapter = new FileBrowserAdapter(new ArrayList<>());

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        fileList.setLayoutManager(layoutManager);
        fileList.setAdapter(adapter);

        adapter.setOnItemClickListener(item -> {
            if (item.isDirectory()) {
                navigateInto(item.getPath());
            }
        });
    }

    private void loadDirectory(String path) {
        currentPath = path;

        // Update path display
        String displayPath = ".".equals(path) ? "/" : "/" + path;
        pathText.setText(displayPath);

        // Update title to show current directory name
        if (".".equals(path)) {
            titleText.setText(R.string.file_browser_title);
        } else {
            String dirName = path;
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0) {
                dirName = path.substring(lastSlash + 1);
            }
            titleText.setText(dirName);
        }

        // Load files in background
        new Thread(() -> {
            try {
                VirtualFileSystem.FileListResult result = virtualFileSystem.listFiles(path, false);
                List<VirtualFileSystem.FileInfo> files = new ArrayList<>(result.getFiles());

                // Sort: directories first, then alphabetically
                Collections.sort(files, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) {
                        return a.isDirectory() ? -1 : 1;
                    }
                    // Extract just the name for sorting
                    return getFileName(a.getPath()).compareToIgnoreCase(getFileName(b.getPath()));
                });

                // Add parent directory entry (..) if not at root
                if (!".".equals(path)) {
                    String parentPath = getParentPath(path);
                    files.add(0, new ParentDirectoryEntry(parentPath));
                }

                requireActivity().runOnUiThread(() -> {
                    adapter.setFiles(files);
                    emptyText.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
                    fileList.setVisibility(files.isEmpty() ? View.GONE : View.VISIBLE);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to list directory: " + path, e);
                requireActivity().runOnUiThread(() -> {
                    emptyText.setText(getString(R.string.file_browser_error, e.getMessage()));
                    emptyText.setVisibility(View.VISIBLE);
                    fileList.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    private void navigateInto(String path) {
        navigationStack.add(new VirtualFileSystem.FileInfo(currentPath, true, 0, 0));
        loadDirectory(path);
    }

    private void navigateUp() {
        if (navigationStack.isEmpty()) {
            return;
        }
        VirtualFileSystem.FileInfo parent = navigationStack.remove(navigationStack.size() - 1);
        loadDirectory(parent.getPath());
    }

    private String getParentPath(String path) {
        if (".".equals(path)) {
            return ".";
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return ".";
        }
        return path.substring(0, lastSlash);
    }

    private String getFileName(String path) {
        // Special case for parent directory
        if ("..".equals(path)) {
            return "..";
        }
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    private String getIconForFile(VirtualFileSystem.FileInfo file) {
        if (file.isDirectory()) {
            return "📁";
        }
        String name = getFileName(file.getPath()).toLowerCase();
        if (name.endsWith(".md")) return "📝";
        if (name.endsWith(".py")) return "🐍";
        if (name.endsWith(".js")) return "📜";
        if (name.endsWith(".json")) return "📋";
        if (name.endsWith(".txt")) return "📄";
        if (name.endsWith(".xml")) return "📰";
        if (name.endsWith(".sh")) return "⚙️";
        if (name.endsWith(".yaml") || name.endsWith(".yml")) return "⚙️";
        return "📄";
    }

    private String getDetailsForFile(VirtualFileSystem.FileInfo file) {
        if (file.isDirectory()) {
            return getString(R.string.file_browser_directory);
        }
        return formatFileSize(file.getSize()) + " • " + formatDate(file.getLastModified());
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        } else {
            return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
        }
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * Special FileInfo subclass to identify parent directory entries.
     */
    static class ParentDirectoryEntry extends VirtualFileSystem.FileInfo {
        private final String parentPath;

        ParentDirectoryEntry(String parentPath) {
            super("..", true, 0, 0);
            this.parentPath = parentPath;
        }

        @Override
        public String getPath() {
            return parentPath;
        }
    }

    /**
     * Adapter for displaying files and directories in the file browser.
     */
    static class FileBrowserAdapter extends RecyclerView.Adapter<FileBrowserAdapter.FileViewHolder> {
        private final List<VirtualFileSystem.FileInfo> files;
        private OnItemClickListener listener;

        public interface OnItemClickListener {
            void onItemClick(VirtualFileSystem.FileInfo file);
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            this.listener = listener;
        }

        public FileBrowserAdapter(List<VirtualFileSystem.FileInfo> files) {
            this.files = files;
        }

        public void setFiles(List<VirtualFileSystem.FileInfo> newFiles) {
            files.clear();
            files.addAll(newFiles);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_file_browser, parent, false);
            return new FileViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
            VirtualFileSystem.FileInfo file = files.get(position);
            holder.bind(file);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(file);
                }
            });
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class FileViewHolder extends RecyclerView.ViewHolder {
            private final TextView fileIcon;
            private final TextView fileName;
            private final TextView fileDetails;
            private final TextView fileChevron;

            FileViewHolder(@NonNull View itemView) {
                super(itemView);
                fileIcon = itemView.findViewById(R.id.fileIcon);
                fileName = itemView.findViewById(R.id.fileName);
                fileDetails = itemView.findViewById(R.id.fileDetails);
                fileChevron = itemView.findViewById(R.id.fileChevron);
            }

            void bind(VirtualFileSystem.FileInfo file) {
                // Check if this is a parent directory entry
                boolean isParentDir = file instanceof ParentDirectoryEntry;
                
                // Extract just the name for display
                String name;
                if (isParentDir) {
                    name = "..";
                } else {
                    String path = file.getPath();
                    int lastSlash = path.lastIndexOf('/');
                    name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
                }
                
                fileName.setText(name);

                // Set icon based on file type
                String icon;
                if (isParentDir) {
                    icon = "⬆️";
                } else if (file.isDirectory()) {
                    icon = "📁";
                } else {
                    String nameLower = name.toLowerCase();
                    if (nameLower.endsWith(".md")) icon = "📝";
                    else if (nameLower.endsWith(".py")) icon = "🐍";
                    else if (nameLower.endsWith(".js")) icon = "📜";
                    else if (nameLower.endsWith(".json")) icon = "📋";
                    else if (nameLower.endsWith(".txt")) icon = "📄";
                    else if (nameLower.endsWith(".xml")) icon = "📰";
                    else if (nameLower.endsWith(".sh")) icon = "⚙️";
                    else if (nameLower.endsWith(".yaml") || nameLower.endsWith(".yml")) icon = "⚙️";
                    else icon = "📄";
                }
                fileIcon.setText(icon);

                // Set details
                if (isParentDir) {
                    fileDetails.setText(R.string.file_browser_parent_directory);
                } else if (file.isDirectory()) {
                    fileDetails.setText(R.string.file_browser_directory);
                } else {
                    String size;
                    if (file.getSize() < 1024) {
                        size = file.getSize() + " B";
                    } else if (file.getSize() < 1024 * 1024) {
                        size = String.format(Locale.getDefault(), "%.1f KB", file.getSize() / 1024.0);
                    } else {
                        size = String.format(Locale.getDefault(), "%.1f MB", file.getSize() / (1024.0 * 1024.0));
                    }
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    fileDetails.setText(size + " • " + sdf.format(new Date(file.getLastModified())));
                }

                // Show chevron for directories (including parent directory)
                fileChevron.setVisibility(file.isDirectory() ? View.VISIBLE : View.GONE);
            }
        }
    }
}