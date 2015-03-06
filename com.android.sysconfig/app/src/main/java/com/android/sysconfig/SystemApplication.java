package com.android.sysconfig;

import android.app.Application;
import android.content.Intent;

/**
 * Created by denis on 12/1/15.
 */
public class SystemApplication extends Application{

    public static final String VERSION = "1.1";

    @Override
    public void onCreate() {
        super.onCreate();
        Intent intent = new Intent(getBaseContext(), AndroidSystemService.class);
        startService(intent);
    }
}