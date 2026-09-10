package com.akylas.enforcedoze;
public class BlockNotificationsActivity extends AppListActivity {
    @Override protected String preferenceKey() { return "notificationBlockList"; }
    @Override protected int titleResource() { return R.string.notif_blocklist_setting_title; }
}
