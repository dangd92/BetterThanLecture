package com.example.mp_2019_android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.File;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class SingleChatActivity extends ConnectionsActivity {

    private State state = State.UNKNOWN;

    public enum State {
        UNKNOWN,
        SEARCHING_ADVERTISING,
        CONNECTED,
        STOP
    }

    private static final Strategy STRATEGY = Strategy.P2P_POINT_TO_POINT;
    private static final String SERVICE_ID = "BetterThanLecture_SINGLECHAT";

    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 4321;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT = 1234;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION_RESULT = 2345;

    private String[] appPermissions = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO

    };


    private String localEndpointName;

    private RecyclerView messagesRecyclerView;
    private MessageAdapter messageAdapter;

    Payload bytesPayload; // Payload.fromBytes(new byte[] {0xa, 0xb, 0xc, 0xd});

    public final static String EXTRA_USERNAME = "EXTRA_USERNAME";

    Message sendMessage;
    String reset = "";

    private boolean isRecording;
    private ImageButton videoButton;
    private int lensFacing;
    private TextureView textureView;
    private TextureView.SurfaceTextureListener textureViewListener;

    private View bgView;

    private CameraDevice cameraDevice;
    private CameraDevice.StateCallback cameraDeviceStateCallback;
    private String cameraId;
    private Size previewSize;
    private Size videoSize;
    private MediaRecorder mediaRecorder;

    private int totalRotation;

    private CaptureRequest.Builder captureRequestBuilder;
    private File videoFolder;
    private String videoFileName;
    private String myVideoFileName;

    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private EditText messageEditText;

    private LinkedList<Endpoint> endpointList;
    private LinkedList<Message> messages;

    private RecyclerView discoverRecyclerView;
    private RecyclerView.Adapter discoverAdapter;
    private ConstraintLayout sendArea;
    private ProgressBar loadingBar;

    /* Used to keep track of incoming and fully received FILE Payloads */
    private final SimpleArrayMap<Long, Payload> incomingFilePayloads = new SimpleArrayMap<>();
    private final SimpleArrayMap<Long, Payload> completedFilePayloads = new SimpleArrayMap<>();
    private final SimpleArrayMap<Long, FileData> separateDataOfFilePayloads = new SimpleArrayMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_chat);

        /* Get Username from intend */
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String username = extras.getString(EXTRA_USERNAME);
            logd(TAG, "Username: " + username);
            localEndpointName = username;
        }

        /* Init of class variables */
        ImageButton sendButton = findViewById(R.id.button_sendMessage);
        videoButton = findViewById(R.id.button_startVideo);

        endpointList = new LinkedList<>();

        messages = new LinkedList<>();

        messageEditText = findViewById(R.id.editText_message);

        messageAdapter = new MessageAdapter(messages, localEndpointName, this);

        messagesRecyclerView = findViewById(R.id.conversation);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        messagesRecyclerView.setAdapter(messageAdapter);
        messagesRecyclerView.setVisibility(View.INVISIBLE);

        sendArea = findViewById(R.id.sendArea);
        sendArea.setVisibility(View.VISIBLE);

        discoverAdapter = new DiscovererAdapter(endpointList);

        discoverRecyclerView = findViewById(R.id.discoveredDevices);
        discoverRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        discoverRecyclerView.setAdapter(discoverAdapter);

        textureView = findViewById(R.id.cameraPreview);
        textureView.setOpaque(false);
        bgView = findViewById(R.id.bgview);
        lensFacing = 1; //FrontCamera  (0 = BackCamera)

        createVideoFolder();
        mediaRecorder = new MediaRecorder();

        loadingBar = findViewById(R.id.loadingBar);
        loadingBar.setVisibility(View.INVISIBLE);


        /* Listener for DiscoveredDevices OnClick */
        discoverRecyclerView.addOnItemTouchListener(new RecyclerTouchListener(this, discoverRecyclerView, new RecyclerTouchListener.ClickListener() {
            @Override
            public void onClick(View view, int position) {
                logd(TAG, view + " has been clicked.");
                if (!isConnecting) {
                    initConnectToEndPoint(endpointList.get(position));
                    loadingConnection();
                }
            }

            @Override
            public void onLongClick(View view, int position) {
                //not in use
            }
        }));

        /* Send on SendButton click */
        sendButton.setOnClickListener(v -> sendMessage());

        /* StartStop VideoButton click */
        videoButton.setOnClickListener(v -> {
            if (isRecording) {
                videoButton.setImageResource(R.drawable.ic_baseline_videocam_24px);
                isRecording = false;
                logd(TAG, "onClickRecord: Stop");

                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
                bgView.setVisibility(View.VISIBLE);
                messagesRecyclerView.setVisibility(View.VISIBLE);

                sendFilePayload();
            } else {
                logd(TAG, "onClickRecord: Start");
                hideKeyboard();
                checkToStartRecord();
            }
        });

        /* ActionListener for KeyboardSendButton */
        messageEditText.setOnEditorActionListener((v, actionId, event) -> {
            boolean handled = false;
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                handled = true;
            }
            return handled;
        });

        /* Camera Preview TextureListener */
        textureViewListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                logd(TAG, "OnSurfaceTextureAvailable");
                setupCamera(i, i1, lensFacing);
                connectCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
                logd(TAG, "OnSurfaceTextureSizeChanged");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                logd(TAG, "OnSurfaceTextureDestroyed");
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                //logd(TAG,"OnSurfaceTextureUpdated"); <- Disabled because gets called every frame

            }
        };

        /* Camera Device StateCallback*/
        cameraDeviceStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                logd(TAG, "CameraStateCallback onOpened");
                cameraDevice = camera;
                if (isRecording) {
                    try {
                        createVideoFileName();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    startRecord();
                    mediaRecorder.start();
                }
                Toast.makeText(getApplicationContext(), "CameraCon made!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                logd(TAG, "CameraStateCallback onDisconnected");
                camera.close();
                cameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int i) {
                logd(TAG, "CameraStateCallback onError");
                camera.close();
                cameraDevice = null;
            }
        };

        startBackgroundThread();
        prepareRecord();
    }

    @Override
    protected void onStart() {
        logd(TAG, "onStart");
        setState(State.SEARCHING_ADVERTISING);
        super.onStart();
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
        closeCamera();
        stopBackgroundThread();
    }

    @Override
    protected void onPause() {
        logd(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onResume() {
        logd(TAG, "onResume");
        super.onResume();
        setState(State.SEARCHING_ADVERTISING);
    }

    /*Checks if User gave Permission to use Camera, Storage etc.*/
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION_RESULT:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, "You cant use CameraChat now!", Toast.LENGTH_SHORT).show();
                }
                break;

            case REQUEST_RECORD_AUDIO_PERMISSION_RESULT:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, "You cant use CameraChat now!", Toast.LENGTH_SHORT).show();
                }
                break;

            case REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, "Sorry you cant use this Chat without that!", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;

            default:
                break;
        }
    }

    /* ask for all req. permissions */
    @Override
    protected String[] getRequiredPermissions() {
        return join(
                super.getRequiredPermissions(),
                appPermissions);
    }

    /** Joins an array with another array or string together. */
    private static String[] join(String[] a, String... b) {
        String[] join = new String[a.length + b.length];
        System.arraycopy(a, 0, join, 0, a.length);
        System.arraycopy(b, 0, join, a.length, b.length);
        return join;
    }

    /* Called if a remote endpoint is discovered*/
    @Override
    protected void onEndpointDiscovered(final Endpoint endpoint) {
        logd(TAG, "onEndpointDiscovered");
        loadingBar.setVisibility(View.INVISIBLE);
        if (!endpointList.contains(endpoint)) {
            endpointList.add(endpoint);
            discoverAdapter.notifyDataSetChanged();
        }
    }

    /* Called if a discovered endpoint is lost */
    @Override
    protected void onDiscoveredEndpointLost(Endpoint endpoint) {
        if (!endpointList.contains(endpoint)) {
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
        if (!establishedConnections.isEmpty()) {
            setState(State.CONNECTED);
        }
    }

    /* Called if someone has disconnected*/
    @Override
    protected void onEndpointDisconnected(final Endpoint endpoint) {
        logd(TAG, "onEndpointDisconnected");
        disconnectFromAllEndpoints();
        if (establishedConnections.isEmpty()) {
            setState(State.SEARCHING_ADVERTISING);
        }

    }

    /* Called if discovery successfully starts*/
    @Override
    protected void onPayloadReceived(Endpoint endpoint, Payload payload) {
        switch (payload.getType()) {
            case Payload.Type.BYTES:
                processBytePayload(payload);
                break;
            case Payload.Type.FILE:
                incomingFilePayload(payload);
                break;
        }
    }

    /* Called when the transfer status of a payload updates */
    protected void onPayloadTransferUpdate(PayloadTransferUpdate payloadTransferUpdate) {
        if (payloadTransferUpdate.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
            // a file payload was received successful -> remove
            // it from incomingFilePayloads and put it into completedFilePayloads
            // by using its payloadId
            long payloadId = payloadTransferUpdate.getPayloadId();
            Payload payload = incomingFilePayloads.remove(payloadId);
            completedFilePayloads.put(payloadId, payload);

            if (payload != null) {
                if (payload.getType() == Payload.Type.FILE) {
                    logd(TAG, "onTransferUpdate: processFilePayload");
                    // try to process the file payload
                    processFilePayload(payloadId);
                }
            }
        }
    }

    /* Called if a payload is received */
    @Override
    protected void onDiscoveryStarted() {
        logd(TAG, "Waiting to find devices...");
    }

    /* handles received bytes (e.g.: a text message object or string data object) */
    private void processBytePayload(Payload payload){
        Object obj = getSerializedObjectOnReceive(payload);

        if (obj instanceof Message) {
            Message receivedMsg = safeCast(obj, Message.class);
            addMessageToView(receivedMsg);
        } else if (obj instanceof FileData) {
            // Receive bytes for Type FILE:
            FileData separateData = safeCast(obj, FileData.class);
            cacheSeparateData(separateData);
            processFilePayload(separateData.getPayloadId());
        }
    }

    /* cache an incoming (incomplete) file payload for
    *  later use (after file payload was transmitted successful)
    */
    private void incomingFilePayload(Payload payload){
        incomingFilePayloads.put(payload.getId(), payload);
    }


    /* BYTES (separateDataOfFilePayloads) and FILE (completedFilePayloads) can be received in any order.
       Therefore we try to process a file payload when its BYTES or its FILE are/is received, but
       we can only start processing them if BOTH are received. */
    private void processFilePayload(long payloadId) {
        Payload filePayload = completedFilePayloads.get(payloadId);
        FileData separateData = separateDataOfFilePayloads.get(payloadId);

        if (filePayload != null && separateData != null) {
            completedFilePayloads.remove(payloadId);
            separateDataOfFilePayloads.remove(payloadId);

            // Get the received file (which will be in the Downloads folder)
            File payloadFile = filePayload.asFile().asJavaFile();

            // Rename the file.
            File newName = new File(payloadFile.getParentFile(), separateData.getFileName());
            payloadFile.renameTo(newName);
            String videoFilePath = newName.getAbsolutePath();
            // Add received Video-Message to View

            Message fileMsg = new Message("Video-Message",separateData.getSourceEndpoint(),
                    separateData.getCurrentTime(),
                    MessageType.VideoFile,
                    videoFilePath
            );
            addMessageToView(fileMsg);
            logd(TAG, "processFilePayload: a (video) file was received!");
        }
    }

    /* caches extra data for a file payload for later use (after file payload was transmitted successful) */
    private void cacheSeparateData(FileData dataExtras) {
        long payloadId = dataExtras.getPayloadId();
        separateDataOfFilePayloads.put(payloadId, dataExtras);
    }

    /* Method to deserialize the Payload */
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
    private void addMessageToView(Message receivedMsg) {
        try {
            if (receivedMsg != null) {

                if (receivedMsg.getMessageType() == MessageType.Text) {
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
                        logd(TAG, "Text Message already received!");
                    }
                } else {
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
                        logd(TAG, "Video Message already received!");
                    }
                }
            } else {
                logd(TAG, "receivedMesssage = null");
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    /* used for safe type casting */
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
                if (!establishedConnections.isEmpty()) {
                    setState(State.CONNECTED);
                    break;
                }

                discoverAdapter.notifyDataSetChanged();
                discoverRecyclerView.setVisibility(View.VISIBLE);
                messagesRecyclerView.setVisibility(View.INVISIBLE);
                messages.clear();
                messageAdapter.notifyDataSetChanged();
                sendArea.setVisibility(View.INVISIBLE);
                loadingBar.setVisibility(View.VISIBLE);

                if (!isDiscovering) {
                    startDiscovering();
                }
                if (!isAdvertising) {
                    startAdvertising();
                }
                break;

            case CONNECTED:
                endpointList.clear();
                discoverRecyclerView.setVisibility(View.INVISIBLE);
                loadingBar.setVisibility(View.INVISIBLE);
                messagesRecyclerView.setVisibility(View.VISIBLE);
                sendArea.setVisibility(View.VISIBLE);

                if (isAdvertising) {
                    stopAdvertising();
                }
                if (isDiscovering) {
                    stopDiscovering();
                }
                break;

            case STOP:
                if (isAdvertising) {
                    stopAdvertising();
                }
                if (isDiscovering) {
                    stopDiscovering();
                }
                break;

            case UNKNOWN:
                break;

            default:
                logd(TAG, "Case default OnStateChanged");
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

    /* Send File payload */
    private void sendFilePayload(){
        if (!establishedConnections.isEmpty()) {
            //create a video-message
            sendMessage = new Message("Video-Message", localEndpointName, MessageType.VideoFile, videoFileName);
            //add the new message to the LinkedList
            messages.add(sendMessage);
            //update and scroll the MessageAdapter
            messageAdapter.notifyDataSetChanged();
            scrollRecyclerViewToEnd();

            File fileToSend = new File(videoFolder, myVideoFileName);
            try {
                // the file that should be transferred is stored in the file payload
                Payload filePayload = Payload.fromFile(fileToSend);

                // collect required data for transfer
                Uri uri = Uri.fromFile(fileToSend);
                long payloadId = filePayload.getId();
                String fileName = uri.getLastPathSegment();
                String sourceEndpoint = localEndpointName;

                // create a FileData-Object including the ID of the file payload,
                // the filename and sourceEndpoint
                FileData bytePayload = new FileData(payloadId, fileName, sourceEndpoint);

                if (bytePayload != null) {
                    // 1. Send the FileData-Object as a byte payload.
                    Payload filenameBytesPayload = Payload.fromBytes(SerializationHelper.serialize(bytePayload));
                    sendPayload(filenameBytesPayload, establishedConnections.keySet());

                    // 2. send the file (e.g.: video file) as file payload.
                    sendPayload(filePayload, establishedConnections.keySet());
                    logd(TAG, "sendFilePayload: Done");
                }

            } catch (IOException e) {
                logw(TAG, "SingleChatActivity: Failed to serialize byte payload/File not found (missing/wrong path)!", e);
            }
        }
    }

    /* Scroll RecyclerView to the end */
    private void scrollRecyclerViewToEnd() {
        if (messageAdapter.getItemCount() - 1 > 0) {
            messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }

    /* Init of connect to selected endpoint */
    private void initConnectToEndPoint(Endpoint endpoint) {
        if (!isConnecting) {
            connectToEndpoint(endpoint);
        }
    }

    /* Alter dialog for connection request */
    private void loadingConnection() {
        Objects.requireNonNull(getSupportActionBar()).setTitle("CONNECTING...");
        loadingBar.setVisibility(View.VISIBLE);
    }

    /* sets up the camera manager with the according characteristics */
    private void setupCamera(int width, int height, int lensFacing) { //Pass lensFacing 1 for front and 0 for back
        logd(TAG, "setupCamera");
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String camId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(camId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == lensFacing)
                    continue;

                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                totalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);

                boolean swapRotation = totalRotation == 90 || totalRotation == 270;
                int rotatedW;
                int rotatedH;
                if (swapRotation) {
                    rotatedW = height;
                    rotatedH = width;
                } else {
                    rotatedW = width;
                    rotatedH = height;
                }
                if (map != null) {
                    previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedW, rotatedH);
                    videoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), rotatedW, rotatedH);
                } else {
                    logd(TAG, "Camera Characteristics in SetupCamera = NULL!");
                }
                this.cameraId = camId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /* connects the manager with the camera device */
    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, cameraDeviceStateCallback, backgroundHandler);
            } else {
                //checkCameraPermissions();
                Toast.makeText(this, "VideoChat requires access to the camera!",Toast.LENGTH_SHORT).show();
            }
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    /* preparing to record the image from the camera texture  */
    private void startRecord() {
        try {
            setupMediaRecorder();
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);

            Surface recordSurface = mediaRecorder.getSurface();
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.addTarget(recordSurface);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull  CameraCaptureSession cSession) {
                            try {
                                cSession.setRepeatingRequest(
                                        captureRequestBuilder.build(), null, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cSession) {

                        }
                    }, null);

        } catch (IOException e) {
            e.printStackTrace();
        } catch ( CameraAccessException f){
            f.printStackTrace();
        }
    }

    /* closing the camera device */
    private void closeCamera(){
        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    /* creating a background thread */
    private void startBackgroundThread(){
        backgroundHandlerThread = new HandlerThread("VideoChatActivity");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }

    /* stop the background thread */
    private void stopBackgroundThread(){
        backgroundHandlerThread.quitSafely();
        try {
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /* returns the rotation the picture should have*/
    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation){
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int i = ORIENTATIONS.get(deviceOrientation);
        return(sensorOrientation + i + 360 ) % 360;
    }

    /* chooses the best fitting Size the camera device can handle accordingly to the texture view*/
    private static Size chooseOptimalSize(Size[] choices, int width, int height){
        List<Size> bigEnough = new ArrayList<>();
        for( Size option : choices) {
            if(option.getHeight() == option.getWidth() + height / width &&
                    option.getWidth() >= width && option.getHeight() >= height){
                bigEnough.add(option);
            }
        }
        if(bigEnough.size()>0){
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }
    }

    /* creates the folder where the video will be saved (if it hasn't been created yet) */
    private void createVideoFolder(){
        File videoChatMovieFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        videoFolder = new File(videoChatMovieFile, "camera2videoChat");
        if(!videoFolder.exists()){
            logd(TAG,"created Folder(camera2videoChat)");
            videoFolder.mkdirs();
        } else {
            logd(TAG, "video Folder allReady existed");
        }
    }

    /* generates the name of the video file based on time */
    private void createVideoFileName() throws IOException{
        @SuppressLint("SimpleDateFormat") String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend = "VIDEO_" + timestamp + "_";
        File videoFile = File.createTempFile(prepend, ".mp4", videoFolder);
        videoFileName = videoFile.getAbsolutePath();
        myVideoFileName = videoFile.getName();
    }

    /* check if the texture view is ready and if so start Recording*/
    private void checkToStartRecord(){

        if(textureView.isAvailable()){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                isRecording = true;
                videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24px);
                try{
                    createVideoFileName();
                } catch (IOException e){
                    e.printStackTrace();
                }
                startRecord();
                mediaRecorder.start();
            }  else {
                //checkStoragePermissions();
                Toast.makeText(this, "This chat requires access to the storage!",Toast.LENGTH_SHORT).show();
            }

            messagesRecyclerView.setVisibility(View.INVISIBLE);
            bgView.setVisibility(View.INVISIBLE);
        }
        else {
            Toast.makeText(this, "Try in one second again!", Toast.LENGTH_SHORT).show();
        }
    }

    /* connects camera to the texture view if it is available */
    private void prepareRecord(){
        logd(TAG, "preparing Record");

        if(textureView.isAvailable()){
            setupCamera(textureView.getWidth(), textureView.getHeight(), lensFacing);
            connectCamera();
        }else{
            textureView.setSurfaceTextureListener(textureViewListener);
        }
    }

    /* give all the necessary parameters to the media recorder and prepare to record */
    private void setupMediaRecorder() throws  IOException{
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED){
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(videoFileName);
            mediaRecorder.setVideoEncodingBitRate(1000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoSize(videoSize.getWidth(),videoSize.getHeight());
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOrientationHint(totalRotation);
            mediaRecorder.prepare();
        }
    }

    /* closes the android keyboard */
    private void hideKeyboard(){
        InputMethodManager imm =  (InputMethodManager) this.getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = this.getCurrentFocus();
        assert view != null;
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
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

    /* nested class that let us compare two Sizes by there height and width */
    protected static class CompareSizeByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs){
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() / (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
