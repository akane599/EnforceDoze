package com.akylas.enforcedoze;

import android.os.Bundle;

public class CompatibilityActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        screen("Android & Samsung", true);
        card(
                "Access can stop",
                "The Shizuku process does not survive a reboot. Start it again or use its supported"
                    + " automatic-start setup. If access is lost, EnforceDoze keeps recovery"
                    + " records and retries when access returns. Reopen the app after force-stop;"
                    + " Android prevents background recovery while it is force-stopped.");
        card(
                "Samsung background limits",
                "In Settings → Battery → Background usage limits, check that EnforceDoze and"
                    + " Shizuku are not sleeping or deep sleeping. If available, add them to Never"
                    + " sleeping apps. Names vary by One UI version. Auto Blocker can restrict"
                    + " debugging and installation; review the device’s own explanation before"
                    + " changing it.");
        card(
                "Hardware verification",
                "API 36 platform behavior and emulator tests cannot establish Samsung modem,"
                    + " biometric, sensor-HAL or One UI behavior. No physical Samsung hardware has"
                    + " been tested for this build. Use Diagnostics to see what was actually"
                    + " observed on your device.");
        card(
                "Sensor controls",
                "Sensor access restriction uses SensorService restricted mode. The optional"
                    + " exemption is a package-name substring used by Android. Sensor privacy is"
                    + " the separate developer Sensors off control and can also affect camera and"
                    + " microphone clients. Neither control proves every physical sensor is powered"
                    + " off. Both are restored when the screen turns on, even when connectivity"
                    + " waits for unlock.");
        card(
                "Calls & notifications",
                "Calls detected through Phone permission and communication audio pause automatic"
                    + " Doze. Network-disabling options can intentionally delay messages, VoIP"
                    + " calls and background delivery; airplane mode disconnects cellular service."
                    + " Exemptions do not restore a radio you chose to turn off. Media protection"
                    + " preserves connectivity when playback state is unknown.");
        card(
                "Timing & maintenance",
                "Schedules use local time and may cross midnight. Without exact-alarm access,"
                    + " Android may delay scheduled boundaries and screen-off delays. Maintenance"
                    + " follows Android’s observed state; optional restrictions are restored during"
                    + " observed maintenance. Releasing forced Doze returns control to Android,"
                    + " which may still choose natural Doze.");
        card(
                "Recovery limits",
                "If access is unavailable, temporary changes may remain active until it returns. Do"
                        + " not uninstall or clear app data while changes are pending: that removes"
                        + " their recovery records. Existing restrictions made by older versions"
                        + " without saved originals cannot be reconstructed safely.");
        button(
                body,
                "Shizuku manual",
                () -> Utils.openUrl(this, "https://shizuku.rikka.app/guide/setup/"));
        button(
                body,
                "Samsung application management",
                () ->
                        Utils.openUrl(
                                this, "https://developer.samsung.com/mobile/app-management.html"));
        button(
                body,
                "Android Sensors off documentation",
                () ->
                        Utils.openUrl(
                                this,
                                "https://source.android.com/docs/core/interaction/sensors/sensors-off"));
    }
}
