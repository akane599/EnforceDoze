# SPDX-License-Identifier: GPL-3.0-or-later
# Shizuku and libsu load these classes by name (WorkerContract) in the worker process.
-keep class io.github.akane599.enforcedoze.privilege.worker.DozeWorker { public <init>(android.content.Context); }
-keep class io.github.akane599.enforcedoze.privilege.worker.DozeRootService { public <init>(); }

# Hidden framework classes the worker calls through :core:hidden-api-stubs (compileOnly). They are on
# the device boot classpath, not in the APK, so R8 must not fail on them; HiddenSensorPrivacy catches
# their absence at run time.
-dontwarn android.os.ServiceManager
-dontwarn android.hardware.ISensorPrivacyManager
-dontwarn android.hardware.ISensorPrivacyManager$Stub
