package com.example.mp_2019_android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;


public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView endpointName;
        TextView textMessage;
        TextView timeStamp;

        MessageViewHolder(View itemView) {
            super(itemView);
            endpointName = itemView.findViewById(R.id.endpointName);
            textMessage = itemView.findViewById(R.id.textMessage);
            timeStamp = itemView.findViewById(R.id.timeStamp);

        }
    }

    /* used to visualize a sent or received video message */
    class VideoViewHolder extends RecyclerView.ViewHolder {
        TextView endpointName;
        TextView timeStamp;
        ImageView playVideo;

        VideoViewHolder(View itemView) {
            super(itemView);
            endpointName = itemView.findViewById(R.id.endpointName);
            timeStamp = itemView.findViewById(R.id.timeStamp);
            playVideo = itemView.findViewById(R.id.ImageView_play_video);
        }
    }

    // All messages
    private List<Message> messages;
    private String localEndpointName;
    private Context context;

    MessageAdapter(List<Message> messages, String localEndpointName, Context context) {
        this.messages = messages;
        this.localEndpointName = localEndpointName;
        this.context = context;
    }

    /* determines the target layout index which will be used in recycler view */
    @Override
    public int getItemViewType(int i) {
        int targetLayout = R.layout.send_msg_bubble;
        String sourceEndpointName = messages.get(i).getSourceEndpointName();
        MessageType msgType = messages.get(i).getMessageType();
        switch(msgType){
            case Text:
                if(msgSrcEqualsDst(localEndpointName, sourceEndpointName)) {
                    targetLayout = R.layout.send_msg_bubble;
                }else{
                    targetLayout = R.layout.recieve_msg_bubble;
                }
                break;
            case VideoFile:
                if(msgSrcEqualsDst(localEndpointName, sourceEndpointName)) {
                    targetLayout =  R.layout.send_video_bubble;
                }else{
                    targetLayout =  R.layout.receive_video_bubble;
                }
                break;
        }
        return targetLayout;
    }

    private boolean msgSrcEqualsDst(String source, String destination){
        // checks if a message should be placed on the right or left side in the recycler view
        // right side: you sent the message
        // left side: you received the message
        return source.equals(destination);
    }

    /* determines the target ViewHolder which will be used in recycler view */
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        switch(viewType){
            case R.layout.send_msg_bubble:
                return new MessageViewHolder(LayoutInflater.from(viewGroup.getContext())
                        .inflate(viewType, viewGroup, false));
            case R.layout.recieve_msg_bubble:
                return new MessageViewHolder(LayoutInflater.from(viewGroup.getContext())
                        .inflate(viewType, viewGroup, false));
            case R.layout.send_video_bubble:
                return new VideoViewHolder(LayoutInflater.from(viewGroup.getContext())
                        .inflate(viewType, viewGroup, false));
            case R.layout.receive_video_bubble:
                return new VideoViewHolder(LayoutInflater.from(viewGroup.getContext())
                        .inflate(viewType, viewGroup, false));
        }
        return null;
    }

    /* determines the content for the target layout which will be used in recycler view */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, @SuppressLint("RecyclerView") int i) {
        switch(viewHolder.getItemViewType()){
            case R.layout.send_msg_bubble: {
                MessageViewHolder messageViewHolder = (MessageViewHolder)viewHolder;
                messageViewHolder.endpointName.setText(messages.get(i).getSourceEndpointName());
                messageViewHolder.textMessage.setText(messages.get(i).getMessage());
                messageViewHolder.timeStamp.setText(messages.get(i).getCurrentTime());
            }
            break;
            case R.layout.recieve_msg_bubble: {
                MessageViewHolder messageViewHolder = (MessageViewHolder)viewHolder;
                messageViewHolder.endpointName.setText(messages.get(i).getSourceEndpointName());
                messageViewHolder.textMessage.setText(messages.get(i).getMessage());
                messageViewHolder.timeStamp.setText(messages.get(i).getCurrentTime());
            }
            break;
            case R.layout.send_video_bubble: {
                VideoViewHolder videoViewHolder = (VideoViewHolder)viewHolder;
                videoViewHolder.endpointName.setText(messages.get(i).getSourceEndpointName());
                if(messages.get(i).getVideoFilePath() != null){
                    videoViewHolder.playVideo.setOnClickListener(v -> {
                        Log.d("MessageAdapter", "onClick: send_video_bubble");
                        String videoUrl= messages.get(i).getVideoFilePath(); //"/sdcard/zzzz.3gp";
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl));
                        intent.setDataAndType(Uri.parse(videoUrl), "video/mp4");
                        context.startActivity(intent);
                    });
                }
                videoViewHolder.timeStamp.setText(messages.get(i).getCurrentTime());
            }
            break;
            case R.layout.receive_video_bubble: {
                VideoViewHolder videoViewHolder = (VideoViewHolder)viewHolder;
                videoViewHolder.endpointName.setText(messages.get(i).getSourceEndpointName());

                if(messages.get(i).getVideoFilePath() != null){
                    videoViewHolder.playVideo.setOnClickListener(v -> {
                        Log.d("MessageAdapter", "onClick: receive_video_bubble");
                        String videoUrl= messages.get(i).getVideoFilePath(); //"/sdcard/zzzz.3gp";
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl));
                        intent.setDataAndType(Uri.parse(videoUrl), "video/mp4");
                        context.startActivity(intent);
                    });
                }
                videoViewHolder.timeStamp.setText(messages.get(i).getCurrentTime());
            }
            break;
        }
    }

    /* returns the amount of elements in the message list */
    @Override
    public int getItemCount() {
        return messages.size();
    }
}
