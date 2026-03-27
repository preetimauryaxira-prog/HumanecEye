package com.xira.humanec_eye_app.ui.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.LifecycleOwner;
import com.ml.quaterion.facenetdetection.model.ModelInfo;
import com.xira.humanec_eye_app.ImageConverter;
import com.xira.humanec_eye_app.R;
import com.xira.humanec_eye_app.api.ApiRepository;
import com.xira.humanec_eye_app.model.FaceData;
import com.xira.humanec_eye_app.ui.camera.model.FaceNetModel;
import com.xira.humanec_eye_app.ui.camera.model.Models;
import com.xira.humanec_eye_app.utils.ToastUtils;
import java.io.File;
import java.util.Arrays;

public class RegisterActivity extends AppCompatActivity {
    private PreviewView previewView;
    private RegisterFrameAnalyser frameAnalyser;
    private FaceNetModel faceNetModel;
    private FileAccess fileAccess;
    private CameraService cameraService;
    private String empName, code;
    private ModelInfo modelInfo = Models.Companion.getFACENET();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_register);

        // Get employee details from intent
        empName = getIntent().getStringExtra("empName");
        code = getIntent().getStringExtra("empCode");

        // Initialize views
        previewView = findViewById(R.id.preview_view);
        Button registerButton = findViewById(R.id.register_button);
        ImageButton rotate = findViewById(R.id.rotateCamera);

        // Initialize FaceNetModel
        faceNetModel = new FaceNetModel(this, modelInfo, true, true);

        // Initialize FrameAnalyser
        frameAnalyser = new RegisterFrameAnalyser(this, findViewById(R.id.faceOverlay), faceNetModel);
        // frameAnalyser.setOnFaceDetectedListener(this);
        // Initialize CameraService
        cameraService = new CameraService(this, this, previewView, frameAnalyser);

        // Initialize FileAccess
        fileAccess = new FileAccess(this);

        // Request camera permission if not granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        } else {
            cameraService.startCameraPreview();
        }

        // Rotate camera button click listener
        rotate.setOnClickListener(view -> cameraService.switchCamera());

        // Register button click listener
        registerButton.setOnClickListener(v -> {
            // Capture the current face embedding
            float[] embedding = frameAnalyser.getCurrentFaceEmbedding();
            String image = frameAnalyser.getCurrentFaceImageAsBase64();
            if (embedding != null && !image.isEmpty()) {
                sendRegistrationToApi(image, Arrays.toString(embedding));
                fileAccess.addFace(new FaceData(empName, code, embedding));
            }
        });
    }

//    @Override
//    public void onFaceDetected(Bitmap faceBitmap, float[] embedding) {
//        runOnUiThread(() -> showRegistrationDialog(faceBitmap, embedding));
//    }

//    private void showRegistrationDialog(Bitmap faceBitmap, float[] embedding) {
//        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        View dialogView = getLayoutInflater().inflate(R.layout.dialog_registration, null);
//        builder.setView(dialogView);
//
//        ImageView faceImageView = dialogView.findViewById(R.id.faceImageView);
//        TextView nameTextView = dialogView.findViewById(R.id.employeeNameTextView);
//        TextView codeTextView = dialogView.findViewById(R.id.employeeCodeTextView);
//        ImageButton cancelButton = dialogView.findViewById(R.id.cancelBtn);
//        Button addFaceButton = dialogView.findViewById(R.id.addFaceBtn);
//
//        faceImageView.setImageBitmap(faceBitmap);
//        nameTextView.setText(empName);
//        codeTextView.setText(code);
//
//        AlertDialog dialog = builder.create();
//        dialog.setCancelable(false);
//
//        cancelButton.setOnClickListener(v -> {
//            dialog.dismiss();
//            // Continue camera process
//            cameraService.startCameraPreview();
//        });
//
//        addFaceButton.setOnClickListener(v -> {
//            dialog.dismiss();
//            // Register the face
//            sendRegistrationToApi(frameAnalyser.getCurrentFaceImageAsBase64(), Arrays.toString(embedding));
//            fileAccess.addFace(new FaceData(empName, code, embedding));
//            finish();
//        });
//
//        dialog.show();
//    }

    private void sendRegistrationToApi(String base64Image, String embeddings) {
        File image = ImageConverter.base64ToFile(this, base64Image, "Image.jpeg");

        if (image != null && image.exists()) {
            Log.d("ImageCheck", "Image file created: " + image.getAbsolutePath());
            Log.d("ImageCheck", "File size: " + image.length() + " bytes");
        } else {
            Log.e("ImageCheck", "Image file creation failed!");
            return;
        }

        ApiRepository.getInstance(this).registerEmployee(
                code,
                embeddings,
                image.getAbsolutePath(),
                this
        ).observe((LifecycleOwner) this, response -> {
            if (response) {
                // Success: Close the activity and release resources
                runOnUiThread(() -> {
                    ToastUtils.showSuccessToast(this, "Registration successful!",true);
                    finish();
                });
            } else {
                // Failed: Show error message
                runOnUiThread(() -> {
                    ToastUtils.showErrorToast(this, "Registration failed. Please try again.",true);
                });
            }
        });
    }

    private void requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    // Camera permission launcher
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

}