package com.xira.humanec_eye_app.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.xira.humanec_eye_app.R;
import com.xira.humanec_eye_app.api.ApiRepository;
import com.xira.humanec_eye_app.utils.NetworkUtils;
import com.xira.humanec_eye_app.utils.ToastUtils;

public class LoginActivity extends AppCompatActivity implements PinCheckCallback{
    private EditText phoneNumber;
    private Button verifyButton;
    private ApiRepository apiRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        apiRepository = ApiRepository.getInstance(this);
        verifyButton = findViewById(R.id.verifyButton);

        phoneNumber = findViewById(R.id.editTextNumber);

        verifyButton.setOnClickListener(v -> {
            String number = phoneNumber.getText().toString().trim();

            if (number.isEmpty()) {
                phoneNumber.setError("Please enter your number");
            } else if (number.length() < 10) {
                phoneNumber.setError("Number must be at least 10 digits");
            } else {
                verifyButton.setEnabled(false);
                verifyButton.setText("Processing...");
                checkIsSetPin(this);

            }
        });
    }

    private void checkIsSetPin(PinCheckCallback callback) {
       if(NetworkUtils.isNetworkConnected(this)){
           apiRepository.getUserDetail(String.valueOf(phoneNumber.getText())).observe(this, value -> {
               System.out.println("Received isSet value: " + value); // Debugging log
               callback.onResult(value);  // Pass only once
           });
       }else{
           ToastUtils.showErrorToast(this, "Please connect to the internet.");
           verifyButton.setEnabled(true);
           verifyButton.setText("Continue");
       }
    }

    private void login(String number) {

        apiRepository.sendWithOTP(number,getApplicationContext()).observe(this, success -> {
            // Re-enable the button and hide the loading indicator
            verifyButton.setEnabled(true);
            verifyButton.setText("Continue");
            if (success) {
               ToastUtils.showSuccessToast(this, "OTP Sent Successfully!");
                Intent intent = new Intent(this, OtpActivity.class);
                intent.putExtra("phone_number", number);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                verifyButton.setEnabled(true);
                verifyButton.setText("Continue");
            }
        });
    }

    private void navigateToPinPage(String number) {
        // Re-enable the button and hide the loading indicator
        verifyButton.setEnabled(true);
        verifyButton.setText("Continue");
        Intent intent = new Intent(this, PinActivity.class);
        intent.putExtra("phone_number", number);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onResult(boolean isSet) {
        String number = phoneNumber.getText().toString();  // Get phone number from input field
        System.out.println("Pin Set or not =="+isSet);
        if (isSet) {
            System.out.println("pin page");
            navigateToPinPage(number);
            saveInLocal();

        } else {
            System.out.println("Calling send OTP");
            login(number);

        }
    }


    void saveInLocal(){
        SharedPreferences sharedPreferences = this.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isSetPin", true);
        editor.apply(); // Asynchronous commit
    }
}