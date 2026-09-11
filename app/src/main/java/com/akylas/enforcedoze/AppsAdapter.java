package com.akylas.enforcedoze;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Rows are diffed rather than fully rebound, so typing in the search box keeps the list steady. */
public class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.ViewHolder> {

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView appName;
        final TextView appPackageName;
        final ImageView appIcon;
        final View remove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            appName = itemView.findViewById(R.id.appName);
            appPackageName = itemView.findViewById(R.id.appPackageName);
            appIcon = itemView.findViewById(R.id.appIcon);
            remove = itemView.findViewById(R.id.removeApp);
        }
    }

    private final ArrayList<AppsItem> listData = new ArrayList<>();
    private java.util.function.Consumer<String> removeListener;
    private java.util.function.Consumer<String> selectListener;

    public void setOnRemoveListener(java.util.function.Consumer<String> listener) { removeListener = listener; }
    public void setOnSelectListener(java.util.function.Consumer<String> listener) { selectListener = listener; }

    public AppsAdapter(Context context, List<AppsItem> initial) {
        if (initial != null) listData.addAll(initial);
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    /** Replaces the visible rows, animating only what actually changed. */
    public void submit(List<AppsItem> next) {
        List<AppsItem> current = new ArrayList<>(listData);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return current.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return current.get(oldPosition).getAppPackageName().equals(next.get(newPosition).getAppPackageName());
            }
            @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return current.get(oldPosition).getAppName().equals(next.get(newPosition).getAppName());
            }
        }, false);
        listData.clear();
        listData.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemCount() {
        return listData.size();
    }

    @Nullable
    public AppsItem getItem(int position) {
        return listData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return listData.get(position).getAppPackageName().hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_row_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppsItem item = getItem(position);
        holder.appName.setText(item.getAppName());
        holder.appPackageName.setText(item.getAppPackageName());
        AppIcons.bind(holder.appIcon, item.getAppPackageName());
        holder.remove.setVisibility(removeListener == null ? View.GONE : View.VISIBLE);
        holder.remove.setContentDescription(holder.itemView.getContext().getString(R.string.remove_named_app, item.getAppName()));
        if (selectListener == null) {
            // setOnClickListener(null) still leaves the row clickable, which ripples for nothing.
            holder.itemView.setOnClickListener(null);
            holder.itemView.setClickable(false);
        } else {
            holder.itemView.setOnClickListener(v -> {
                int index = holder.getBindingAdapterPosition();
                if (index != RecyclerView.NO_POSITION) selectListener.accept(listData.get(index).getAppPackageName());
            });
        }
        holder.remove.setOnClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            if (index != RecyclerView.NO_POSITION && removeListener != null) removeListener.accept(listData.get(index).getAppPackageName());
        });
    }
}
