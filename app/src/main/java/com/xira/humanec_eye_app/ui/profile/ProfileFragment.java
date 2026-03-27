package com.xira.humanec_eye_app.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.xira.humanec_eye_app.ui.camera.FileAccess;
import com.xira.humanec_eye_app.R;
import com.xira.humanec_eye_app.ui.auth.LoginActivity;

import java.io.File;


public class ProfileFragment extends Fragment {

    private SharedPreferences sharedPreferences;

    private ListView profileOptionsList;

    private FileAccess fileAccess;
    private String[] profileItems = {

            "Terms & Conditions",
            "Privacy Policy",
            "Delete Account",
            "Set PIN",
            "Logout"
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fileAccess=new FileAccess(getContext());
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
         sharedPreferences = getContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);

        // Set user details
        TextView tvUserName = view.findViewById(R.id.tvUserName);
        TextView tvUserEmail = view.findViewById(R.id.tvUserEmail);
        TextView tvCompany=view.findViewById(R.id.tvCompany);


        tvUserName.setText(sharedPreferences.getString("name",""));
        tvUserEmail.setText(sharedPreferences.getString("email",""));
        tvCompany.setText(sharedPreferences.getString("orgName",""));

        // Setup ListView
        profileOptionsList = view.findViewById(R.id.profileOptionsList);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, profileItems);
        profileOptionsList.setAdapter(adapter);
        boolean isPinSet = sharedPreferences.getBoolean("isSetPin", false);
        profileItems[3] = isPinSet ? "Update Current PIN" : "Set PIN";
        // Handle Item Clicks
        profileOptionsList.setOnItemClickListener((parent, view1, position, id) -> handleItemClick(position));

        return view;
    }

    private void handleItemClick(int position) {
        switch (position) {


            case 0:
                openWebPage("https://humanec.ai/terms-and-condition.html");
                break;
            case 1:
                openWebPage("https://humanec.ai/privacy-policy.html");
                break;
            case 2:
                showDeleteAccountDialog();
                break;
            case 3:
                showSetPinDialog();
                break;
            case 4:
                clearUserData();
                break;
        }
    }
    private void redirectToLogin() {
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // Clear back stack
        startActivity(intent);
    }


    private void openWebPage(String url) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(browserIntent);
    }
    private void clearUserData() {

        SharedPreferences sharedPreferences = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
        deleteFaceEmbeddingFile();
        redirectToLogin();
    }
    private void deleteFaceEmbeddingFile() {
        fileAccess.clearAllFaces();
        // List of files to delete
        String[] filesToDelete = {"attendance_records.json"};

        for (String fileName : filesToDelete) {
            File file = new File(requireContext().getFilesDir(), fileName);

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


    private void showDeleteAccountDialog() {
        Context context = getContext();
        if (context == null) return;

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.RoundedAlertDialog)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Delete", (dialogInterface, which) -> {

                    clearUserData();
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.GRAY);
        });

        dialog.show();
    }

    private void showSetPinDialog() {
        SetPinBottomSheetDialog bottomSheet = new SetPinBottomSheetDialog();
        bottomSheet.show(getParentFragmentManager(), "SetPinDialog");
    }




}

