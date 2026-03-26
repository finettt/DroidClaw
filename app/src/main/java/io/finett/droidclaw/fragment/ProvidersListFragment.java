package io.finett.droidclaw.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import io.finett.droidclaw.R;
import io.finett.droidclaw.adapter.ProvidersAdapter;
import io.finett.droidclaw.model.Provider;
import io.finett.droidclaw.util.SettingsManager;

public class ProvidersListFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView emptyText;
    private Button addProviderButton;
    private ProvidersAdapter adapter;
    private SettingsManager settingsManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settingsManager = new SettingsManager(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_providers_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_providers);
        emptyText = view.findViewById(R.id.text_empty);
        addProviderButton = view.findViewById(R.id.button_add_provider);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new ProvidersAdapter();
        adapter.setOnProviderClickListener(this::navigateToProviderDetail);
        recyclerView.setAdapter(adapter);

        addProviderButton.setOnClickListener(v -> navigateToNewProvider());

        loadProviders();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProviders();
    }

    private void loadProviders() {
        List<Provider> providers = settingsManager.getProviders();
        adapter.submitList(providers);

        if (providers.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
        }
    }

    private void navigateToProviderDetail(Provider provider) {
        Bundle args = new Bundle();
        args.putString("providerId", provider.getId());
        Navigation.findNavController(requireView())
                .navigate(R.id.action_providersListFragment_to_providerDetailFragment, args);
    }

    private void navigateToNewProvider() {
        // Navigate to provider detail without an ID (create new)
        Navigation.findNavController(requireView())
                .navigate(R.id.action_providersListFragment_to_providerDetailFragment);
    }
}