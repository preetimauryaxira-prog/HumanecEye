package com.xira.humanec_eye_app;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageConverter {

    public static File base64ToFile(Context context, String base64String, String fileName) {
        try {
            byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);
            File file = new File(context.getCacheDir(), fileName); // Use context.getCacheDir()
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(decodedBytes);
            fos.flush();
            fos.close();
            return file;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}


