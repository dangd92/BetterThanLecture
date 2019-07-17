package com.example.mp_2019_android;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class DiscovererAdapter extends  RecyclerView.Adapter<DiscovererAdapter.DiscoverViewHolder>{

    private LinkedList<Endpoint> endpointList;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class DiscoverViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public TextView endpointName;

        public DiscoverViewHolder(View itemView) {
            super(itemView);
            endpointName = (TextView) itemView.findViewById(R.id.endpointNames);
        }
    }

    public DiscovererAdapter(LinkedList<Endpoint> dEndpoints){
        this.endpointList = dEndpoints;
    }

    @Override
    public int getItemViewType(int i) {
        return R.layout.discovered_users;
    }

    @Override
    public DiscoverViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        return new DiscoverViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(viewType, viewGroup, false));
    }

    @Override
    public void onBindViewHolder(@NonNull DiscoverViewHolder discoverViewHolder, int i) {
        discoverViewHolder.endpointName.setText(endpointList.get(i).getName());
    }

    @Override
    public int getItemCount() {
        return endpointList.size();
    }
    /*

    // Create new views (invoked by the layout manager)
    @Override
    public DiscovererAdapter.DiscoverViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        TextView v = (TextView) LayoutInflater.from(parent.getContext()).inflate(R.layout.my_text_view, parent, false);
        DiscoverViewHolder vh = new DiscoverViewHolder();
        return vh;
    }*/
}
