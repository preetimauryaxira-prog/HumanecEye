package com.xira.humanec_eye_app.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.xira.humanec_eye_app.R;

public class DateTimeValidator {
    private static final String TAG = "DateTimeValidator";
    public interface CleanupCallback {
        void cleanup();
    }
    public static boolean isAutoTimeEnabled(Context context) {
        try {
            int autoTime = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AUTO_TIME, 0);
            return autoTime == 1;
        } catch (Exception e) {
            Log.e(TAG, "Error checking auto time setting: " + e.getMessage());
            return true;
        }
    }

    public static void showDateTimeErrorDialog(Activity activity, CleanupCallback cleanupCallback) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
      
        if (cleanupCallback != null) {
            cleanupCallback.cleanup();
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Date & Time Settings Error");
        builder.setMessage("Please enable automatic date and time in your phone settings to ensure accurate attendance tracking.");
        builder.setCancelable(false);

        builder.setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                   Intent intent = new Intent(Settings.ACTION_DATE_SETTINGS);
                    activity.startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error opening settings: " + e.getMessage());
                    Intent intent = new Intent(Settings.ACTION_SETTINGS);
                    activity.startActivity(intent);
                }
                activity.finish();
            }
        });
        
        builder.setNegativeButton("Exit App", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                activity.finish();
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.show();

        customizeDialogButtons(activity, dialog);
    }

    private static void customizeDialogButtons(Activity activity, AlertDialog dialog) {
        try {
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                    activity.getResources().getColor(android.R.color.darker_gray));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackground(
                        activity.getResources().getDrawable(R.drawable.rounded_item_background));
                }
            }
            
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
                    activity.getResources().getColor(android.R.color.darker_gray));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setBackground(
                        activity.getResources().getDrawable(R.drawable.rounded_item_background));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error customizing dialog buttons: " + e.getMessage());
        }
    }

    public static boolean validateAndShowDialog(Activity activity, CleanupCallback cleanupCallback) {
        if (!isAutoTimeEnabled(activity)) {
            showDateTimeErrorDialog(activity, cleanupCallback);
            return false;
        }
        return true;
    }
    
    public static boolean validateAndShowDialog(Activity activity) {
        return validateAndShowDialog(activity, null);
    }
}
