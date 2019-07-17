package com.example.mp_2019_android;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import static android.media.AudioManager.*;
import static android.os.Process.THREAD_PRIORITY_AUDIO;
import static android.os.Process.setThreadPriority;

import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Objects;

public class VoiceChatActivity extends ConnectionsActivity {

    private State state = State.UNKNOWN;
    public enum State {
        UNKNOWN,
        SEARCHING_ADVERTISING,
        CONNECTED,
        STOP
    }

    private static final Strategy STRATEGY = Strategy.P2P_STAR ;
    private static final String SERVICE_ID = "BetterThanLecture_VOICECHAT";

    private String localEndpointName;

    Payload bytesPayload; // Payload.fromBytes(new byte[] {0xa, 0xb, 0xc, 0xd});

    public final static String EXTRA_USERNAME = "EXTRA_USERNAME";


    private LinkedList<Endpoint> endpointList;

    private RecyclerView discoverRecyclerView;
    private RecyclerView.Adapter discoverAdapter;
    private ProgressBar loadingBar;
    private ConstraintLayout audioArea;

    Button mic;
    Button audio;

    /* Audio */
    AudioManager audioManager;
    OutputStream outputStream; //Audio player
    InputStream inputStream; //Audio recorder
    Thread recordThread;
    Thread playThread;
    AudioTrack audioTrack;
    AudioRecord record;
    byte[] recordBuffer;
    byte[] playBuffer;
    int bufferSize = 1024;
    int sampleRate = 8000;
    int originalVolume;
    boolean isRecording = false;
    boolean isLoud = true;
    boolean isPlaying = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_chat);

        /* Get Username from intend */
        Bundle extras = getIntent().getExtras();
        if(extras != null){
            String username = extras.getString(EXTRA_USERNAME);
            logd(TAG,"Username: " + username);
            localEndpointName = username;
        }

        /* Init of class variables */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.getStreamVolume(STREAM_MUSIC);

        endpointList = new LinkedList<>();

        discoverAdapter = new DiscovererAdapter(endpointList);

        discoverRecyclerView = findViewById(R.id.discoveredDevices);
        discoverRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        discoverRecyclerView.setAdapter(discoverAdapter);

        loadingBar = findViewById(R.id.loadingBar);
        loadingBar.setVisibility(View.VISIBLE);

        audioArea = findViewById(R.id.audioArea);
        audioArea.setVisibility(View.INVISIBLE);

        /* Listener for DiscoveredDevices OnClick */
        discoverRecyclerView.addOnItemTouchListener(new RecyclerTouchListener(this, discoverRecyclerView, new RecyclerTouchListener.ClickListener() {
            @Override
            public void onClick(View view, int position) {
                logd(TAG, view + " has been clicked.");
                if(!isConnecting){
                    initConnectToEndPoint(endpointList.get(position));
                    loadingConnection();
                }
            }
            @Override
            public void onLongClick(View view, int position) {
                //not in use
            }
        }));

        mic = findViewById(R.id.mic);
        /* Send on SendButton click */
        mic.setOnClickListener(v -> {
            if(!isRecording) {
                startRecording();
                mic.setText(R.string.mute_mic);
                mic.setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_mic_24px, 0, 0, 0);
            } else {
                stopRecording();
                mic.setText(R.string.unmute_mic);
                mic.setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_mic_off_24px, 0, 0, 0);
            }

        });

        audio = findViewById(R.id.audio);
        audio.setOnClickListener(v -> {
            if(!isLoud) {
                isLoud = true;
                audioManager.setStreamVolume(STREAM_MUSIC, ADJUST_UNMUTE, 0);
                audio.setText(R.string.mute_audio);
                audio.setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_volume_up_24px, 0, 0, 0);
            } else {
                isLoud = false;
                audioManager.setStreamVolume(STREAM_MUSIC, ADJUST_MUTE, 0);
                audio.setText(R.string.unmute_audio);
                audio.setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_volume_off_24px, 0, 0, 0);
            };
        });
    }

    @Override
    protected void onStart() {
        logd(TAG,"onStart");

        /* Set media originalVolume to max */
        setVolumeControlStream(STREAM_MUSIC);
        originalVolume = audioManager.getStreamVolume(STREAM_MUSIC);

        audioManager.setStreamVolume(STREAM_MUSIC, ADJUST_UNMUTE, 0);

        setState(State.SEARCHING_ADVERTISING);
        super.onStart();
    }

    @Override
    protected void onStop() {
        logd(TAG, "onStop");
        super.onStop();

        // Restore the original originalVolume.
        audioManager.setStreamVolume(STREAM_MUSIC, originalVolume, 0);
        setVolumeControlStream(USE_DEFAULT_STREAM_TYPE);

        disconnectFromAllEndpoints();
        stopAllEndpoints();
        setState(State.STOP);
    }

    @Override
    protected void onDestroy() {
        logd(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        logd(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected  void onResume() {
        logd(TAG, "onResume");
        super.onResume();
        setState(State.SEARCHING_ADVERTISING);
    }

    /* Called if a remote endpoint is discovered*/
    @Override
    protected void onEndpointDiscovered(final Endpoint endpoint) {
        logd(TAG, "onEndpointDiscovered");
        loadingBar.setVisibility(View.INVISIBLE);
        if(!endpointList.contains(endpoint)){
            endpointList.add(endpoint);
            discoverAdapter.notifyDataSetChanged();
        }
    }

    /* Called if a discovered endpoint is lost */
    @Override
    protected void onDiscoveredEndpointLost(Endpoint endpoint) {
        if(!endpointList.contains(endpoint)) {
            endpointList.remove(endpoint);
            discoverAdapter.notifyDataSetChanged();
        }
    }

    /* Called if a pending connection with a remote endpoint is created*/
    @Override
    protected void onConnectionInitiated(Endpoint endpoint, ConnectionInfo connectionInfo) {
        logd(TAG, "onConnectionInitiated");
        loadingConnection();

        /* Alertdialog for connection request
         * Reference: https://developers.google.com/nearby/connections/android/manage-connectionshttps://developers.google.com/nearby/connections/android/manage-connections
         */
        String endpointId = endpoint.getId();
        new AlertDialog.Builder(this)
                .setTitle("Accept connection to " + endpoint.getName())
                .setMessage("Confirm the code matches on both devices: " + connectionInfo.getAuthenticationToken())
                .setPositiveButton(
                        "Accept",
                        (DialogInterface dialog, int which) ->
                                // The user confirmed, so we can accept the connection.
                                Nearby.getConnectionsClient(this)
                                        .acceptConnection(endpointId, payloadCallback))
                .setNegativeButton(
                        android.R.string.cancel,
                        (DialogInterface dialog, int which) ->
                                // The user canceled, so we should reject the connection.
                                Nearby.getConnectionsClient(this).rejectConnection(endpointId))
                .setIcon(R.drawable.baseline_warning_24px)
                .show();
    }

    /* Called if a called connection failed */
    @Override
    protected void onConnectionFailed(final Endpoint endpoint) {
        logd(TAG, "onConnectionFailed");
        Objects.requireNonNull(getSupportActionBar()).setTitle("FAILED! Please try again.");
        loadingBar.setVisibility(View.INVISIBLE);
    }

    /* Called if someone has connected to us*/
    @Override
    protected void onEndpointConnected(Endpoint endpoint) {
        logd(TAG, "onEndpointConnected");
        if(!establishedConnections.isEmpty()) {
            setState(State.CONNECTED);
        }
    }

    /* Called if someone has disconnected*/
    @Override
    protected void onEndpointDisconnected(final Endpoint endpoint) {
        logd(TAG, "onEndpointDisconnected");
        disconnectFromAllEndpoints();
        if(establishedConnections.isEmpty()) {
            setState(State.SEARCHING_ADVERTISING);
        }

    }

    /* Called if discovery successfully starts*/
    @Override
    protected void onDiscoveryStarted() {
        logd(TAG, "Waiting to find devices...");
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            if(discoveredEndpoints.isEmpty()) {
                setState(State.SEARCHING_ADVERTISING);
            }
        }, 5000);
    }

    /* Called if a payload is received */
    @Override
    protected  void onPayloadReceived(Endpoint endpoint, Payload payload) {
        if (payload.getType() == Payload.Type.STREAM) {
            logd(TAG, "Stream Payload received!");

            inputStream = payload.asStream().asInputStream();
            playAudio();
        }
    }

    private void playAudio() {
        isPlaying = true;

        playThread = new Thread() {
            @Override
            public void run() {
                setThreadPriority(THREAD_PRIORITY_AUDIO);

                audioTrack = new AudioTrack(
                        STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                );

                playBuffer = new byte[bufferSize];
                audioTrack.play();

                int rec;
                try{
                    while(isPlaying && (rec = inputStream.read(playBuffer)) > 0) {
                        audioTrack.write(playBuffer, 0, rec);
                    }
                } catch (IOException e) {
                    logw(TAG, "Exception with playing stream", e);
                } finally {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to close input stream", e);
                    }
                    inputStream  = null;
                    audioTrack.release();
                }
            }
        };
        playThread.start();
    }
    
    public void stopAudio() {
        try {
            playThread.join();
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while joining AudioRecorder thread", e);
            Thread.currentThread().interrupt();
        }
    }

    public static <T> T safeCast(Object object, Class<T> classType) {
        return classType != null && classType.isInstance(object) ? classType.cast(object) : null;
    }

    /* Method to set a new state */
    private void setState(State state) {
        if (this.state == state) {
            logd(TAG, "State set to " + state + " but already in that state");
            Objects.requireNonNull(getSupportActionBar()).setTitle(state.toString());
            return;
        }

        logd(TAG, "State set to " + state);
        this.state = state;

        Objects.requireNonNull(getSupportActionBar()).setTitle(state.toString());

        onStateChanged(this.state);
    }

    /* Called if the state variable is changed */
    private void onStateChanged(State newState) {
        switch (newState) {

            case SEARCHING_ADVERTISING:
                if(!establishedConnections.isEmpty()) {
                    setState(State.CONNECTED);
                    break;
                }
                discoverAdapter.notifyDataSetChanged();
                discoverRecyclerView.setVisibility(View.VISIBLE);
                audioArea.setVisibility(View.INVISIBLE);
                loadingBar.setVisibility(View.VISIBLE);

                if(!isDiscovering){
                    startDiscovering();
                }
                if(!isAdvertising) {
                    startAdvertising();
                }
                break;

            case CONNECTED:
                endpointList.clear();
                discoverRecyclerView.setVisibility(View.INVISIBLE);
                loadingBar.setVisibility(View.INVISIBLE);
                audioArea.setVisibility(View.VISIBLE);

                if(isAdvertising) {
                    stopAdvertising();
                }
                if(isDiscovering) {
                    stopDiscovering();
                }
                startRecording();
                break;

            case STOP:
                if(isAdvertising) {
                    stopAdvertising();
                }
                if(isDiscovering) {
                    stopDiscovering();
                }
                stopRecording();
                break;

            case UNKNOWN:
                break;

            default:
                logd(TAG,"Case default OnStateChanged");
                // no-op
                break;
        }
    }

    private void startRecording() {
        logd(TAG, "startRecording");
        if(isRecording) {
            logd(TAG, "Already recording");
            return;
        }

        isRecording = true;
        try{
            ParcelFileDescriptor[] payloadPipe = ParcelFileDescriptor.createPipe();


            outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(payloadPipe[1]);

            recordThread = new Thread() {
                @Override
                public void run() {
                    setThreadPriority(THREAD_PRIORITY_AUDIO);
                    logd(TAG, "Thread run");
                    record = new AudioRecord(
                            MediaRecorder.AudioSource.DEFAULT,
                            sampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize
                    );

                    if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                        logd(TAG, "Failed to start recording");
                        return;
                    }
                    recordBuffer = new byte[bufferSize];
                    record.startRecording();

                    try {
                        while(isRecording) {
                            int rec = record.read(recordBuffer, 0, bufferSize);
                            if(rec >= 0 && rec <= bufferSize) {
                                outputStream.write(recordBuffer, 0, rec);
                                outputStream.flush();
                            }
                        }
                    } catch (IOException e) {
                        logw(TAG, "ERROR startRecording 1", e);
                    } finally {
                        isRecording = false;
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            logw(TAG, "ERROR startRecording 2", e);
                        }
                        try {
                            record.stop();
                        } catch (IllegalStateException e) {
                            Log.e(TAG, "Failed to stop AudioRecord", e);
                        }
                        record.release();

                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            logw(TAG, "ERROR stopRecording 1", e);
                        }
                    }
                }
            };
            recordThread.start();
            sendStreamPayload(payloadPipe[0]);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        logd(TAG, "stopRecording");
        isRecording = false;

        /*
        try {
            recordThread.join();
        } catch (InterruptedException e) {
            logw(TAG, "ERROR stopRecording 2", e);
            Thread.currentThread().interrupt();
        } */
    }

    @Override
    protected String[] getRequiredPermissions() {
        return join(
                super.getRequiredPermissions(),
                Manifest.permission.RECORD_AUDIO);
    }

    /** Joins 2 arrays together. */
    private static String[] join(String[] a, String... b) {
        String[] join = new String[a.length + b.length];
        System.arraycopy(a, 0, join, 0, a.length);
        System.arraycopy(b, 0, join, a.length, b.length);
        return join;
    }

    /* Init of connect to selected endpoint */
    private void initConnectToEndPoint(Endpoint endpoint) {
        if(!isConnecting) {
            connectToEndpoint(endpoint);
        }
    }

    /* Alter dialog for connection request */
    private void loadingConnection(){
        Objects.requireNonNull(getSupportActionBar()).setTitle("CONNECTING...");
        loadingBar.setVisibility(View.VISIBLE);
    }

    /**** Log methods ****/
    /* Used for printing logs in debug console and on the debug textview */
    @Override
    protected void logd(String TAG, String msg){
        super.logd(TAG,msg);
    }
    @Override
    protected void logw(String TAG, String msg){
        super.logw(TAG,msg);
    }
    @Override
    protected void logw(String TAG, String msg, Throwable e){
        super.logw(TAG,msg,e);
    }

    @Override
    protected String getName() {
        return localEndpointName;
    }

    @Override
    public String getServiceId() {
        return SERVICE_ID;
    }

    @Override
    public Strategy getStrategy() {
        return STRATEGY;
    }
}
