package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.ToolApprovalAdapter;
import io.finett.droidclaw.model.ToolApprovalMode;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolRegistry;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Per-tool approval settings screen. Each registered tool gets a row with an
 * inline dropdown for choosing {@link ToolApprovalMode}.
 */
public class ToolApprovalSettingsFragment extends Fragment {

    private ListView listView;
    private ToolApprovalAdapter adapter;
    private SettingsManager settingsManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tool_approval_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        listView = view.findViewById(R.id.list_tools);

        List<ToolApprovalAdapter.ToolApprovalEntry> entries = buildEntries();
        adapter = new ToolApprovalAdapter(requireContext(), entries, settingsManager);
        listView.setAdapter(adapter);
    }

    private List<ToolApprovalAdapter.ToolApprovalEntry> buildEntries() {
        List<ToolApprovalAdapter.ToolApprovalEntry> entries = new ArrayList<>();

        Map<String, String> overrides = settingsManager.getAgentConfig()
                .getToolApprovalOverrides();

        // Build a lightweight registry just to enumerate tool names/descriptions.
        // We avoid constructing the full AgentLoop-owned registry to keep settings
        // navigation cheap. ToolRegistry can be created standalone with just context.
        ToolRegistry registry = null;
        try {
            registry = new ToolRegistry(requireContext(), settingsManager);
        } catch (Exception e) {
            android.util.Log.w("ToolApprovalSettings", "Could not build tool list", e);
        }

        if (registry == null) {
            return entries;
        }

        for (Tool tool : registry.getAllTools()) {
            ToolApprovalMode mode = ToolApprovalMode.DEFAULT;

            String toolName = tool.getName();
            if (overrides.containsKey(toolName)) {
                String value = overrides.get(toolName);
                try {
                    mode = ToolApprovalMode.valueOf(value);
                } catch (IllegalArgumentException ignored) {
                    // Unknown value — treat as DEFAULT
                }
            }

            String desc = tool.getDefinition().getFunction().getDescription();
            entries.add(new ToolApprovalAdapter.ToolApprovalEntry(toolName, desc, mode));
        }

        return entries;
    }
}
