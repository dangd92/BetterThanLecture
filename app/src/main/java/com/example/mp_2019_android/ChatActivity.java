package com.example.mp_2019_android;

import android.database.Cursor;
import android.os.Bundle;

import android.os.Handler;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import android.text.Editable;
import android.text.Selection;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.nearby.connection.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ChatActivity extends ConnectionsActivity {

    private MessageSQLiteOpenHelper myDataBaseHelper;
    private State state = State.UNKNOWN;

    public enum State {
        UNKNOWN,
        SEARCHING,
        SEARCHING_AVERTISTING,
        CONNECTED,
        STOP
    }

    private static boolean DEBUG = true;
    private static boolean ShowDEBUG = false;

    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    private static final String SERVICE_ID = "BetterThanLecture_CHAT";

    private int errorCounter = 0;
    private String localEndpointName;

    Payload bytesPayload; // Payload.fromBytes(new byte[] {0xa, 0xb, 0xc, 0xd});

    public final static String EXTRA_USERNAME = "EXTRA_USERNAME";

    Message sendMessage;
    String reset;
    private LinkedList<Message> messages;

    private EditText messageEditText;
    private ConstraintLayout debugArea;
    private TextView connectedDevices;
    private TextView debugLogView;
    private RecyclerView messagesRecyclerView;
    private  MessageAdapter messageAdapter;
    private MenuItem menuItemToggle;
    private ProgressBar loadingBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        /* Get Username from intend */
        Bundle extras = getIntent().getExtras();
        if(extras != null){
            String username = extras.getString(EXTRA_USERNAME);
            logd(TAG,"Username: " + username);
            localEndpointName = username;
        }

        /* Init of class variables */
        debugArea = findViewById(R.id.debugArea);

        debugLogView = findViewById(R.id.debug_log);
        debugLogView.setVisibility(debugVisibility());
        debugLogView.setMovementMethod(new ScrollingMovementMethod());

        connectedDevices = findViewById(R.id.connectedDevices);
        connectedDevices.setVisibility(debugVisibility());
        connectedDevices.setMovementMethod(new ScrollingMovementMethod());

        messageEditText = findViewById(R.id.editText_message);
        ImageButton sendButton = findViewById(R.id.button_sendMessage);
        reset = "";

        myDataBaseHelper = new MessageSQLiteOpenHelper(this);

        messages = new LinkedList<>();

        messageAdapter = new MessageAdapter(messages, localEndpointName, this);

        messagesRecyclerView = findViewById(R.id.conversation);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        messagesRecyclerView.setAdapter(messageAdapter);

        loadingBar = findViewById(R.id.loadingBar);
        loadingBar.setVisibility(View.INVISIBLE);

        /* Restore messages from db */
        LinkedList<Message> dbMessages = loadChatMessagesFromDatabase();
        for(Message msg : dbMessages) {
            messages.add(msg);
            messageAdapter.notifyDataSetChanged();
        }
        scrollRecyclerViewToEnd();

        logd(TAG, "onCreate");

        /* Send on SendButton click */
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        /* ActionListener for KeyboardSendButton */
        messageEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessage();
                    handled = true;
                }
                return handled;
            }
        });

        /* Show Debug site if true */
        showDebugView(ShowDEBUG);
    }

    @Override
    protected void onStart() {
        logd(TAG,"onStart");
        super.onStart();
    }

    @Override
    protected void onPause() {
        deleteAllRowsOfTable();
        saveChatMessagesToDatabase(messages);
        logd(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        logd(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        logd(TAG, "onDestroy");
        super.onDestroy();
        disconnectFromAllEndpoints();
        stopAllEndpoints();
        setState(State.STOP);
    }

    @Override
    protected  void onResume() {
        logd(TAG, "onResume");
        super.onResume();
        setState(State.SEARCHING);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        logd(TAG,"onCreateOptionsMenu: ");

        getMenuInflater().inflate(R.menu.menu_vertical_dropdown, menu);
        menuItemToggle = menu.findItem(R.id.menu_menuItemShowDebug);
        menuItemToggle.setChecked(ShowDEBUG);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.menu_clearMessageHistory:
                deleteAllRowsOfTable();
                messages.clear();
                messageAdapter.notifyDataSetChanged();
                scrollRecyclerViewToEnd();
                break;
            case R.id.menu_menuItemShowDebug:
                ShowDEBUG = !ShowDEBUG;
                showDebugView(ShowDEBUG);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

     /* Called if a remote endpoint is discovered */
    @Override
    protected void onEndpointDiscovered(final Endpoint endpoint) {
        logd(TAG, "onEndpointDiscovered");
        if(!isConnecting) {
            connectToEndpoint(endpoint);
        }

    }

    /* Called if a pending connection with a remote endpoint is created */
    @Override
    protected void onConnectionInitiated(Endpoint endpoint, ConnectionInfo connectionInfo) {
        logd(TAG, "onConnectionInitiated");
        acceptConnection(endpoint);
    }

    /* Called if a connection with this endpoint has failed */
    @Override
    protected void onConnectionFailed(final Endpoint endpoint) {
        logd(TAG, "onConnectionFailed ErrorCounter: " + errorCounter);
        if(discoveredEndpoints.containsValue(endpoint) && errorCounter <= 3) {
            Handler handler = new Handler();
            handler.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            connectToEndpoint(endpoint);
                        }
                    },
                    getRandomValueInRange(0, 5000));
            errorCounter++;
        }
        else if (errorCounter > 3) {
            discoveredEndpoints.remove(endpoint);
            errorCounter = 0;
        }
    }

    /* Called if someone has connected to us */
    @Override
    protected void onEndpointConnected(Endpoint endpoint) {
        logd(TAG, "onEndpointConnected");
        if(!establishedConnections.isEmpty()) {
            setState(State.CONNECTED);
        }
        printConnectedDevices();
    }

    /* Called if someone has disconnected */
    @Override
    protected void onEndpointDisconnected(final Endpoint endpoint) {
        logd(TAG, "onEndpointDisconnected");
        if(establishedConnections.isEmpty()){
            setState(State.SEARCHING);
        }
        if(discoveredEndpoints.containsValue(endpoint)) {
            Handler handler = new Handler();
            handler.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            connectToEndpoint(endpoint);
                        }
                    },
                    getRandomValueInRange(0, 5000));
        }
        printConnectedDevices();
    }

     /* Called if a payload is received */
    @Override
    protected  void onPayloadReceived(Endpoint endpoint, Payload payload) {
        if(payload.getType() == Payload.Type.BYTES){
            processBytePayload(payload);
        }
    }

    /* Called if discovery successfully starts */
    @Override
    protected void onDiscoveryStarted() {
        logd(TAG, "Waiting to find devices...");
    }

    private void processBytePayload(Payload payload){
        Object obj = getSerializedObjectOnReceive(payload);
        Message receivedMsg = safeCast(obj, Message.class);
        addMessageToView(receivedMsg);
        broadcastMessage(receivedMsg);
    }

    private Object getSerializedObjectOnReceive(Payload payload) {
        try {
            byte[] receivedBytes = payload.asBytes();
            return SerializationHelper.deserialize(receivedBytes);
        } catch (IOException | ClassNotFoundException e) {
            e.getMessage();
        }
        return null;
    }

    /* Method to add message to LinkedList and update UI */
    private void addMessageToView(Message receivedMsg){
        try {
            if (receivedMsg != null) {
                if (!messages.contains(receivedMsg)) {
                    messages.add(receivedMsg);
                    messageAdapter.notifyDataSetChanged();
                    scrollRecyclerViewToEnd();

                    logd(TAG,
                            "onPayloadReceived(EndpointName '" + receivedMsg.getSourceEndpointName() + "\n" +
                                    "Message: " + receivedMsg.getMessage() + "\n" +
                                    "Time: " + receivedMsg.getCurrentTime()
                    );
                } else {
                    logd(TAG, "Message already received!");
                }
            }else{
                logd(TAG,"receivedMesssage = null");
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    /* Send Message forward to other connected devices */
    private void broadcastMessage(Message receivedMsg){
        if (receivedMsg != null) {
            receivedMsg.receivedEndpoints.add(localEndpointName);
            sendSerializedObjectPayload(receivedMsg, getEstablishedConnectionsWithoutEndpointID(receivedMsg));
        }
    }

    /* Used to serialze the message object and to send the payload via Nearby Connections */
    public void sendSerializedObjectPayload(Object object, Set<String> endpoints){
        try {
            sendPayload(
                    Payload.fromBytes(
                            SerializationHelper.serialize(object)
                    ),
                    endpoints
            );
        } catch (IOException e) {
            e.getMessage();
        }
    }

    /* used for safe type casting */
    private static <T> T safeCast(Object object, Class<T> classType) {
        return object != null && classType != null && classType.isInstance(object) ? classType.cast(object) : null;
    }

    /* Method to set a new state */
    private void setState(State state) {
        printConnectedDevices();
        if (this.state == state) {
            logd(TAG, "State set to " + state + " but already in that state");
            Objects.requireNonNull(getSupportActionBar()).setTitle(state.toString() + " (" + establishedConnections.size() + ")");
            return;
        }

        logd(TAG, "State set to " + state);
        this.state = state;

        Objects.requireNonNull(getSupportActionBar()).setTitle(state.toString() + " (" + establishedConnections.size() + ")");

        onStateChanged(this.state);
    }

    /* Called if the state variable is changed */
    private void onStateChanged(State newState) {
        switch (newState) {
            case SEARCHING:

                if(!isDiscovering) {
                    startDiscovering();
                }
                if(!establishedConnections.isEmpty()) {
                    setState(State.CONNECTED);
                    break;
                }

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(discoveredEndpoints.isEmpty()) {
                            setState(State.SEARCHING_AVERTISTING);
                        }
                    }
                }, 5000);

                errorCounter = 0;
                loadingBar.setVisibility(View.VISIBLE);
                break;

            case SEARCHING_AVERTISTING:
                if(!isAdvertising) {
                    startAdvertising();
                }
                if(!establishedConnections.isEmpty()) {
                    setState(State.CONNECTED);
                    break;
                }

                loadingBar.setVisibility(View.VISIBLE);
                break;

            case CONNECTED:
                handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if(!isAdvertising) {
                            startAdvertising();
                        }
                    }
                }, 3000 * discoveredEndpoints.size());

                loadingBar.setVisibility(View.INVISIBLE);
                break;

            case STOP:
                stopAdvertising();
                stopDiscovering();
                break;

            case UNKNOWN:
                break;

            default:
                // no-op
                break;
        }
    }

    /* Send Message payload */
    private void sendMessage() {
        //get the users message from the EditText input field
        sendMessage = new Message(messageEditText.getText().toString(), localEndpointName, MessageType.Text);

        //check if the message is not empty
        if (!sendMessage.getMessage().isEmpty() && !establishedConnections.isEmpty()) {
            //add the new string to the LinkedList
            messages.add(sendMessage);
            //update and scroll the MessageAdapter
            messageAdapter.notifyDataSetChanged();
            scrollRecyclerViewToEnd();

            try {
                bytesPayload = Payload.fromBytes(SerializationHelper.serialize(sendMessage));
                sendPayload(bytesPayload, establishedConnections.keySet());
            } catch (IOException e) {
                e.getMessage();
            }

            //Reset EditText input field
            messageEditText.setText(reset);
        }
    }

    /* Scroll RecyclerView to the end */
    private void scrollRecyclerViewToEnd(){
        if(messageAdapter.getItemCount() - 1 > 0){
            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }

    /* Method for storing all messages into the SQLLite DB */
    private void saveChatMessagesToDatabase(LinkedList<Message> messages){
        if(messages.size() >= 1){
            Log.d(TAG, "saveChatMessagesToDatabase: trying to save messages to DataBase");
            for(Message msgToSave : messages){
                boolean insertedData = myDataBaseHelper.insertDataChatHistory(
                        msgToSave.getMessage(),
                        msgToSave.getSourceEndpointName(),
                        msgToSave.getCurrentTime(),
                        msgToSave.getMessageType()
                );
                if(insertedData){
                    Log.d(TAG, "saveChatMessagesToDatabase: success (saved message to DataBase)");
                }else{
                    Log.d(TAG, "saveChatMessagesToDatabase: failure (did not save message to DataBase)");
                }
            }
        }else{
            Log.d(TAG, "saveChatMessagesToDatabase: there are no messages to save to DataBase");
        }
    }

    /* Used to delete all messages in SQLLite DB */
    private void deleteAllRowsOfTable() {
        boolean deletedRow = myDataBaseHelper.deleteAllRowsOfTable(MessageSQLiteOpenHelper.TABLE_CHAT_HISTORY);
        if(deletedRow){
            Log.d(TAG, "deleteAllRowsOfTable: deleted at least one row from table: " + MessageSQLiteOpenHelper.TABLE_CHAT_HISTORY);
        }else{
            Log.d(TAG, "deleteAllRowsOfTable: nothing was deleted from table: " + MessageSQLiteOpenHelper.TABLE_CHAT_HISTORY);
        }
    }

    /* Used to load messages from SQLLite DB */
    private LinkedList<Message> loadChatMessagesFromDatabase(){
        LinkedList<Message> messages = new LinkedList<>();
        Cursor data = myDataBaseHelper.getData(MessageSQLiteOpenHelper.TABLE_CHAT_HISTORY);
        if(data.getCount() > 0){
            Log.d(TAG, "loadChatMessagesFromDatabase: trying to load messages from DataBase");
            while(data.moveToNext()){
                //colmunIndex = 0 will only return the id,
                //colmunIndex = 1 will only return the message string
                Message msgToLoad = new Message(
                        data.getString(1),
                        data.getString(2),
                        data.getString(3),
                        MessageType.fromId(data.getInt(4))
                );
                Log.d(TAG, "loadChatMessagesFromDatabase: success (loaded message from DataBase)");
                messages.add(msgToLoad);
            }
            data.close();
        }else{
            Log.d(TAG, "loadChatMessagesFromDatabase: there are no messages to load from DataBase");
        }
        return messages;
    }

    /* Method to get Set of endpoints, who hasn't received the incoming message yet */
    protected Set<String> getEstablishedConnectionsWithoutEndpointID(Message forwardedMessage) {
        Map<String, Endpoint> establishedConnectionsWithoutEndpoint = new HashMap<>();

        for(Map.Entry<String, Endpoint> ep : establishedConnections.entrySet()) {
            if(!forwardedMessage.receivedEndpoints.contains(ep.getValue().getName())) {
                establishedConnectionsWithoutEndpoint.put(ep.getKey(), ep.getValue());
            }
        }
        return establishedConnectionsWithoutEndpoint.keySet();
    }

    /* Used to show debug Textview and connectedDevices Textview */
    private void showDebugView(boolean isDebug) {
        if(isDebug){
            if(menuItemToggle != null){
                menuItemToggle.setChecked(isDebug);
            }
            messagesRecyclerView.setVisibility(View.INVISIBLE);
            debugArea.setVisibility(View.VISIBLE);
        }else{
            if(menuItemToggle != null){
                menuItemToggle.setChecked(isDebug);
            }
            messagesRecyclerView.setVisibility(View.VISIBLE);
            debugArea.setVisibility(View.INVISIBLE);
        }
    }

    /**** Log methods ****/
    /* Used for printing logs in debug console and on the debug textview */
    @Override
    protected void logd(String TAG, String msg){
        super.logd(TAG,msg);
        appendToLogs(msg);
    }
    @Override
    protected void logw(String TAG, String msg){
        super.logw(TAG,msg);
        appendToLogs(msg);
    }
    @Override
    protected void logw(String TAG, String msg, Throwable e){
        super.logw(TAG,msg,e);
        appendToLogs(msg);
    }

    /* Changes the visibility of DEBUG */
    private int debugVisibility(){
        return DEBUG ? View.VISIBLE : View.GONE;
    }

    /* Used to update the connectedDevices Textview for UI */
    private void printConnectedDevices() {
        String toAppend;
        if(connectedDevices != null){
            connectedDevices.setText(null);
        }
        for(Map.Entry<String, Endpoint> endpoint : establishedConnections.entrySet()) {
            toAppend = "ID: " + endpoint.getValue().getId() + "\nName: " + endpoint.getValue().getName() + "\n";
            appendTextAndScroll(connectedDevices, toAppend);
        }
    }

    /* Used to append a log to the debug TextView for UI */
    private void appendToLogs(String msg) {
        String toAppend = DateFormat.format("mm:ss", System.currentTimeMillis()) + ": " + msg + "\n";
        appendTextAndScroll(debugLogView,toAppend);
    }

    /* Used to Scroll a Textview to the end */
    private void appendTextAndScroll(TextView textView, String string){
        if(textView != null){
            textView.append(string);
            Editable editable = textView.getEditableText();
            Selection.setSelection(editable, editable.length());
        }
    }

    /* Generates a random value. Used for random time delays in ErrorHandling and ConnectionHandling */
    private long getRandomValueInRange(long min, long max){
        return (long)(Math.random()*((max - min) + 1))+ min;
    }

    @Override
    protected String getName() {
        return this.localEndpointName;
    }

    /** {@see ConnectionsActivity#getServiceId()} */
    @Override
    public String getServiceId() {
        return SERVICE_ID;
    }

    /** {@see ConnectionsActivity#getStrategy()} */
    @Override
    public Strategy getStrategy() {
        return STRATEGY;
    }
}
