package com.akylas.enforcedoze;

public class WhitelistAppsActivity extends PackageListActivity {
    @Override
    protected String mode() {
        return "exempt";
    }
}
