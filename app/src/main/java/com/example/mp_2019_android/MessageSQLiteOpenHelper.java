package com.example.mp_2019_android;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.Nullable;

public class MessageSQLiteOpenHelper extends SQLiteOpenHelper {

    public static final String DATABASE_Name = "NewDB.db";
    public static final int DATABASE_Version = 1;

    public static  final  String TABLE_CHAT_HISTORY = "chatHistory";
    public static final String COL_1_ID = "ID";//_id
    public static final String COL_2_Msg = "Message";
    public static final String COL_3_SrcEndptNm = "SourceEndpointName";
    public static final String COL_4_CurTm = "CurrentTime";
    public static final String COL_5_MsgType = "MessageType";

    public static final  String TABLE_USER_NAME = "userName";
    public static final String Col_1_id = "ID";//_id
    public static final String COL_2_LclEndptNm = "localEndpointName";
    public static final String COL_3_LclEndptId = "localEndpointId";

    private static final String SQL_CREATE_TABLE_1 = "create table " + TABLE_CHAT_HISTORY + " ("
            + COL_1_ID + " integer primary key autoincrement, " + COL_2_Msg + " text not null, " + COL_3_SrcEndptNm + " text not null, " + COL_4_CurTm + " text not null, " + COL_5_MsgType + " integer not null);";

    private static final String SQL_CREATE_TABLE_2 = "create table " + TABLE_USER_NAME + " ("
            + Col_1_id + " integer primary key autoincrement, " + COL_2_LclEndptNm + " text not null, " + COL_3_LclEndptId + " text not null);";

    public MessageSQLiteOpenHelper(@Nullable Context context) {
        super(context, DATABASE_Name, null, DATABASE_Version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_TABLE_1);
        db.execSQL(SQL_CREATE_TABLE_2);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("drop table if exists " + TABLE_CHAT_HISTORY + ";");
        db.execSQL("drop table if exists " + TABLE_USER_NAME + ";");
        onCreate(db);
    }

    public boolean insertDataUserName(String localEndpointName, String localEndpointId){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();

        contentValues.put(COL_2_LclEndptNm, localEndpointName);
        contentValues.put(COL_3_LclEndptId, localEndpointId);
        long result = db.insert(TABLE_USER_NAME, null, contentValues);
        // clean up
        db.close();
        contentValues.clear();

        // check for error after inserting
        if(result == -1){
          return false;
        }else{
            return true;
        }
    }

    public boolean updateDataUserName(Integer id, String localEndpointName){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(Col_1_id, id);
        contentValues.put(COL_2_LclEndptNm, localEndpointName);

        int effectedRows =  db.update(TABLE_USER_NAME, contentValues, "id = ?", new String[]{id + ""});
        if(effectedRows > 0){
            return true;
        }
        return false;
    }

    public boolean insertDataChatHistory(String message, String sourceEndpointName, String currentTime, MessageType messageType){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();

        contentValues.put(COL_2_Msg, message);
        contentValues.put(COL_3_SrcEndptNm, sourceEndpointName);
        contentValues.put(COL_4_CurTm, currentTime);
        contentValues.put(COL_5_MsgType, messageType.getId());
        long result = db.insert(TABLE_CHAT_HISTORY, null, contentValues);
        // clean up
        db.close();
        contentValues.clear();

        // check for error after inserting
        if(result == -1){
            return false;
        }else{
            return true;
        }
    }

    public boolean deleteAllRowsOfTable(String tableName){
        SQLiteDatabase db = this.getWritableDatabase();
        int affectedRows = db.delete(tableName, "1", null);
        return affectedRows > 0;
    }

    public Cursor getData(String tableName){
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor data = db.rawQuery("SELECT * FROM " + tableName, null);
        return data;
    }
}
