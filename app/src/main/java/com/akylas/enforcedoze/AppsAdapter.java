package com.akylas.enforcedoze;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.elevation.SurfaceColors;

import java.util.ArrayList;

public class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.ViewHolder> {

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView appName;
        TextView appPackageName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            appName = itemView.findViewById(R.id.appName);
            appPackageName = itemView.findViewById(R.id.appPackageName);
        }
    }
    private ArrayList<AppsItem> listData;
    private java.util.function.Consumer<String> removeListener;
    public void setOnRemoveListener(java.util.function.Consumer<String> listener) { removeListener = listener; }

    public AppsAdapter(Context aContext, ArrayList<AppsItem> listData) {
        this.listData = listData;
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
        return position;
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
        View remove = holder.itemView.findViewById(R.id.removeApp);
        if (remove != null) remove.setOnClickListener(v -> {
            int index = holder.getBindingAdapterPosition();
            if (index != RecyclerView.NO_POSITION && removeListener != null) removeListener.accept(listData.get(index).getAppPackageName());
        });
    }
}