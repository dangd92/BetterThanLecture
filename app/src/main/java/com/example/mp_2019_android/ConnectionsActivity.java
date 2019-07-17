package com.example.mp_2019_android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import android.os.Bundle;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/* Inspired by API Example WalkieTalkie from GoogleSamples on GitHub
 * Reference: https://github.com/googlesamples/android-nearby/tree/master/connections/walkietalkie
 */
public abstract class ConnectionsActivity extends AppCompatActivity {
    /**** Class variables ****/
    /* Permissions variable */
    private static final String[] REQUIRED_PERMISSIONS =
            new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            };
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

    protected String[] getRequiredPermissions() {
        return REQUIRED_PERMISSIONS;
    }

    /* TAG for android.os.util.Log API */
    public static final String TAG = "ConnectionsActivity";

    /* Nearby Connections Handler */
    private ConnectionsClient connectionsClient;

    /* Endpoints we have discovered */
    protected Map<String, Endpoint> discoveredEndpoints = new HashMap<>();

    /* Endpoints we have pending connections to */
    protected final Map<String, Endpoint> pendingConnections = new HashMap<>();

    /* Endpoints we are currently connected to */
    protected final Map<String, Endpoint> establishedConnections = new HashMap<>();

    /* True if we are asking a discovered device to connect to us */
    protected boolean isConnecting = false;

    /* True if we are discovering */
    protected boolean isDiscovering = false;

    /* True if we are advertising */
    protected boolean isAdvertising = false;

    /* ConnectionsCallbacks for connections to other devices. */
    private ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        /* Called if a connection is initiated */
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, ConnectionInfo connectionInfo) {
            logd(TAG, "Connection request from: " + endpointId + " " + connectionInfo.getEndpointName());
            isConnecting = true;
            Endpoint endpoint = new Endpoint(endpointId, connectionInfo.getEndpointName());
            pendingConnections.put(endpointId, endpoint);
            ConnectionsActivity.this.onConnectionInitiated(endpoint, connectionInfo);
        }
        /* Called if is connection is successful initiated or failed */
        @Override
        public void onConnectionResult(@NonNull String endpointId, ConnectionResolution connectionResolution) {
            isConnecting = false;
            if (!connectionResolution.getStatus().isSuccess()) {
                logw(TAG, "Connection failed. Received status: " + connectionResolution.getStatus());
                onConnectionFailed(pendingConnections.remove(endpointId));
                return;
            }
            connectedToEndpoint(pendingConnections.remove(endpointId));
        }
        /* Called if a connection gets disconnected */
        @Override
        public void onDisconnected(@NonNull String endpointId) {
            if (!establishedConnections.containsKey(endpointId)) {
                logw(TAG, "Unexpected disconnection from endpoint " + endpointId);
                return;
            }
            disconnectedFromEndpoint(Objects.requireNonNull(establishedConnections.get(endpointId)));
        }
    };

    /* DiscoveryCallbacks for connections to other devices. */
    private EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        /* Called if a nearby device with the same ServiceID is found */
        @Override
        public void onEndpointFound(@NonNull String endpointId, DiscoveredEndpointInfo discoveredEndpointInfo) {
            logd(TAG, "onEndpointFound(endpointId=" + endpointId + ", serviceId=" + discoveredEndpointInfo.getServiceId() + ", endpointName=" + discoveredEndpointInfo.getEndpointName() + ")");
            if (getServiceId().equals(discoveredEndpointInfo.getServiceId())) {
                Endpoint endpoint = new Endpoint(endpointId, discoveredEndpointInfo.getEndpointName());
                discoveredEndpoints.put(endpointId, endpoint);
                onEndpointDiscovered(endpoint);
            }
        }
        /* Called if a nearby device is not advertising anymore */
        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            logd(TAG, "onEndpointLost " + endpointId);
            Endpoint endpoint = discoveredEndpoints.get(endpointId);
            discoveredEndpoints.remove(endpointId);
            onDiscoveredEndpointLost(endpoint);
        }
    };

    /* PayloadCallbacks for receiving payloads */
    protected PayloadCallback payloadCallback = new PayloadCallback() {
        /* Called if a payload is received */
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
           ConnectionsActivity.this.onPayloadReceived(establishedConnections.get(endpointId), payload);
           logd(TAG, "Payload reveived!");
        }
        /* Used to see the transfer status of a payload */
        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate payloadTransferUpdate) {
            ConnectionsActivity.this.onPayloadTransferUpdate(payloadTransferUpdate);
            logd(TAG, String.format("onPayloadTransferUpdate(endpointId=%s, update=%s)", endpointId, payloadTransferUpdate));
        }
    };

    /**** Class methods ****/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectionsClient = Nearby.getConnectionsClient(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (checkPermissions(this, getRequiredPermissions())) {
            if (checkPermissions(this, getRequiredPermissions())) {
                if (Build.VERSION.SDK_INT < 23) {
                    ActivityCompat.requestPermissions(
                            this, getRequiredPermissions(), REQUEST_CODE_REQUIRED_PERMISSIONS);
                } else {
                    requestPermissions(getRequiredPermissions(), REQUEST_CODE_REQUIRED_PERMISSIONS);
                }
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume(){
        super.onResume();
    }

    /* Called if the user has accepted our permission request */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_REQUIRED_PERMISSIONS) {
            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    finish();
                    return;
                }
            }
            recreate();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /* Check if the needed permission got accepted by the user */
    public static boolean checkPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    /* Start advertising on the device with the individual ServiceID in each activity */
    protected void startAdvertising() {
        isAdvertising = true;
        final String localEndpointName = getName();
        AdvertisingOptions.Builder advertisingOptions = new AdvertisingOptions.Builder();
        advertisingOptions.setStrategy(getStrategy());

        connectionsClient.startAdvertising(
                        localEndpointName,
                        getServiceId(),
                        connectionLifecycleCallback,
                        advertisingOptions.build())
                .addOnSuccessListener(
                        unusedResult -> {
                            logd(TAG, "Now advertising endpoint " + localEndpointName);
                            onAdvertisingStarted();
                        })
                .addOnFailureListener(
                        e -> {
                            isAdvertising = false;
                            logw(TAG,"startAdvertising() failed.", e);
                            onAdvertisingFailed();
                        });
        logd(TAG, "startAdvertising");
    }

    /* Stop advertising */
    protected void stopAdvertising() {
        isAdvertising = false;
        connectionsClient.stopAdvertising();
        logd(TAG, "stopAdvertising");
    }

    /* Start discovering on the device with the individual ServiceID in each activity */
    protected  void startDiscovering() {
        isDiscovering = true;

        discoveredEndpoints.clear();

        DiscoveryOptions.Builder discoveryOptions = new DiscoveryOptions.Builder();
        discoveryOptions.setStrategy(getStrategy());

        connectionsClient.startDiscovery(
                        getServiceId(),
                        endpointDiscoveryCallback,
                        discoveryOptions.build())
                .addOnSuccessListener(
                        unusedResult -> {
                            logd(TAG, "startDiscovering() success.");
                            onDiscoveryStarted();
                        })
                .addOnFailureListener(
                        e -> {
                            isDiscovering = false;
                            logw(TAG,"startDiscovering() failed.", e);
                            onDiscoveryFailed();
                        });
        logd(TAG, "startDiscovering");
    }

    /* Stop discovering */
    protected void stopDiscovering() {
        isDiscovering = false;
        connectionsClient.stopDiscovery();
        logd(TAG, "stopDiscovering");
    }

    /* Accepts a connection request */
    protected void acceptConnection(final Endpoint endpoint) {
        connectionsClient.acceptConnection(endpoint.getId(), payloadCallback);
    }

    /* Sends a connection request to the endpoint */
    protected void connectToEndpoint(final Endpoint endpoint) {
        logd(TAG, "Sending a connection request to endpoint " + endpoint);
        isConnecting = true;

        /* Check if we are already connected to the endpoint */
        boolean shouldConnect = !hasEndpoint(endpoint);
        logd(TAG,"shouldConnect: " + shouldConnect);

        if(shouldConnect) {
            logd(TAG, "Connect to endpoint");
            /* Ask to connect */
            connectionsClient
                    .requestConnection(getName(), endpoint.getId(), connectionLifecycleCallback)
                    .addOnFailureListener(
                            e -> {
                                logw(TAG, "requestConnection() failed.", e);
                                isConnecting = false;
                                onConnectionFailed(endpoint);
                            });
        } else {
            logd(TAG, "Already connected with endpoint");
        }
    }

    /* Check if we have already a connection to the endpoint */
    private boolean hasEndpoint(Endpoint endpoint) {
        for(Map.Entry<String, Endpoint> endp : establishedConnections.entrySet()) {
            if ((endp.getKey()).equals(endpoint.getId())) {
                return true;
            }
        }
        return false;
    }

    /* Add the new connected endpoint to establishedConnections Map */
    private void connectedToEndpoint(Endpoint endpoint) {
        logd(TAG, "connectedToEndpoint " + endpoint);
        establishedConnections.put(endpoint.getId(), endpoint);
        onEndpointConnected(endpoint);
    }

    /* Remove the disconnected endpoint from establishedConnections Map */
    private void disconnectedFromEndpoint(Endpoint endpoint) {
        logd(TAG, "disconnectedFromEndpoint" + endpoint);
        establishedConnections.remove(endpoint.getId());
        onEndpointDisconnected(endpoint);
    }

    /* Disconnects from all currently connected endpoints */
    protected void disconnectFromAllEndpoints() {
        logd(TAG, "disconnectedFromAllEndpoints");
        for (Endpoint endpoint : establishedConnections.values()) {
            connectionsClient.disconnectFromEndpoint(endpoint.getId());
        }
        establishedConnections.clear();
    }

    /* Reset everything */
    protected void stopAllEndpoints() {
        connectionsClient.stopAllEndpoints();
        isAdvertising = false;
        isDiscovering = false;
        isConnecting = false;
        discoveredEndpoints.clear();
        pendingConnections.clear();
        establishedConnections.clear();
    }

    /* Restart advertising */
    protected void restartAdvertising() {
        logd(TAG, "restartAdvertising");
        stopAdvertising();
        startAdvertising();
    }

    /* Restart discovering */
    protected  void restartDiscovering() {
        logd(TAG, "restartDiscovering");
        stopDiscovering();
        startDiscovering();
    }

    /* Called to send a payload to a single endpoint or multiple endpoints */
    protected void sendPayload(Payload payload, Set<String> endpoints){
        connectionsClient.sendPayload(new ArrayList<>(endpoints), payload);
        logd(TAG,"send Payload of type " + payload.getType()+ "!");
    }

    /* Called to send a payload to a single endpoint */
    protected void sendPayload(Payload payload, String endpoint) {
        connectionsClient.sendPayload(endpoint, payload);
        logd(TAG, "send Payload of type " + payload.getType() + "!");
    }

    protected void sendFilePayload(File file){
        try {
            Payload filePayload = Payload.fromFile(file);
            connectionsClient.sendPayload(new ArrayList<>(establishedConnections.keySet()), filePayload);
            logd(TAG,"send Payload of type " + filePayload.getType()+ "!");
        } catch (FileNotFoundException e){
            logw(TAG, "File not found", e);
        }
    }

    protected void sendStreamPayload(ParcelFileDescriptor pfd) {
        Payload streamPayload = Payload.fromStream(pfd);
        connectionsClient.sendPayload(new ArrayList<>(establishedConnections.keySet()), streamPayload)
        .addOnFailureListener(
                e -> logw(TAG, "sendPayload() failed.", e));
        logd(TAG,"send Payload of type " + streamPayload.getType()+ "!");
    }

    /**** Event methods ****/
    /* Called if a pending connection with a remote endpoint is created. Override this method to act on the event */
    protected void onConnectionInitiated(Endpoint endpoint, ConnectionInfo connectionInfo) {}
    /* Called if discovery successfully starts. Override this method to act on the event */
    protected void onDiscoveryStarted() {}
    /* Called if discovery fails to start. Override this method to act on the event */
    protected void onDiscoveryFailed() {}
    /* Called if advertising successfully starts. Override this method to act on the event */
    protected void onAdvertisingStarted() {}
    /* Called if advertising fails to start. Override this method to act on the event */
    protected void onAdvertisingFailed() {}
    /* Called if a connection with this endpoint has failed. Override this method to act on the event */
    protected void onConnectionFailed(Endpoint endpoint) {}
    /* Called if someone has connected to us. Override this method to act on the event */
    protected void onEndpointConnected(Endpoint endpoint) {}
    /* Called if someone has disconnected. Override this method to act on the event */
    protected void onEndpointDisconnected(Endpoint endpoint) {}
    /* Called if a payload is received. Override this method to act on the event */
    protected  void onPayloadReceived(Endpoint endpoint, Payload payload) {}
    /* Called if the transfer status of a payload updates. Override this method to act on the event */
    protected void onPayloadTransferUpdate(PayloadTransferUpdate payloadTransferUpdate){}
    /* Called if a remote endpoint is discovered. Override this method to act on the event */
    protected void onEndpointDiscovered(Endpoint endpoint) {}
    /* Called if a discovered endpoint is lost */
    protected  void onDiscoveredEndpointLost(Endpoint endpoint) {}

    /**** Abstract methods ****/
    /* Returns the client's name. Visible to others when connecting */
    protected abstract String getName();
    /* Returns the service id of the individual activity*/
    protected abstract String getServiceId();
    /* Returns the strategy we use to connect to other devices */
    protected abstract Strategy getStrategy();

    /**** Log methods ****/
    /* Used for printing logs in debug console and on the debug textview */
    @CallSuper
    protected void logd(String TAG, String msg){
        Log.d(TAG,msg);
    }
    @CallSuper
    protected void logw(String TAG, String msg){
        Log.w(TAG,msg);
    }
    @CallSuper
    protected void logw(String TAG, String msg, Throwable e){ Log.w(TAG,msg,e); }
}


