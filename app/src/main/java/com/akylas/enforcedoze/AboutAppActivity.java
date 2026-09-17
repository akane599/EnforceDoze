package com.akylas.enforcedoze;
import android.os.Bundle;
import android.widget.LinearLayout;
public class AboutAppActivity extends BaseActivity {
    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle); screen("About EnforceDoze",true);
        card("EnforceDoze",BuildConfig.VERSION_NAME+" • build "+BuildConfig.VERSION_CODE+"\nA fork of ForceDoze by theblixguy. Free, open source, no ads.");
        LinearLayout project=card("Project & support","Local diagnostics are shared only when you choose to copy or share them.");
        button(project,"Source code",()->Utils.openUrl(this,"https://github.com/Akylas/EnforceDoze"));
        button(project,"Support the project",()->Utils.openUrl(this,"https://github.com/sponsors/farfromrefug"));
        button(project,"Translation contributors",()->Utils.openUrl(this,"https://hosted.weblate.org/engage/enforcedoze/"));
        button(project,"Licenses",()->message("Open-source licenses",getString(R.string.licenses_text)+"\n\nEnforceDoze: GNU GPL v3. AndroidX, Material Components and Shizuku API: Apache License 2.0."));
    }
}
