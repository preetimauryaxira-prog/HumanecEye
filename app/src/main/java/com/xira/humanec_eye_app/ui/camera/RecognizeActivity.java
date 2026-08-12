package com.xira.humanec_eye_app.ui.camera;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ml.quaterion.facenetdetection.model.ModelInfo;
import com.xira.humanec_eye_app.FirebaseLogger;
import com.xira.humanec_eye_app.model.Attendance;
import com.xira.humanec_eye_app.ui.camera.model.FaceNetModel;
import com.xira.humanec_eye_app.R;
import com.xira.humanec_eye_app.ui.auth.PinActivity;
import com.xira.humanec_eye_app.ui.camera.autoSync.AttendanceService;
import com.xira.humanec_eye_app.ui.camera.model.Models;
import com.xira.humanec_eye_app.utils.DateTimeValidator;
import com.xira.humanec_eye_app.utils.NetworkUtils;
import com.xira.humanec_eye_app.utils.ToastUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecognizeActivity extends AppCompatActivity {

    private PreviewView previewView;
    private RecogniseFrameAnalyser frameAnalyser;
    private FaceNetModel faceNetModel;
    private CameraService cameraService;
    private ModelInfo modelInfo = Models.Companion.getFACENET();
    private AttendanceService attendanceService;
    private TextView internetSpeedTextView;
    private Button syncBtn;

    private Handler mainHandler = new Handler();
//    private Handler syncCheckHandler = new Handler();
    private Handler syncCheckHandler = new Handler(Looper.getMainLooper());
    private static final int SYNC_CHECK_INTERVAL = 5000; 
    private static final int SPEED_UPDATE_INTERVAL = 1000; 

    private boolean isSyncing = false;

    private Runnable speedUpdater = new Runnable() {
        @Override
        public void run() {
            updateInternetSpeed();
            mainHandler.postDelayed(this, SPEED_UPDATE_INTERVAL);
        }
    };

    private Runnable syncCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkUnsyncedData();

            // Auto-retry sync if network is available and not currently syncing
            if (NetworkUtils.isNetworkConnected(RecognizeActivity.this) &&
                    !isSyncing &&
                    !attendanceService.getUnsyncedAttendance().isEmpty()) {
                showAttendanceSyncDialog(true);
            }

            syncCheckHandler.postDelayed(this, SYNC_CHECK_INTERVAL);
        }
    };

//    private Runnable syncCheckRunnable = new Runnable() {
//        @Override
//        public void run() {
////            checkUnsyncedData();
//            if (NetworkUtils.isNetworkConnected(RecognizeActivity.this)
//                    && !isSyncing
//                    && attendanceService.hasUnsyncedData()) {
//
//                startAutoSync();
//            }
//
//            syncCheckHandler.postDelayed(this, SYNC_CHECK_INTERVAL); // 15 sec
//        }
//    };



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);

        // Initialize services and views
        attendanceService = new AttendanceService(this);
        previewView = findViewById(R.id.preview_view);
        Button adminView = findViewById(R.id.adminView);
        syncBtn = findViewById(R.id.sync);
        internetSpeedTextView = findViewById(R.id.internetSpeed);

        // Setup sync button
        syncBtn.setEnabled(false);
        syncBtn.setAlpha(0.5f);
        syncBtn.setOnClickListener(v -> {
            if (!isSyncing) {
                showAttendanceSyncDialog(true);
            }
        });

        // Initialize FaceNet and camera
        faceNetModel = new FaceNetModel(this, modelInfo, true, true);
        frameAnalyser = new RecogniseFrameAnalyser(this, findViewById(R.id.faceOverlay), faceNetModel);
        cameraService = new CameraService(this, this, previewView, frameAnalyser);

        // Check camera permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        } else {
            cameraService.startCameraPreview();
        }

        adminView.setOnClickListener(v -> showAttendanceSyncDialog(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Check datetime settings when app resumes
        if (!DateTimeValidator.validateAndShowDialog(this, () -> {
            // Cleanup callback - stop camera and periodic tasks
            if (cameraService != null) {
                cameraService.stopCamera();
            }
            mainHandler.removeCallbacks(speedUpdater);
            syncCheckHandler.removeCallbacks(syncCheckRunnable);
        })) {
            return;
        }
        
        // Start periodic tasks
        mainHandler.post(speedUpdater);
        syncCheckHandler.post(syncCheckRunnable);

        if (cameraService != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraService.startCameraPreview();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop periodic tasks
        mainHandler.removeCallbacks(speedUpdater);
        syncCheckHandler.removeCallbacks(syncCheckRunnable);

        if (cameraService != null) {
            cameraService.stopCamera();
        }
    }

    private void checkUnsyncedData() {
        boolean hasUnsynced = !attendanceService.getUnsyncedAttendance().isEmpty();
        runOnUiThread(() -> {
            syncBtn.setEnabled(hasUnsynced && !isSyncing);
            syncBtn.setAlpha(hasUnsynced && !isSyncing ? 1.0f : 0.5f);
        });
    }

    private void showAttendanceSyncDialog(boolean isBtn) {
        if (!NetworkUtils.isNetworkConnected(this)) {
            ToastUtils.showErrorToast(this, "Please connect to the internet to sync attendance.", true);
            return;
        }

        isSyncing = true;
        syncBtn.setEnabled(false);
        syncBtn.setAlpha(0.5f);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.RoundedAlertDialog);
        builder.setTitle("Attendance Syncing")
                .setMessage("Please wait until the process is finished...")
                .setCancelable(false);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.rounded_item_background);
        }
        dialog.show();

        attendanceService.syncAttendance(
                () -> { // Success callback
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        isSyncing = false;
                        if(!isBtn){
                            navigateToPin();

                        }
                        checkUnsyncedData();

                    });
                },
                () -> {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        isSyncing = false;

                        ToastUtils.showErrorToast(this, "Failed to sync attendance. Data will be retried later.");


                        checkUnsyncedData(); // Re-check sync status
                    });
                }
        );
    }

    private void navigateToPin() {
        SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String phoneNumber = sharedPreferences.getString("phoneNumber", null);
        Intent intent = new Intent(this, PinActivity.class);
        intent.putExtra("phone_number", phoneNumber);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void updateInternetSpeed() {
        runOnUiThread(() -> {
            double speed = NetworkUtils.getCurrentInternetSpeed(this);
            String speedText;

            if (speed < 1000) {
                speedText = String.format(Locale.getDefault(), "%.0f Kbps", speed);
            } else {
                speedText = String.format(Locale.getDefault(), "%.1f Mbps", speed / 1000);
            }

            internetSpeedTextView.setText(speedText);

            // Visual feedback based on speed
            if (speed < 500) {
                internetSpeedTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            } else if (speed < 2000) {
                internetSpeedTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
            } else {
                internetSpeedTextView.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
            }
        });
    }

    private void requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    cameraService.startCameraPreview();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Camera Permission")
                            .setMessage("The app couldn't function without the camera permission.")
                            .setCancelable(false)
                            .setPositiveButton("ALLOW", (dialog, which) -> {
                                dialog.dismiss();
                                requestCameraPermission();
                            })
                            .setNegativeButton("CLOSE", (dialog, which) -> {
                                dialog.dismiss();
                                finish();
                            })
                            .create()
                            .show();
                }
            }
    );


    @Override
    protected void onDestroy() {
        System.out.println("Closed");
        AttendanceService service=new AttendanceService(this);
        SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String orgIdString = sharedPreferences.getString("orgID", "0");
        String orgName = sharedPreferences.getString("orgName", "0");
        int orgId = (int) Double.parseDouble(orgIdString);

        JSONArray data = service.getAllAttendanceData();
        List<Map<String, Object>> dataList = new ArrayList<>();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        for (int i = 0; i < data.length(); i++) {
            try {
                JSONObject item = data.getJSONObject(i);
                Map<String, Object> map = new HashMap<>();
                Iterator<String> keys = item.keys();

                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = item.get(key);
                    // Check if the key contains "timestamp" or "time" (case insensitive)
                    if (key.toLowerCase().contains("timestamp") || key.toLowerCase().contains("time")) {
                        try {
                            // Handle both long timestamps and string timestamps
                            if (value instanceof Long) {
                                Date date = new Date((Long) value);
                                map.put(key, dateFormat.format(date));
                            } else if (value instanceof String) {
                                map.put(key, value); // or parse and reformat if needed
                            }
                        } catch (Exception e) {
                            map.put(key, value);
                        }
                    } else {
                        map.put(key, value);
                    }
                }
                dataList.add(map);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        // SOLUTION: Wrap the List inside a Map so Firestore can serialize it properly as a document
        Map<String, Object> backupDocument = new HashMap<>();
        backupDocument.put("attendance_records", dataList);

        // Pass the wrapped Map instead of the raw List
        FirebaseLogger.Companion.getInstance().logData(
                orgName+" Backup ORG ID: " + orgId,
                backupDocument
        );

        super.onDestroy();
    }
}