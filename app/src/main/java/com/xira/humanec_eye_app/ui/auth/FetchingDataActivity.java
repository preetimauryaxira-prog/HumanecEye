package com.xira.humanec_eye_app.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;

import com.xira.humanec_eye_app.model.FaceData;
import com.xira.humanec_eye_app.ui.camera.FileAccess;
import com.xira.humanec_eye_app.Embedding;
import com.xira.humanec_eye_app.MainActivity;
import com.xira.humanec_eye_app.R;
import com.xira.humanec_eye_app.api.ApiRepository;
import com.xira.humanec_eye_app.model.Employee;
import com.xira.humanec_eye_app.utils.ToastUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FetchingDataActivity extends AppCompatActivity {
    private TextView statusText;
    private TextView doneText;
    private ProgressBar progressBar;
    private Button continueButton;
    private ImageView checkMarkImage;
    private List<FaceData> registeredFaces = new ArrayList<>();
    private static final String TAG = "FetchingData";

    private List<Employee> employees;
    private Context context;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fetchdata);

        context = this;
        statusText = findViewById(R.id.statusText);
        doneText = findViewById(R.id.doneText);
        progressBar = findViewById(R.id.progressBar);
        continueButton = findViewById(R.id.continueButton);
        checkMarkImage = findViewById(R.id.checkMarkImage);

        continueButton.setOnClickListener(view -> navigateToHome());

        fetchData();
    }
    private void fetchData() {
        statusText.setText("Fetching data...");
        progressBar.setVisibility(View.VISIBLE);
        continueButton.setVisibility(View.GONE);
        doneText.setVisibility(View.GONE);
        checkMarkImage.setVisibility(View.GONE);

        LiveData<List<Employee>> employeeLiveData = ApiRepository.getInstance(this).getRegisteredEmployees("", context);
        employeeLiveData.observe(this, employeeData -> {
            if (employeeData != null && !employeeData.isEmpty()) {
                Log.d(TAG, "Employee data received: " + employeeData.size() + " employees");
                employees = employeeData;
                processEmployeeData();
            } else {
                Log.e(TAG, "Failed to fetch employees or empty list received");
                employees = new ArrayList<>();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setVisibility(View.GONE);
                    doneText.setVisibility(View.VISIBLE); // Show "Done" message
                    checkMarkImage.setVisibility(View.VISIBLE); // Show green circle with check mark
                    Toast.makeText(context, "No employee data available", Toast.LENGTH_SHORT).show();
                    continueButton.setVisibility(View.VISIBLE);
                    continueButton.setEnabled(true); // Allow navigation even on failure
                });
            }
            employeeLiveData.removeObservers(this); // Clean up observer
        });
    }

    private void processEmployeeData() {
        executorService.execute(() -> {
            try {
                int total = employees.size();
                for (int i = 0; i < total; i++) {
                    Employee employee = employees.get(i);
                    float[] embedding = Embedding. decodeEmbedding(employee.getImg());

                    if (embedding != null) {
                        Log.d(TAG, "Embedding for " + employee.getEmpName() + ": " + Arrays.toString(embedding));
                        registeredFaces.add(new FaceData(employee.getEmpName(), employee.getCode(), embedding));
                    } else {
                        Log.w(TAG, "Failed to decode embedding for " + employee.getEmpName());
                    }

                    int progress = (i + 1) * 100 / total;
                    int finalI = i;
                    runOnUiThread(() -> statusText.setText("Processing " + (finalI + 1) + " of " + total + " (" + progress + "%)"));
                }

                saveRegisteredFaces();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setVisibility(View.GONE);
                    doneText.setVisibility(View.VISIBLE); // Show "Done" message
                    checkMarkImage.setVisibility(View.VISIBLE); // Show green circle with check mark
                    continueButton.setVisibility(View.VISIBLE);
                    continueButton.setEnabled(true); // Enable the Continue button
                });
            } catch (Exception e) {
                Log.e(TAG, "Error processing employee data", e);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Failed to process data");
                  ToastUtils.showErrorToast(context, "Error processing data: " + e.getMessage());
                    continueButton.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void saveRegisteredFaces() {
        try {
            var fileAccess= new FileAccess(this);

            fileAccess.saveRegisteredFaces(registeredFaces);

        } catch (Exception e) {
            Log.e(TAG, "Error saving face data", e);
        }
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("navigateTo", "home");
        intent.putExtra("home",true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}