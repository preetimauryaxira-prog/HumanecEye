package com.xira.humanec_eye_app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.xira.humanec_eye_app.ui.camera.FileAccess;
import com.xira.humanec_eye_app.R;
import com.xira.humanec_eye_app.api.ApiRepository;
import com.xira.humanec_eye_app.model.Business;
import com.xira.humanec_eye_app.utils.ToastUtils;

import java.io.File;

public class OptionActivity extends AppCompatActivity {

    private RadioGroup radioGroupBusiness;

    private Button btnContinue;
    private String selectedBusiness = null;

    private ApiRepository apiRepository;

    private TextView title;

    private FileAccess fileAccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options);
        fileAccess=new FileAccess(this);
        title = findViewById(R.id.optionTitle);
        radioGroupBusiness = findViewById(R.id.radioGroupBusiness);
        btnContinue = findViewById(R.id.btnSubmit);

        // Check if the activity is launched from PIN-OTP flow
        boolean isFromPinOtp = getIntent().getBooleanExtra("isFromPinOtp", false);

        // Set title visibility based on the flag
        if (isFromPinOtp) {
            title.setText("Welcome To\nHumanec Eye");
            title.setVisibility(View.VISIBLE);
        } else {
            title.setVisibility(View.GONE);
        }

        apiRepository = ApiRepository.getInstance(this);

        apiRepository.getAllBusiness(this).observe(this, businessList -> {
            if (businessList != null && !businessList.isEmpty()) {
                for (Business business : businessList) {
                    RadioButton radioButton = new RadioButton(this);
                    radioButton.setText(business.getORGName());
                    radioButton.setTextSize(18);
                    radioButton.setId(View.generateViewId());
                    radioButton.setTag(business.getId());
                    radioButton.setPadding(30, 10, 0, 16);
                    radioGroupBusiness.addView(radioButton);
                }
            } else {
                Toast.makeText(this, "No businesses found!", Toast.LENGTH_SHORT).show();
            }
        });

        radioGroupBusiness.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selectedRadioButton = findViewById(checkedId);
            selectedBusiness = selectedRadioButton.getTag().toString();
            btnContinue.setEnabled(true);
        });

        btnContinue.setOnClickListener(v -> {
            if (selectedBusiness == null || selectedBusiness.isEmpty()) {
                ToastUtils.showErrorToast(this, "Please Select Organization");
                return;
            }
            deleteFaceEmbeddingFile();
            apiRepository.switchBusiness(selectedBusiness, this).observe(this, success -> {
                if (success) {
                    Intent intent = new Intent(OptionActivity.this, FetchingDataActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    ToastUtils.showErrorToast(this, "Failed to Switch Business.");
                }
            });
        });
    }

    private void deleteFaceEmbeddingFile() {
        // List of files to delete
        fileAccess.clearAllFaces();

        String[] filesToDelete = {"face_embeddings.json", "attendance_records.json"};

        for (String fileName : filesToDelete) {
            File file = new File(getFilesDir(), fileName);

            // Check if the file exists
            if (file.exists()) {
                // Attempt to delete the file
                if (file.delete()) {
                    Log.d("ProfileFragment", fileName + " deleted successfully.");
                } else {
                    Log.e("ProfileFragment", "Failed to delete " + fileName + ".");
                }
            } else {
                Log.d("ProfileFragment", fileName + " does not exist.");
            }
        }
    }
}

