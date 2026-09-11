package com.akylas.enforcedoze;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;
import android.widget.ImageView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Row icons are loaded off the main thread and cached, so scrolling a few hundred apps never waits
 * on the package manager. Each request is tagged with its package; a recycled row drops stale loads.
 */
final class AppIcons {
    private static final LruCache<String, Drawable> CACHE = new LruCache<>(150);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            task.run();
        }, "app-icons");
        thread.setDaemon(true);
        return thread;
    });

    private AppIcons() {}

    static void bind(ImageView view, String pkg) {
        view.setTag(R.id.appIcon, pkg);
        Drawable cached = CACHE.get(pkg);
        if (cached != null) { view.setImageDrawable(cached); return; }
        view.setImageDrawable(null);
        PackageManager manager = view.getContext().getApplicationContext().getPackageManager();
        WORKER.execute(() -> {
            Drawable icon;
            try { icon = manager.getApplicationIcon(pkg); }
            catch (PackageManager.NameNotFoundException | RuntimeException e) { icon = null; }
            if (icon == null) return;
            Drawable loaded = icon;
            CACHE.put(pkg, loaded);
            MAIN.post(() -> { if (pkg.equals(view.getTag(R.id.appIcon))) view.setImageDrawable(loaded); });
        });
    }
}
