package com.xira.humanec_eye_app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.xira.humanec_eye_app.R;
import com.xira.humanec_eye_app.api.ApiRepository;
import com.xira.humanec_eye_app.utils.NetworkUtils;
import com.xira.humanec_eye_app.utils.ToastUtils;

import java.util.Map;

public class PinActivity extends AppCompatActivity implements OnOtpFilledListener {
    private TextView sendOtpTxt;
    private Button submit;
    private ImageView backButtonPin;
    private EditText otpDigit1, otpDigit2, otpDigit3, otpDigit4;
    private EditText[] otpFields;
    private ApiRepository apiRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);

        final String phoneNumber = getIntent().getStringExtra("phone_number");
        otpDigit1 = findViewById(R.id.otpDigit1);
        otpDigit2 = findViewById(R.id.otpDigit2);
        otpDigit3 = findViewById(R.id.otpDigit3);
        otpDigit4 = findViewById(R.id.otpDigit4);
        sendOtpTxt = findViewById(R.id.sendOtp);
        submit = findViewById(R.id.submitLogin);
        backButtonPin = findViewById(R.id.backButtonPin);

        final TextView numberTextView = findViewById(R.id.number);
        numberTextView.setText(phoneNumber);

        apiRepository = ApiRepository.getInstance(this);

        // Initialize OTP fields array
        otpFields = new EditText[]{otpDigit1, otpDigit2, otpDigit3, otpDigit4};

        // Set up OtpTextWatcher for each OTP field
        setupOtpAutoMove();

        sendOtpTxt.setOnClickListener(view -> sendOTP(phoneNumber));

        backButtonPin.setOnClickListener(view -> {
            Intent intent = new Intent(PinActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        submit.setOnClickListener(view -> submit(phoneNumber));
    }

    private void submit(String phoneNumber) {
        String otp = getEnteredOtp();

        if (otp.length() == 4) {
            if(NetworkUtils.isNetworkConnected(this)) {
                apiRepository.loginWithOTP(Map.of("username", phoneNumber, "password", otp), PinActivity.this)
                        .observe(PinActivity.this, success -> {
                            if (success) {
                                Intent intent = new Intent(PinActivity.this, OptionActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                intent.putExtra("isFromPinOtp", true);
                                startActivity(intent);
                                finish();
                            } else {
                                ToastUtils.showErrorToast(this, "You Entered Incorrect PIN!");
                            }
                        });
            }else{
                ToastUtils.showErrorToast(this, "Please connect to the internet.");

            }
        } else {
            ToastUtils.showErrorToast(PinActivity.this, "Please enter a 4-digit PIN");
        }
    }

    private void sendOTP(String number) {
        apiRepository.sendWithOTP(number,getApplicationContext()).observe(this, success -> {
            if (success) {
                ToastUtils.showSuccessToast(this, "OTP Sent Successfully!");
                Intent intent = new Intent(this, OtpActivity.class);
                intent.putExtra("phone_number", number); // Replace with dynamic number
                startActivity(intent);
            } else {
                ToastUtils.showErrorToast(this, "Failed to send OTP.");
            }
        });
    }

    private void setupOtpAutoMove() {
        // Set up OtpTextWatcher for each OTP field
        otpDigit1.addTextChangedListener(new OtpTextWatcher(otpDigit1,otpDigit2, otpFields, this));
        otpDigit2.addTextChangedListener(new OtpTextWatcher(otpDigit2, otpDigit3, otpFields, this));
        otpDigit3.addTextChangedListener(new OtpTextWatcher(otpDigit3, otpDigit4, otpFields, this));
        otpDigit4.addTextChangedListener(new OtpTextWatcher(otpDigit4, null, otpFields, this));
    }

    private String getEnteredOtp() {
        return otpDigit1.getText().toString() +
                otpDigit2.getText().toString() +
                otpDigit3.getText().toString() +
                otpDigit4.getText().toString();
    }

    @Override
    public void onOtpFilled(String otp) {
        // Automatically submit the OTP when all fields are filled
        submit(getIntent().getStringExtra("phone_number"));
    }
}