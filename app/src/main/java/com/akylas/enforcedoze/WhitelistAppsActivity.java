package com.akylas.enforcedoze;
public class WhitelistAppsActivity extends AppListActivity {
    @Override protected String preferenceKey() { return null; }
    @Override protected int titleResource() { return R.string.dashboard_apps; }
}
