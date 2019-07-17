package com.example.mp_2019_android;

import android.annotation.SuppressLint;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

import static com.example.mp_2019_android.ConnectionsActivity.TAG;

public class Message implements Parcelable, Serializable{
    /**** Class variables ****/
    private String message;
    private String sourceEndpointName;
    private String currentTime;
    private MessageType messageType;
    private String videoFilePath = null;
    LinkedList<String> receivedEndpoints = new LinkedList<>();
    
    @SuppressLint("SimpleDateFormat")
    Message(String message, String sourceEndpointName, MessageType messageType){
        this.message = message;
        this.sourceEndpointName = sourceEndpointName;
        this.currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
        this.messageType = messageType;
    }

    Message(String message, String sourceEndpointName, String oldTime, MessageType messageType){
        this.messageType = messageType;
        this.message = message;
        this.sourceEndpointName = sourceEndpointName;
        this.currentTime = oldTime;
        this.messageType = messageType;
    }

    Message(String message, String sourceEndpointName, MessageType messageType, String videoFilePath){
        this.messageType = messageType;
        this.message = message;
        this.sourceEndpointName = sourceEndpointName;
        this.currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());;
        this.messageType = messageType;
        this.videoFilePath = videoFilePath;
    }

    Message(String message, String sourceEndpointName, String oldTime, MessageType messageType, String videoFilePath){
        this.messageType = messageType;
        this.message = message;
        this.sourceEndpointName = sourceEndpointName;
        this.currentTime = oldTime;
        this.messageType = messageType;
        this.videoFilePath = videoFilePath;
    }

    private Message(Parcel in) {
        message = in.readString();
        sourceEndpointName = in.readString();
        currentTime = in.readString();
    }

    public static final Creator<Message> CREATOR = new Creator<Message>() {
        @Override
        public Message createFromParcel(Parcel in) {
            Log.d(TAG, "createFromParcel in class " + Message.class.getName());
            return new Message(in);
        }

        @Override
        public Message[] newArray(int size) {
            return new Message[size];
        }
    };

    @Override
    public int describeContents() {
        Log.d(TAG, "describeContents in class '" + Message.class.getName() + "'");
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Log.d(TAG, "writeToParcel '" + Message.class.getName() + "'");
        dest.writeString(message);
        dest.writeString(sourceEndpointName);
        dest.writeString(currentTime);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Message) {
            Message other = (Message) obj;
            return this.toString().equals(other.toString());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @NonNull
    public String toString() {
        return String.format("Message{message = %s, currentTime = %s, sourceEndpointName = %s}", message, currentTime, sourceEndpointName);
    }

    String getMessage() {
        return message;
    }

    String getSourceEndpointName() {
        return sourceEndpointName;
    }

    String getCurrentTime() {
        return currentTime;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public String getVideoFilePath() {
        return videoFilePath;
    }

    public void setVideoFilePath(String videoFilePath) {
        this.videoFilePath = videoFilePath;
    }
}
