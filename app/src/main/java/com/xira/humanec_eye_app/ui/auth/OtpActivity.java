package com.xira.humanec_eye_app.ui.auth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.xira.humanec_eye_app.R;
import com.xira.humanec_eye_app.api.ApiRepository;
import com.xira.humanec_eye_app.utils.NetworkUtils;
import com.xira.humanec_eye_app.utils.ToastUtils;

import java.util.Map;
public class OtpActivity extends AppCompatActivity implements OnOtpFilledListener {

    private TextView timerText, resendOtpText, resentOtpButton, number, loginByPin;
    private CountDownTimer countDownTimer;
    private EditText otpDigit1, otpDigit2, otpDigit3, otpDigit4;
    private ApiRepository apiRepository;
    private Button submit;
    private ImageView backButton;
    private EditText[] otpFields;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify);

        // Get phone number from Intent
        String phoneNumber = getIntent().getStringExtra("phone_number");
        apiRepository = ApiRepository.getInstance(this);

        // Initialize Views
        otpDigit1 = findViewById(R.id.otpDigit1);
        otpDigit2 = findViewById(R.id.otpDigit2);
        otpDigit3 = findViewById(R.id.otpDigit3);
        otpDigit4 = findViewById(R.id.otpDigit4);
        timerText = findViewById(R.id.timerText);
        number = findViewById(R.id.number);
        resendOtpText = findViewById(R.id.resendOtpText);
        resentOtpButton = findViewById(R.id.resendOtpButton);
        submit = findViewById(R.id.submitOtpButton);
        loginByPin = findViewById(R.id.loginByPin);
        backButton=findViewById(R.id.backButton);

        // Set the phone number in UI
        number.setText("+91" + phoneNumber);

        // Initialize OTP fields array
        otpFields = new EditText[]{otpDigit1, otpDigit2, otpDigit3, otpDigit4};

        // Set up OtpTextWatcher for each OTP field

            // Set up OtpTextWatcher for each OTP field
            otpDigit1.addTextChangedListener(new OtpTextWatcher(otpDigit1,otpDigit2, otpFields, this));
            otpDigit2.addTextChangedListener(new OtpTextWatcher(otpDigit2, otpDigit3, otpFields, this));
            otpDigit3.addTextChangedListener(new OtpTextWatcher(otpDigit3, otpDigit4, otpFields, this));
            otpDigit4.addTextChangedListener(new OtpTextWatcher(otpDigit4, null, otpFields, this));


        loginByPin.setOnClickListener(view -> sendPinPage(phoneNumber));

        resentOtpButton.setOnClickListener(view -> resendOTP(phoneNumber));

        submit.setOnClickListener(view -> sendToOption(phoneNumber));

        backButton.setOnClickListener(view -> {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });


        // Start OTP countdown
        startOtpCountdown();
    }

    @Override
    public void onOtpFilled(String otp) {
        // Automatically submit the OTP when all fields are filled
        sendToOption(getIntent().getStringExtra("phone_number"));
    }

    private void sendToOption(String phoneNumber) {
        String otp = getEnteredOtp();
        Log.d("OtpActivity", "Getting otp: " + otp);
        if (otp.length() == 4) {
            if(NetworkUtils.isNetworkConnected(this)) {
                apiRepository.loginWithOTP(Map.of("username", phoneNumber, "password", otp), this)
                        .observe(this, success -> {
                            if (success) {
                                Intent intent = new Intent(this, OptionActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                intent.putExtra("isFromPinOtp", true);
                                startActivity(intent);
                                finish();
                            }
                        });
            }else{
                ToastUtils.showErrorToast(this, "Please connect to the internet.");

            }
        } else {
            ToastUtils.showErrorToast(this, "Please enter a 4-digit PIN");
        }
    }

    private void startOtpCountdown() {
        countDownTimer = new CountDownTimer(60000, 1000) { // 60 seconds countdown
            @SuppressLint("SetTextI18n")
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setText("00:" + millisUntilFinished / 1000);
            }

            @Override
            public void onFinish() {
                timerText.setVisibility(View.GONE);
                resendOtpText.setVisibility(View.VISIBLE);
                resentOtpButton.setVisibility(View.VISIBLE);
            }
        }.start();
    }

    private void resendOTP(String number) {
        resendOtpText.setVisibility(View.GONE);
        resentOtpButton.setVisibility(View.GONE);
        timerText.setVisibility(View.VISIBLE);
        apiRepository.sendWithOTP(number,getApplicationContext()).observe(this, success -> {
            if (success) {
                Toast.makeText(this, "OTP Sent Successfully!", Toast.LENGTH_SHORT).show();
                startOtpCountdown();
            }
        });
    }

    private void sendPinPage(String number) {
        Intent intent = new Intent(this, PinActivity.class);
        intent.putExtra("phone_number", number);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private String getEnteredOtp() {
        return otpDigit1.getText().toString() +
                otpDigit2.getText().toString() +
                otpDigit3.getText().toString() +
                otpDigit4.getText().toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel(); // Stop the timer to prevent memory leaks
        }
    }
}