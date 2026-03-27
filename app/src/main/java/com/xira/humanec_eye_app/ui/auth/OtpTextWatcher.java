package com.xira.humanec_eye_app.ui.auth;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

public class OtpTextWatcher implements TextWatcher {

    private EditText otpDigit1;
    private View currentView, nextView;
    private OnOtpFilledListener otpFilledListener; // Callback listener
    private EditText[] otpFields; // Array of all OTP fields

    // Constructor for the first OTP digit
    public OtpTextWatcher(EditText firstOtp, EditText[] otpFields, OnOtpFilledListener listener) {
        this.otpDigit1 = firstOtp;
        this.currentView = firstOtp;
        this.otpFields = otpFields;
        this.otpFilledListener = listener;
    }

    // Constructor for other OTP digits
    public OtpTextWatcher(View currentView, View nextView, EditText[] otpFields, OnOtpFilledListener listener) {
        this.currentView = currentView;
        this.nextView = nextView;
        this.otpFields = otpFields;
        this.otpFilledListener = listener;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (currentView == null) {
            return; // Avoid NullPointerException
        }
        if (s.length() == 1 && nextView != null) {

            nextView.requestFocus();
        } else if (s.length() == 0) {
            // Handle deletion
            if (currentView == otpDigit1) {
                // If it's the first OTP digit, do nothing (cannot move focus left)
                return;
            } else {
                // Move focus to the previous view
                View previousView = currentView.focusSearch(View.FOCUS_LEFT);
                if (previousView != null) {
                    previousView.requestFocus();
                }
            }
        }

        // Check if all OTP fields are filled
        if (areAllOtpFieldsFilled()) {
            String otp = getOtp();
            if (otpFilledListener != null) {
                otpFilledListener.onOtpFilled(otp); // Notify listener
            }
        }
    }


    @Override
    public void afterTextChanged(Editable s) {}

    // Check if all OTP fields are filled
    private boolean areAllOtpFieldsFilled() {
        for (EditText otpField : otpFields) {
            if (otpField.getText().toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // Get the complete OTP
    private String getOtp() {
        StringBuilder otp = new StringBuilder();
        for (EditText otpField : otpFields) {
            otp.append(otpField.getText().toString().trim());
        }
        return otp.toString();
    }
}