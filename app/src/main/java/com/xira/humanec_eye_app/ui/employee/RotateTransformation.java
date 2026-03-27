package com.xira.humanec_eye_app.ui.employee;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Log;

import com.squareup.picasso.Transformation;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class RotateTransformation implements Transformation {
    private String imageUrl;

    public RotateTransformation(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @Override
    public Bitmap transform(Bitmap source) {
        int rotation = getRotationFromUrl(imageUrl);
        if (rotation == 0) {
            return source; // No rotation needed
        }

        // Rotate the bitmap
        Matrix matrix = new Matrix();
        matrix.postRotate(rotation);
        Bitmap rotatedBitmap = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        source.recycle(); // Recycle old bitmap to prevent memory leaks
        return rotatedBitmap;
    }

    @Override
    public String key() {
        return "rotate_" + imageUrl;
    }

    private int getRotationFromUrl(String imageUrl) {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            ExifInterface exifInterface = new ExifInterface(connection.getInputStream());

            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            Log.e("RotateTransformation", "Error reading EXIF data", e);
            return 0;
        }
    }
}
