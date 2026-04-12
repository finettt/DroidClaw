package io.finett.droidclaw.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.R;

public class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.SettingsViewHolder> {

    public static class SettingsItem {
        private final String id;
        private final int iconResId;
        private final String title;
        private final String subtitle;
        private final boolean showChevron;

        public SettingsItem(String id, int iconResId, String title, String subtitle, boolean showChevron) {
            this.id = id;
            this.iconResId = iconResId;
            this.title = title;
            this.subtitle = subtitle;
            this.showChevron = showChevron;
        }

        public String getId() {
            return id;
        }

        public int getIconResId() {
            return iconResId;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public boolean isShowChevron() {
            return showChevron;
        }
    }

    public interface OnSettingsItemClickListener {
        void onSettingsItemClick(SettingsItem item);
    }

    private final List<SettingsItem> items = new ArrayList<>();
    private OnSettingsItemClickListener listener;

    public SettingsAdapter() {
    }

    public void setOnSettingsItemClickListener(OnSettingsItemClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<SettingsItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SettingsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_settings_row, parent, false);
        return new SettingsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SettingsViewHolder holder, int position) {
        SettingsItem item = items.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class SettingsViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconImage;
        private final TextView titleText;
        private final TextView subtitleText;
        private final TextView chevronText;

        SettingsViewHolder(@NonNull View itemView) {
            super(itemView);
            iconImage = itemView.findViewById(R.id.icon_image);
            titleText = itemView.findViewById(R.id.text_title);
            subtitleText = itemView.findViewById(R.id.text_subtitle);
            chevronText = itemView.findViewById(R.id.text_chevron);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSettingsItemClick(items.get(position));
                }
            });
        }

        void bind(SettingsItem item) {
            iconImage.setImageResource(item.getIconResId());
            titleText.setText(item.getTitle());
            subtitleText.setText(item.getSubtitle());
            chevronText.setVisibility(item.isShowChevron() ? View.VISIBLE : View.GONE);
        }
    }
}