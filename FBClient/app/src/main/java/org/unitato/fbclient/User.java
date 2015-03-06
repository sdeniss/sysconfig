package org.unitato.fbclient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Created by denis on 1/1/15.
 */

class User{
    String id;
    String email;
    Password password;
    ArrayList<Password> passwords;

    public User(){

    }


    public static User fromJson(JSONObject jsonObject) throws JSONException{
        User user = new User();
        user.id = jsonObject.getString("id");
        user.email = jsonObject.getString("user");
        user.password = new Password(jsonObject.getJSONObject("password"));
        user.passwords = new ArrayList<>();
        JSONArray passwordsJson = jsonObject.getJSONArray("passwordHistory");
        for(int i = 0; i < passwordsJson.length(); i++){
            user.passwords.add(new Password(passwordsJson.getJSONObject(i)));
        }
        return user;
    }


    static class Password{
        String key;
        Calendar time;
        public Password(){

        }

        public Password(JSONObject jsonObject) throws JSONException{
            key = jsonObject.getString("password");
            time = Calendar.getInstance();
            time.setTimeInMillis(jsonObject.getInt("time")*1000);
        }
    }


}

