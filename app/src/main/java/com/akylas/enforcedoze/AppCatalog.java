package com.akylas.enforcedoze;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Package metadata must never hold up the queue that restores device settings. */
final class AppCatalog {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task ->
            new Thread(() -> {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                task.run();
            }, "app-catalog"));

    interface Callback { void complete(ArrayList<AppsItem> apps, String error); }

    static Future<?> load(Context context, Set<String> packages, Callback callback) {
        PackageManager manager = context.getApplicationContext().getPackageManager();
        return WORKER.submit(() -> {
            ArrayList<AppsItem> apps = new ArrayList<>();
            String failure = null;
            try {
                if (packages == null) {
                    for (ApplicationInfo info : manager.getInstalledApplications(0)) {
                        if (Thread.currentThread().isInterrupted()) return;
                        apps.add(item(manager, info, info.packageName));
                    }
                } else {
                    for (String pkg : packages) {
                        if (Thread.currentThread().isInterrupted()) return;
                        ApplicationInfo info = null;
                        try { info = manager.getApplicationInfo(pkg, 0); }
                        catch (PackageManager.NameNotFoundException ignored) { }
                        apps.add(item(manager, info, pkg));
                    }
                }
                Collator collator = Collator.getInstance();
                apps.sort((a, b) -> {
                    int order = collator.compare(a.getAppName(), b.getAppName());
                    return order != 0 ? order : a.getAppPackageName().compareTo(b.getAppPackageName());
                });
            } catch (RuntimeException e) { failure = e.toString(); }
            if (Thread.currentThread().isInterrupted()) return;
            String error = failure;
            MAIN.post(() -> callback.complete(apps, error));
        });
    }

    private static AppsItem item(PackageManager manager, ApplicationInfo info, String pkg) {
        String label = pkg;
        try { if (info != null) label = info.loadLabel(manager).toString(); }
        catch (RuntimeException ignored) { /* One broken label must not hide every other app. */ }
        AppsItem item = new AppsItem();
        item.setAppName(label);
        item.setAppPackageName(pkg);
        return item;
    }
}
