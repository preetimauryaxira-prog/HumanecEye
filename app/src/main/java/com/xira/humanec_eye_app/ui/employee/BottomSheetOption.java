package com.xira.humanec_eye_app.ui.employee;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.xira.humanec_eye_app.ui.camera.FileAccess;
import com.xira.humanec_eye_app.R;
import com.xira.humanec_eye_app.api.ApiRepository;
import com.xira.humanec_eye_app.model.Business;
import com.xira.humanec_eye_app.ui.auth.FetchingDataActivity;

import java.io.File;

public class BottomSheetOption extends BottomSheetDialogFragment {

    private RadioGroup radioGroupBusiness;
    private ApiRepository apiRepository;
    private Button btnContinue;
    private String selectedBusiness = null;
    private String selectedCompanyName = null;
    private FileAccess fileAccess;
    private EmployeeFragment employeeFragment; // Reference to EmployeeFragment

    public void setEmployeeFragment(EmployeeFragment employeeFragment) {
        this.employeeFragment = employeeFragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        fileAccess=new FileAccess(getContext());

        dialog.setOnShowListener(dialogInterface -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(R.drawable.bottom_sheet_background);
                // Set height to 90% of the screen
                setBottomSheetHeight(bottomSheet);
                // Set the bottom sheet state to fully expanded
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED); // Open in fully expanded state
            }
        });
        return dialog;
    }
    private void setBottomSheetHeight(View bottomSheet) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;
        int targetHeight = (int) (screenHeight * 0.9); // 90% of screen height

        ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
        params.height = targetHeight;
        bottomSheet.setLayoutParams(params);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_options, container, false);

        // Initialize views
        radioGroupBusiness = view.findViewById(R.id.radioGroupBusiness);
        btnContinue = view.findViewById(R.id.btnSubmit);

        // Initialize ApiRepository
        apiRepository = ApiRepository.getInstance(requireContext());

        // Fetch business data
        fetchBusinessData();

        // Set up radio group listener
        radioGroupBusiness.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selectedRadioButton = view.findViewById(checkedId);
            selectedBusiness = selectedRadioButton.getTag().toString();
            selectedCompanyName=selectedRadioButton.getText().toString();
            btnContinue.setEnabled(true);
        });

        // Set up continue button click listener
        btnContinue.setOnClickListener(v -> {
            if (selectedBusiness == null || selectedBusiness.isEmpty()) {
                Toast.makeText(requireContext(), "Please select a business", Toast.LENGTH_SHORT).show();
                return;
            }
deleteFaceEmbeddingFile();
            // Switch business
            apiRepository.switchBusiness(selectedBusiness, requireContext()).observe(getViewLifecycleOwner(), success -> {
                if (success) {
                    // Notify the EmployeeFragment to refresh
                    if (employeeFragment != null) {
                        employeeFragment.refreshEmployeeList();
                        employeeFragment.updateCompanyName(selectedCompanyName);

                    }
                    // Close the bottom sheet
                    dismiss();
                    Intent intent = new Intent(getContext(), FetchingDataActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } else {
                    Toast.makeText(requireContext(), "Failed to switch business", Toast.LENGTH_SHORT).show();
                }
            });
        });



        return view;
    }
    private void deleteFaceEmbeddingFile() {
        // List of files to delete
        fileAccess.clearAllFaces();

        String[] filesToDelete = {"face_embeddings.json", "attendance_records.json"};

        for (String fileName : filesToDelete) {
            File file = new File(getContext().getFilesDir(), fileName);

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
    private void fetchBusinessData() {
        apiRepository.getAllBusiness(requireContext()).observe(getViewLifecycleOwner(), businessList -> {
            if (businessList != null && !businessList.isEmpty()) {
                for (Business business : businessList) {
                    RadioButton radioButton = new RadioButton(requireContext());
                    radioButton.setText(business.getORGName());
                    radioButton.setTextSize(18);
                    radioButton.setId(View.generateViewId());
                    radioButton.setTag(business.getId());
                    radioButton.setPadding(30, 10, 0, 16);
                    radioGroupBusiness.addView(radioButton);
                }
            } else {
                Toast.makeText(requireContext(), "No businesses found!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}