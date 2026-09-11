package com.akylas.enforcedoze;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** One persistent editor header followed by the recyclable app rows. */
final class ListHeaderAdapter extends RecyclerView.Adapter<ListHeaderAdapter.Holder> {
    private final View header;
    ListHeaderAdapter(View header) { this.header = header; }
    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View view) { super(view); }
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) { return new Holder(header); }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) { }
    @Override public int getItemCount() { return 1; }
}
