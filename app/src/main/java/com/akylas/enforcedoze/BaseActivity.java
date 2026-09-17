package com.akylas.enforcedoze;

import android.content.*;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.*;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Shared responsive surfaces, touch targets and Android 16 edge-to-edge insets. */
public class BaseActivity extends AppCompatActivity {
    protected LinearLayout body, root;
    protected MaterialToolbar toolbar;

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    protected void screen(String title, boolean back) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(
                MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0));
        toolbar = new MaterialToolbar(this);
        toolbar.setTitle(title);
        toolbar.setMinimumHeight(dp(56));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        setSupportActionBar(toolbar);
        if (back) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(12), dp(20), dp(28));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        boolean light =
                (getResources().getConfiguration().uiMode
                                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat barsController =
                new WindowInsetsControllerCompat(getWindow(), root);
        barsController.setAppearanceLightStatusBars(light);
        barsController.setAppearanceLightNavigationBars(light);
        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (v, insets) -> {
                    androidx.core.graphics.Insets bars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.displayCutout()
                                            | WindowInsetsCompat.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
        body.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or, ob) -> {
                    int side = Math.max(dp(20), (r - l - dp(800)) / 2);
                    if (body.getPaddingLeft() != side) body.setPadding(side, dp(12), side, dp(28));
                });
    }

    protected LinearLayout card(String title, String description) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(20));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(
                MaterialColors.getColor(
                        this, com.google.android.material.R.attr.colorOutlineVariant, 0));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.addView(content);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(16);
        body.addView(card, lp);
        if (title != null) text(content, title, 22, true);
        if (description != null) text(content, description, 16, false);
        return content;
    }

    protected TextView text(LinearLayout parent, String value, int size, boolean heading) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setLineSpacing(dp(3), 1);
        view.setTextColor(
                MaterialColors.getColor(
                        this, com.google.android.material.R.attr.colorOnSurface, 0));
        if (heading) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
            ViewCompat.setAccessibilityHeading(view, true);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        parent.addView(view, lp);
        return view;
    }

    protected MaterialButton button(LinearLayout parent, String label, Runnable action) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setSingleLine(false);
        button.setMaxLines(4);
        button.setOnClickListener(v -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    protected void open(Class<?> screen) {
        startActivity(new Intent(this, screen));
    }

    protected void message(String title, String detail) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(detail)
                .setPositiveButton("Close", null)
                .show();
    }

    protected void safeStart(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            message("Unavailable", "This device does not provide that settings screen.");
        }
    }

    protected void copy(String label, String value) {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText(label, value));
        if (android.os.Build.VERSION.SDK_INT < 33)
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }
}
