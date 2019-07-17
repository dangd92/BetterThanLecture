package com.example.mp_2019_android;

public enum MessageType {
    Text(0),
    VideoFile(1);

    private int id; // Could be other data type besides int
    MessageType(int id) {
        this.id = id;
    }

    // used to re-init an integer from a data base to previous enum
    public static com.example.mp_2019_android.MessageType fromId(int id) {
        for (com.example.mp_2019_android.MessageType type : values()) {
            if (type.getId() == id) {
                return type;
            }
        }
        return com.example.mp_2019_android.MessageType.Text; // could be error prone
    }

    public int getId(){
        return this.id;
    }
}
