package com.xira.humanec_eye_app.ui.profile;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.xira.humanec_eye_app.R;
import com.xira.humanec_eye_app.api.ApiRepository;

public class SetPinBottomSheetDialog extends BottomSheetDialogFragment {

    private EditText pinInput, confirmPinInput;
    private Button savePinButton;

    private TextView pinBsTitle;

    private SharedPreferences sharedPreferences;

    private ApiRepository apiRepository;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        apiRepository=ApiRepository.getInstance(getContext());
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(R.drawable.bottom_sheet_background);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.bottom_sheet_set_pin, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sharedPreferences = getContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        pinInput = view.findViewById(R.id.editTextPin);
        confirmPinInput = view.findViewById(R.id.editTextConfirmPin);
        savePinButton = view.findViewById(R.id.buttonSavePin);
        pinBsTitle=view.findViewById(R.id.pinBsTitle);

        boolean isPinSet = sharedPreferences.getBoolean("isSetPin", false);
       pinBsTitle.setText( isPinSet ? "Update pin" : "Set pin");

        savePinButton.setOnClickListener(v -> {
            String pin = pinInput.getText().toString();
            String confirmPin = confirmPinInput.getText().toString();

            if (pin.isEmpty() || confirmPin.isEmpty()) {
                Toast.makeText(getContext(), "Please enter PIN", Toast.LENGTH_SHORT).show();
            } else if (!pin.equals(confirmPin)) {
                Toast.makeText(getContext(), "PINs do not match", Toast.LENGTH_SHORT).show();

            } else {
                setPinUpdate(pin);
                Toast.makeText(getContext(), "PIN Set Successfully!", Toast.LENGTH_SHORT).show();
                dismiss(); // Close BottomSheet
            }
        });
    }

    private void setPinUpdate(String pin) {
        apiRepository.setPin(pin, getContext()).observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean success) {
                if (success) {
                    System.out.println("Pin Set Successfully");
                } else {
                    System.out.println("Something went wrong");
                }
            }
        });
    }

}
