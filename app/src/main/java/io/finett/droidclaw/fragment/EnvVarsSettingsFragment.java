package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.finett.droidclaw.R;
import io.finett.droidclaw.util.SettingsManager;

public class EnvVarsSettingsFragment extends Fragment {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Z0-9_]+$");

    private RecyclerView recyclerView;
    private TextView emptyState;
    private FloatingActionButton fab;
    private SettingsManager settingsManager;
    private EnvVarAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_env_vars_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_env_vars);
        emptyState = view.findViewById(R.id.text_empty_state);
        fab = view.findViewById(R.id.fab_add_env_var);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new EnvVarAdapter();
        recyclerView.setAdapter(adapter);

        fab.setOnClickListener(v -> showEditDialog(null, null));

        loadEnvVars();
    }

    private void loadEnvVars() {
        Map<String, String> vars = settingsManager.getAllEnvVars();
        List<Map.Entry<String, String>> entries = new ArrayList<>(vars.entrySet());
        Collections.sort(entries, (a, b) -> a.getKey().compareTo(b.getKey()));

        adapter.setItems(entries);

        if (entries.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showEditDialog(@Nullable String existingKey, @Nullable String existingValue) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_env_var_edit, null);

        TextInputLayout layoutKey = dialogView.findViewById(R.id.layout_env_key);
        TextInputEditText inputKey = dialogView.findViewById(R.id.input_env_key);
        TextInputEditText inputValue = dialogView.findViewById(R.id.input_env_value);

        boolean isEdit = existingKey != null;
        if (isEdit) {
            inputKey.setText(existingKey);
            inputKey.setEnabled(false); // Don't allow key rename on edit
            inputValue.setText(existingValue);
        }

        String title = getString(isEdit ? R.string.env_var_edit : R.string.env_var_add);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String key = inputKey.getText() != null ? inputKey.getText().toString().trim().toUpperCase() : "";
                    String value = inputValue.getText() != null ? inputValue.getText().toString().trim() : "";

                    if (key.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.env_var_key_required, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!KEY_PATTERN.matcher(key).matches()) {
                        Toast.makeText(requireContext(), R.string.env_var_key_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    settingsManager.setEnvVar(key, value);
                    Toast.makeText(requireContext(), R.string.env_var_saved, Toast.LENGTH_SHORT).show();
                    loadEnvVars();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(String key) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(key)
                .setMessage(R.string.env_var_delete_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    settingsManager.removeEnvVar(key);
                    Toast.makeText(requireContext(), R.string.env_var_deleted, Toast.LENGTH_SHORT).show();
                    loadEnvVars();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== RecyclerView Adapter ====================

    private class EnvVarAdapter extends RecyclerView.Adapter<EnvVarAdapter.ViewHolder> {

        private List<Map.Entry<String, String>> items = new ArrayList<>();

        void setItems(List<Map.Entry<String, String>> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_env_var, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map.Entry<String, String> entry = items.get(position);
            holder.textKey.setText(entry.getKey());

            // Mask the value for display, showing only first 4 chars
            String value = entry.getValue();
            if (value.length() > 8) {
                holder.textValue.setText(value.substring(0, 4) + "••••••••");
            } else {
                holder.textValue.setText(value);
            }

            holder.buttonEdit.setOnClickListener(v ->
                    showEditDialog(entry.getKey(), entry.getValue()));
            holder.buttonDelete.setOnClickListener(v ->
                    confirmDelete(entry.getKey()));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView textKey;
            final TextView textValue;
            final View buttonEdit;
            final View buttonDelete;

            ViewHolder(View itemView) {
                super(itemView);
                textKey = itemView.findViewById(R.id.text_env_key);
                textValue = itemView.findViewById(R.id.text_env_value);
                buttonEdit = itemView.findViewById(R.id.button_edit);
                buttonDelete = itemView.findViewById(R.id.button_delete);
            }
        }
    }
}