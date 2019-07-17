package com.example.mp_2019_android;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity{

    public static final String TAG = "MainActivity";

    /* Username field */
    private EditText enterUsername;
    public String username;

    /* SQLLiteOpenHelper */
    public MessageSQLiteOpenHelper myDataBaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myDataBaseHelper = new MessageSQLiteOpenHelper(this);
        enterUsername = findViewById(R.id.editText_enterUsername);
        setUsername(enterUsername);

        /* Open GroupChat Button init and listener */
        /* Buttons on main site */
        Button groupChatButton = findViewById(R.id.button_OpenChat);
        groupChatButton.setOnClickListener(v -> {
            saveUserName();

            Pair<String, String> extra = new Pair<>(ChatActivity.EXTRA_USERNAME, username);
            openActivity(ChatActivity.class, extra);
        });

        /* Open SingleChat Button init and listener */
        Button singleChatButton = findViewById(R.id.button_SingleChat);
        singleChatButton.setOnClickListener(v -> {
            saveUserName();
            Pair<String, String> extra = new Pair<>(SingleChatActivity.EXTRA_USERNAME, username);
            openActivity(SingleChatActivity.class, extra);
        });

        /* Open VideoChat Button init and listener */
        Button videoChatButton = findViewById(R.id.button_OpenVideoChat);
        videoChatButton.setOnClickListener(v -> {
            saveUserName();
            Pair<String, String> extra = new Pair<>(VoiceChatActivity.EXTRA_USERNAME, username);
            openActivity(VoiceChatActivity.class, extra);
        });
    }

    /* Used to save the username and a random generated 4-digit id in SQLLite */
    private void saveUserName() {
        enterUsername = findViewById(R.id.editText_enterUsername);
        username = enterUsername.getText().toString();
        String generatedId = "#" + generateID();

        Cursor data = myDataBaseHelper.getData(MessageSQLiteOpenHelper.TABLE_USER_NAME);
        int rowsInCursor = data.getCount();
        if(rowsInCursor <= 0){  // no userName in database? -> add new name and
                                // new generated id to database
            myDataBaseHelper.insertDataUserName(username, generatedId);
            username = createUserName(username, generatedId);
        }else { // already a userName in database? -> update the current name
                                    // in database to the new name but keep same id in database
            myDataBaseHelper.updateDataUserName(1, username);

            String savedId = getId(data);
            Log.d(TAG, "DataBase id: " + savedId);
            username = createUserName(username, savedId);
        }
        data.close();
    }

    /* Get the random 4-digit generated id from SQLLite */
    private String getId(Cursor cur) {
        //only read data from first row
        String id = "";
        while(cur.moveToNext()){
            //String a = data.getString(0);
            //String b = data.getString(1);
            id = cur.getString(2);
            //String d = data.getString(3);
            break;
        }
        return  id;
    }

    /* Used if the username field is blank. In this case we use the device name as username */
    private String createUserName(String name, String id) {
        return !(name.isEmpty()) ? name + id : android.os.Build.MODEL + id;
    }

    /* Used to open a different acticity */
    private void openActivity(){
        Context packageContext = getBaseContext();
        Intent intent = new Intent(packageContext, VoiceChatActivity.class);
        startActivity(intent);
    }

    /* Used to open a different activity with extra information's */
    private void openActivity(Class activityClass, Pair<String, String> extra){
        Context packageContext = getBaseContext();
        Intent intent = new Intent(packageContext, activityClass);
        intent.putExtra(extra.first, extra.second);
        startActivity(intent);
    }

    /* Used to generate a random 4-digit number */
    private String generateID(){
        int randomId = (int)(Math.random()*9000)+1000;
        return String.valueOf(randomId);
    }

    /* Used to save the username and generated 4-digit in different columns */
    private void setUsername(EditText userInput){
        String textToSt = getUsernameFromDB();
        Log.d(TAG, "getUsernameFromDB: " + textToSt);
        if(!(textToSt == null || textToSt.isEmpty())){
            userInput.setText(textToSt);
            //moves the edit text cursor behind the placed name
            userInput.setSelection(userInput.getText().length());
        }
    }

    /* Used to get the username from SQLLite */
    private String getUsernameFromDB(){
        String username = "";
        Cursor data = myDataBaseHelper.getData(MessageSQLiteOpenHelper.TABLE_USER_NAME);
        if(data.getCount() > 0){
            if(data.moveToNext()){
                username = data.getString(1);
                return username;
            }
        }
        return username;
    }
}
