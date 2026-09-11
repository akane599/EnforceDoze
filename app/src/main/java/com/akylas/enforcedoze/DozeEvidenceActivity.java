package com.akylas.enforcedoze;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads saved observations only; opening this screen cannot change the device's idle state. */
public class DozeEvidenceActivity extends UiActivity {
    private final EvidenceAdapter adapter = new EvidenceAdapter();
    private TextView summary, empty;
    private MaterialButton copy, clear;
    private SharedPreferences saved;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> refresh();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        saved = DozeEvidence.preferences(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        MaterialToolbar toolbar = new MaterialToolbar(this); toolbar.setTitle(R.string.evidence_title); root.addView(toolbar);
        RecyclerView list = new RecyclerView(this); list.setId(R.id.evidenceList);
        list.setLayoutManager(new LinearLayoutManager(this)); list.setItemAnimator(null);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL);
        header.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
        int pad = Math.round(20 * getResources().getDisplayMetrics().density); header.setPadding(pad, pad, pad, pad);
        summary = new TextView(this); summary.setId(R.id.evidenceSummary);
        TextViewCompat.setTextAppearance(summary, com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        header.addView(summary);
        TextView intro = new TextView(this); intro.setText(R.string.evidence_explanation); intro.setPadding(0, pad / 2, 0, pad / 2); header.addView(intro);
        copy = new MaterialButton(this); copy.setText(R.string.evidence_copy); header.addView(copy);
        copy.setOnClickListener(v -> ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText(getString(R.string.evidence_title), DozeEvidence.report(this))));
        clear = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
        clear.setText(R.string.evidence_clear); header.addView(clear);
        clear.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setMessage(R.string.evidence_clear_question)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.evidence_clear, (dialog, which) -> DozeEvidence.clear(this)).show());
        empty = new TextView(this); header.addView(empty);
        list.setAdapter(new ConcatAdapter(new ListHeaderAdapter(header), adapter));
        setContentView(root); setSupportActionBar(toolbar); getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
    @Override protected void onStart() { super.onStart(); saved.registerOnSharedPreferenceChangeListener(listener); refresh(); }
    @Override protected void onStop() { saved.unregisterOnSharedPreferenceChangeListener(listener); super.onStop(); }
    private void refresh() {
        if (isDestroyed()) return;
        List<DozeObservation> records = DozeEvidence.read(this);
        Collections.reverse(records);
        DozeObservation confirmed = null;
        for (DozeObservation o : records) if (o.confirmedScreenOffIdle()) { confirmed = o; break; }
        summary.setText(confirmed == null ? getString(R.string.evidence_none_confirmed)
                : getString(R.string.evidence_last_confirmed, DozeEvidence.timestamp(confirmed)));
        String message = getString(records.isEmpty() ? R.string.evidence_empty : R.string.evidence_retention);
        if (!DozeEvidence.enabled(this)) message = getString(R.string.evidence_disabled) + "\n\n" + message;
        empty.setText(message); copy.setEnabled(!records.isEmpty()); clear.setEnabled(!records.isEmpty());
        adapter.records = records; adapter.notifyDataSetChanged();
    }
    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    private static class Holder extends RecyclerView.ViewHolder {
        final TextView title, details;
        Holder(View view) { super(view); title = view.findViewById(R.id.evidenceTitle); details = view.findViewById(R.id.evidenceDetails); }
    }
    private static class EvidenceAdapter extends RecyclerView.Adapter<Holder> {
        List<DozeObservation> records = new ArrayList<>();
        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_doze_evidence, parent, false));
        }
        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            DozeObservation o = records.get(position);
            holder.title.setText(o.confirmedScreenOffIdle() ? R.string.evidence_confirmed : R.string.evidence_observation);
            holder.details.setText(DozeEvidence.details(holder.itemView.getContext(), o));
        }
        @Override public int getItemCount() { return records.size(); }
    }
}
