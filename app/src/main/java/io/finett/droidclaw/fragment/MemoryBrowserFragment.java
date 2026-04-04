package io.finett.droidclaw.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.repository.MemoryRepository;

/**
 * Fragment for browsing and editing memory files.
 * Displays MEMORY.md and daily notes.
 */
public class MemoryBrowserFragment extends Fragment {
    private static final String TAG = "MemoryBrowserFragment";
    
    private TextView longTermPreview;
    private Button editLongTermButton;
    private RecyclerView dailyNotesRecycler;
    private MemoryRepository memoryRepository;
    private DailyNotesAdapter adapter;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        WorkspaceManager workspaceManager = new WorkspaceManager(requireContext());
        try {
            workspaceManager.initialize();
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize workspace", e);
        }
        
        memoryRepository = new MemoryRepository(workspaceManager);
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_memory_browser, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        longTermPreview = view.findViewById(R.id.longTermPreview);
        editLongTermButton = view.findViewById(R.id.editLongTermButton);
        dailyNotesRecycler = view.findViewById(R.id.dailyNotesRecycler);
        
        setupLongTermMemory();
        setupDailyNotes();
    }
    
    private void setupLongTermMemory() {
        loadLongTermPreview();
        
        editLongTermButton.setOnClickListener(v -> showEditDialog());
    }
    
    private void loadLongTermPreview() {
        try {
            String content = memoryRepository.readLongTermMemory();
            if (content.isEmpty()) {
                longTermPreview.setText("No long-term memory yet. Tap Edit to add some.");
            } else {
                // Show first 200 characters
                String preview = content.length() > 200 
                    ? content.substring(0, 200) + "..." 
                    : content;
                longTermPreview.setText(preview);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load long-term memory", e);
            longTermPreview.setText("Error loading memory");
        }
    }
    
    private void setupDailyNotes() {
        adapter = new DailyNotesAdapter();
        dailyNotesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        dailyNotesRecycler.setAdapter(adapter);
        
        loadDailyNotes();
    }
    
    private void loadDailyNotes() {
        List<File> notes = memoryRepository.getAllDailyNotes();
        adapter.setNotes(notes);
        Log.d(TAG, "Loaded " + notes.size() + " daily notes");
    }
    
    private void showEditDialog() {
        View dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_memory, null);
        EditText editText = dialogView.findViewById(R.id.memoryEditText);
        
        // Load current content
        try {
            String current = memoryRepository.readLongTermMemory();
            editText.setText(current);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load content for editing", e);
        }
        
        new AlertDialog.Builder(requireContext())
            .setTitle("Edit Long-term Memory")
            .setView(dialogView)
            .setPositiveButton("Save", (dialog, which) -> {
                String newContent = editText.getText().toString();
                saveToLongTermMemory(newContent);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void saveToLongTermMemory(String content) {
        try {
            // For simplicity, we'll append. User can manually edit MEMORY.md file if needed.
            memoryRepository.appendToLongTermMemory(content);
            loadLongTermPreview();
            Toast.makeText(requireContext(), "Saved to long-term memory", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Failed to save to long-term memory", e);
            Toast.makeText(requireContext(), "Failed to save: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Adapter for displaying daily notes.
     */
    private class DailyNotesAdapter extends RecyclerView.Adapter<DailyNotesAdapter.ViewHolder> {
        private List<File> notes = List.of();
        
        void setNotes(List<File> notes) {
            this.notes = notes;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File note = notes.get(position);
            String filename = note.getName().replace(".md", "");
            
            holder.title.setText(filename);
            
            // Read first line as preview
            try (BufferedReader reader = new BufferedReader(new FileReader(note))) {
                String firstLine = reader.readLine();
                if (firstLine != null) {
                    holder.subtitle.setText(firstLine);
                }
            } catch (IOException e) {
                holder.subtitle.setText("Error reading file");
            }
            
            holder.itemView.setOnClickListener(v -> showNoteDialog(note));
        }
        
        @Override
        public int getItemCount() {
            return notes.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;
            
            ViewHolder(View view) {
                super(view);
                title = view.findViewById(android.R.id.text1);
                subtitle = view.findViewById(android.R.id.text2);
            }
        }
    }
    
    private void showNoteDialog(File noteFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(noteFile))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            
            new AlertDialog.Builder(requireContext())
                .setTitle(noteFile.getName())
                .setMessage(content.toString())
                .setPositiveButton("OK", null)
                .show();
                
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Failed to read note: " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
        }
    }
}