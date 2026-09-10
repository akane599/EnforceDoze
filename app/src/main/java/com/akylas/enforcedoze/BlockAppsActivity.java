package com.akylas.enforcedoze;
public class BlockAppsActivity extends AppListActivity {
    @Override protected String preferenceKey() { return "dozeAppBlockList"; }
    @Override protected int titleResource() { return R.string.app_blocklist_setting_title; }
}
