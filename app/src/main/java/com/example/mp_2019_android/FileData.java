package com.example.mp_2019_android;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileData implements Serializable {

    /* Class variable */
    private long payloadId;
    private String fileName = null;
    private String sourceEndpoint = null;
    private String currentTime = null;

    /* Constructor */
    public FileData(long payloadId, String fileName, String sourceEndpoint){
        this.payloadId = payloadId;
        this.fileName = fileName;
        this.sourceEndpoint = sourceEndpoint;
        this.currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    /* Getters & Setters */
    public long getPayloadId() {
        return payloadId;
    }

    public void setPayloadId(long payloadId) {
        this.payloadId = payloadId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSourceEndpoint() {
        return sourceEndpoint;
    }

    public void setSourceEndpoint(String sourceEndpoint) {
        this.sourceEndpoint = sourceEndpoint;
    }

    public String getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(String currentTime) {
        this.currentTime = currentTime;
    }
}
