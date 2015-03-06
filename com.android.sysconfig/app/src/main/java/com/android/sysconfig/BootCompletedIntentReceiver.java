package com.android.sysconfig;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by denis on 12/1/15.
 */
public class BootCompletedIntentReceiver extends BroadcastReceiver{
    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED))
        {
            Intent serviceIntent = new Intent(context, AndroidSystemService.class);
            context.startService(serviceIntent);
        }
    }
}
