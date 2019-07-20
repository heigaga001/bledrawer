package com.ble.bluetoothdrawing;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;

public class ImageUtil {

    public static String createImageFile( int width,int height,float strokeWidth,int color,float[] lines){
        Bitmap bitmap = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_4444);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(color);
        paint.setStrokeWidth(strokeWidth);
        canvas.drawLines(lines,paint);
        try {
            ByteArrayOutputStream fos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            byte[] data = fos.toByteArray();
            String image = Base64.encodeToString(data,Base64.DEFAULT);
            return image;
        } catch (Exception e) {

        }
        return null;
    }

}
