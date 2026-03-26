package io.finett.droidclaw.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.Provider;

public class ProvidersAdapter extends ListAdapter<Provider, ProvidersAdapter.ProviderViewHolder> {

    public interface OnProviderClickListener {
        void onProviderClick(Provider provider);
    }

    private OnProviderClickListener listener;

    private static final DiffUtil.ItemCallback<Provider> DIFF_CALLBACK = new DiffUtil.ItemCallback<Provider>() {
        @Override
        public boolean areItemsTheSame(@NonNull Provider oldItem, @NonNull Provider newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Provider oldItem, @NonNull Provider newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId())
                    && oldItem.getName() != null && oldItem.getName().equals(newItem.getName())
                    && oldItem.getBaseUrl() != null && oldItem.getBaseUrl().equals(newItem.getBaseUrl())
                    && oldItem.getModelCount() == newItem.getModelCount();
        }
    };

    public ProvidersAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnProviderClickListener(OnProviderClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProviderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_provider, parent, false);
        return new ProviderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProviderViewHolder holder, int position) {
        Provider provider = getItem(position);
        holder.bind(provider);
    }

    class ProviderViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView urlText;
        private final TextView modelCountText;

        ProviderViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.text_provider_name);
            urlText = itemView.findViewById(R.id.text_provider_url);
            modelCountText = itemView.findViewById(R.id.text_model_count);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onProviderClick(getItem(position));
                }
            });
        }

        void bind(Provider provider) {
            nameText.setText(provider.getName());
            urlText.setText(provider.getBaseUrl());
            
            int modelCount = provider.getModelCount();
            String modelCountStr = itemView.getContext().getString(R.string.provider_models_count, modelCount);
            modelCountText.setText(modelCountStr);
        }
    }
}