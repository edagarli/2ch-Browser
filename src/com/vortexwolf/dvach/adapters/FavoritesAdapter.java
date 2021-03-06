package com.vortexwolf.dvach.adapters;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.vortexwolf.chan.R;
import com.vortexwolf.dvach.db.FavoritesDataSource;
import com.vortexwolf.dvach.db.FavoritesEntity;

public class FavoritesAdapter extends ArrayAdapter<FavoritesEntity> {
    private final LayoutInflater mInflater;
    private final FavoritesDataSource mFavoritesDataSource;
    
    public FavoritesAdapter(Context context, List<FavoritesEntity> objects, FavoritesDataSource favoritesDataSource) {
        super(context, -1, objects);
        this.mInflater = LayoutInflater.from(context);
        this.mFavoritesDataSource = favoritesDataSource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final FavoritesEntity item = this.getItem(position);

        View view = convertView == null ? this.mInflater.inflate(R.layout.tabs_list_item, null) : convertView;

        TextView titleView = (TextView) view.findViewById(R.id.tabs_item_title);
        TextView urlView = (TextView) view.findViewById(R.id.tabs_item_url);
        ImageView deleteButton = (ImageView) view.findViewById(R.id.tabs_item_delete);

        titleView.setText(item.getTitle());
        urlView.setText(item.getUrl());

        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FavoritesAdapter.this.removeItem(item);
            }
        });
        
        return view;
    }
    
    public void removeItem(FavoritesEntity item) {
        this.mFavoritesDataSource.removeFromFavorites(item.getUrl());
        this.remove(item);
    }
}
