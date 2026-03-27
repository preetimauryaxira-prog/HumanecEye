package com.xira.humanec_eye_app.utils;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.xira.humanec_eye_app.R;

public class ToastUtils {
    private static String lastMessage = ""; // Track the last shown message
    private static long lastToastTime = 0; // Track the last toast time
    private static final long TOAST_COOLDOWN = 2000; // 2 seconds cooldown

    /**
     * Show a success toast with the given message.
     * By default, repeated toasts are not shown.
     */
    public static void showSuccessToast(Context context, String message) {
        showCustomToast(context, message, R.layout.custom_success_toast, R.drawable.ic_check_mark, false);
    }

    /**
     * Show a success toast with the given message.
     * @param allowDuplicate If true, allows repeated toasts even within the cooldown period.
     */
    public static void showSuccessToast(Context context, String message, boolean allowDuplicate) {
        showCustomToast(context, message, R.layout.custom_success_toast, R.drawable.ic_check_mark, allowDuplicate);
    }

    /**
     * Show an error toast with the given message.
     * By default, repeated toasts are not shown.
     */
    public static void showErrorToast(Context context, String message) {
        showCustomToast(context, message, R.layout.custom_error_toast, R.drawable.ic_error, false);
    }

    /**
     * Show an error toast with the given message.
     * @param allowDuplicate If true, allows repeated toasts even within the cooldown period.
     */
    public static void showErrorToast(Context context, String message, boolean allowDuplicate) {
        showCustomToast(context, message, R.layout.custom_error_toast, R.drawable.ic_error, allowDuplicate);
    }

    /**
     * Show a custom toast with the given message, layout, and icon.
     * @param allowDuplicate If true, allows repeated toasts even within the cooldown period.
     */
    private static void showCustomToast(Context context, String message, int layoutResId, int iconResId, boolean allowDuplicate) {
        long currentTime = System.currentTimeMillis();

        // Prevent duplicate toasts if the same message is triggered within the cooldown time (unless allowDuplicate is true)
        if (!allowDuplicate && message.equals(lastMessage) && (currentTime - lastToastTime) < TOAST_COOLDOWN) {
            return; // Ignore duplicate toast within cooldown time
        }

        // Update the last message and toast time
        lastMessage = message;
        lastToastTime = currentTime;

        // Inflate the custom layout
        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(layoutResId, null);

        // Set the message and icon
        TextView toastText = layout.findViewById(R.id.toast_text);
        ImageView toastIcon = layout.findViewById(R.id.toast_icon);
        toastText.setText(message);
        toastIcon.setImageResource(iconResId);
        // Create and show the Toast
        Toast toast = new Toast(context);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.show();
    }
}