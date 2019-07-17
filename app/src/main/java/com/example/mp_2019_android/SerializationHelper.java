package com.example.mp_2019_android;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;

public class SerializationHelper {

    public static byte[] serialize(Object obj) throws IOException{
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(out);
        // transform object to stream and
        os.writeObject(obj);
        // then to a byte array
        return out.toByteArray();
    }

    public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException{
        // transform bytes to stream and
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        // transform bytes stream to object stream and
        ObjectInputStream is = new ObjectInputStream(in);
        // then to an object
        return is.readObject();
    }
}
