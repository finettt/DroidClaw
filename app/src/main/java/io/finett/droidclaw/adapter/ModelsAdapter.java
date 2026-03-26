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
import io.finett.droidclaw.model.Model;

public class ModelsAdapter extends ListAdapter<Model, ModelsAdapter.ModelViewHolder> {

    public interface OnModelClickListener {
        void onModelClick(Model model);
    }

    private OnModelClickListener listener;

    private static final DiffUtil.ItemCallback<Model> DIFF_CALLBACK = new DiffUtil.ItemCallback<Model>() {
        @Override
        public boolean areItemsTheSame(@NonNull Model oldItem, @NonNull Model newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Model oldItem, @NonNull Model newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId())
                    && oldItem.getName() != null && oldItem.getName().equals(newItem.getName())
                    && oldItem.getContextWindow() == newItem.getContextWindow();
        }
    };

    public ModelsAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnModelClickListener(OnModelClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_model, parent, false);
        return new ModelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ModelViewHolder holder, int position) {
        Model model = getItem(position);
        holder.bind(model);
    }

    class ModelViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView contextText;

        ModelViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.text_model_name);
            contextText = itemView.findViewById(R.id.text_model_context);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onModelClick(getItem(position));
                }
            });
        }

        void bind(Model model) {
            nameText.setText(model.getName());
            
            String contextStr = itemView.getContext().getString(
                    R.string.model_context_window_tokens, model.getContextWindow());
            contextText.setText(contextStr);
        }
    }
}