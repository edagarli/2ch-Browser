package com.vortexwolf.dvach.common.utils;

import com.vortexwolf.chan.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

public class AppearanceUtils {

    public static void showToastMessage(Context context, String msg) {
        Toast toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
        toast.show();
    }

    public static ListViewPosition getCurrentListPosition(ListView listView) {
        int index = 0;
        int top = 0;

        if (listView != null) {
            index = listView.getFirstVisiblePosition();
            View v = listView.getChildAt(0);
            top = (v == null) ? 0 : v.getTop();
        }

        ListViewPosition position = new ListViewPosition(index, top);
        return position;
    }

    public static void showImageProgressBar(final View indeterminateProgressBar, final ImageView imageView) {
        if (indeterminateProgressBar != null) {
            indeterminateProgressBar.setVisibility(View.VISIBLE);
        }
    }

    public static void hideImageProgressBar(final View indeterminateProgressBar, final ImageView imageView) {
        if (indeterminateProgressBar != null) {
            indeterminateProgressBar.setVisibility(View.GONE);
        }
    }
    
    public static void clearImage(ImageView image) {
        image.setImageResource(android.R.color.transparent);
    }
    
    public static Bitmap reduceBitmapSize(Resources resources, Bitmap bitmap){
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        int oldSize = Math.max(width, height);
        int newSize = resources.getDimensionPixelSize(R.dimen.thumbnail_size);
        
        float scale = newSize / (float)oldSize;

        if(scale >= 1.0) {
            return bitmap;
        }

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, (int)(width * scale), (int)(height * scale), true);
        bitmap.recycle();
        return resizedBitmap;
    }

    public static class ListViewPosition {

        public ListViewPosition(int position, int top) {
            this.position = position;
            this.top = top;
        }

        public int position;
        public int top;
    }
}
