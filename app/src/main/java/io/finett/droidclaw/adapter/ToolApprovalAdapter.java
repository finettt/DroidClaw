package io.finett.droidclaw.adapter;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.ToolApprovalMode;
import io.finett.droidclaw.util.SettingsManager;

/**
 * ArrayAdapter that renders one row per registered tool with an inline
 * {@link AutoCompleteTextView} dropdown for per-tool approval mode.
 */
public class ToolApprovalAdapter extends ArrayAdapter<ToolApprovalAdapter.ToolApprovalEntry> {

    public static class ToolApprovalEntry {
        public final String toolName;
        public final String toolDescription;
        public ToolApprovalMode mode;

        public ToolApprovalEntry(String toolName, String toolDescription, ToolApprovalMode mode) {
            this.toolName = toolName;
            this.toolDescription = toolDescription;
            this.mode = mode;
        }
    }

    private static final String[] MODE_LABELS = {
            "Follow global setting",   // DEFAULT
            "Always approve",          // ALWAYS_APPROVE
            "Always reject"            // ALWAYS_REJECT
    };

    private final Context context;
    private final SettingsManager settingsManager;

    public ToolApprovalAdapter(@NonNull Context context,
                               @NonNull List<ToolApprovalEntry> entries,
                               @NonNull SettingsManager settingsManager) {
        super(context, 0, entries);
        this.context = context;
        this.settingsManager = settingsManager;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return bindView(position, convertView, parent);
    }

    @NonNull
    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return bindView(position, convertView, parent);
    }

    private View bindView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_tool_approval_row, parent, false);
        }

        ToolApprovalEntry entry = getItem(position);

        ((TextView) convertView.findViewById(R.id.text_tool_name)).setText(entry.toolName);
        ((TextView) convertView.findViewById(R.id.text_tool_desc)).setText(entry.toolDescription);

        AutoCompleteTextView dropdown = convertView.findViewById(R.id.dropdown_approval_mode);

        // Rebuild adapter each time to avoid stale listeners / state
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_dropdown_item_1line,
                MODE_LABELS
        );
        dropdown.setAdapter(adapter);

        // Restore current selection
        String label = getLabelForMode(entry.mode);
        dropdown.setText(label, false);

        // Attach text watcher to persist selection changes
        dropdown.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String selected = s.toString();
                ToolApprovalMode newMode = ToolApprovalMode.DEFAULT;
                for (int i = 0; i < MODE_LABELS.length; i++) {
                    if (MODE_LABELS[i].equals(selected)) {
                        newMode = ToolApprovalMode.values()[i];
                        break;
                    }
                }
                entry.mode = newMode;
                persistOverride(entry.toolName, newMode);
            }
        });

        return convertView;
    }

    private String getLabelForMode(ToolApprovalMode mode) {
        switch (mode) {
            case ALWAYS_APPROVE: return MODE_LABELS[1];
            case ALWAYS_REJECT:  return MODE_LABELS[2];
            case DEFAULT:
            default:             return MODE_LABELS[0];
        }
    }

    private void persistOverride(String toolName, ToolApprovalMode mode) {
        Map<String, String> overrides = new HashMap<>(
                settingsManager.getAgentConfig().getToolApprovalOverrides());
        if (mode == ToolApprovalMode.DEFAULT) {
            overrides.remove(toolName);
        } else {
            overrides.put(toolName, mode.name());
        }
        settingsManager.getAgentConfig().setToolApprovalOverrides(overrides);
        settingsManager.setAgentConfig(settingsManager.getAgentConfig());
    }
}
