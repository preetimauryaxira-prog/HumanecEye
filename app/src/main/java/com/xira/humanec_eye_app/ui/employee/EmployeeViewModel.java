package com.xira.humanec_eye_app.ui.employee;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.xira.humanec_eye_app.api.ApiRepository;
import com.xira.humanec_eye_app.model.Employee;

import java.util.ArrayList;
import java.util.List;

public class EmployeeViewModel extends ViewModel {
    // Separate LiveData for each list
    private final MutableLiveData<List<Employee>> registeredEmployees = new MutableLiveData<>();
    private final MutableLiveData<List<Employee>> unregisteredEmployees = new MutableLiveData<>();

    // LiveData for counts
    private final MutableLiveData<Integer> registeredCount = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> unregisteredCount = new MutableLiveData<>(0);

    // Track loading states
    private final MutableLiveData<Boolean> isLoadingRegistered = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoadingUnregistered = new MutableLiveData<>(false);

    public LiveData<List<Employee>> getRegisteredEmployees() {
        return registeredEmployees;
    }

    public LiveData<List<Employee>> getUnregisteredEmployees() {
        return unregisteredEmployees;
    }

    public LiveData<Integer> getRegisteredCount() {
        return registeredCount;
    }

    public LiveData<Integer> getUnregisteredCount() {
        return unregisteredCount;
    }

    public LiveData<Boolean> getIsLoadingRegistered() {
        return isLoadingRegistered;
    }

    public LiveData<Boolean> getIsLoadingUnregistered() {
        return isLoadingUnregistered;
    }

    // Initialize both lists when ViewModel is created
    public void initData(Context context) {
        fetchRegisteredEmployees("", context);
        fetchUnregisteredEmployees("", context);
    }

    public void fetchRegisteredEmployees(String searchQuery, Context context) {
        isLoadingRegistered.setValue(true);

        SharedPreferences sharedPreferences = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String token = sharedPreferences.getString("access_token", null);

        if (token == null) {
            Log.e("TOKEN_ERROR", "Access token is null!");
            registeredEmployees.setValue(new ArrayList<>());
            registeredCount.setValue(0);
            isLoadingRegistered.setValue(false);
            return;
        }

        Log.d("API_CALL", "Fetching Registered Employee Data...");

        ApiRepository.getInstance(context).getRegisteredEmployees(searchQuery, context)
                .observeForever(employeeData -> {
                    isLoadingRegistered.setValue(false);

                    if (employeeData != null) {
                        Log.d("API_CALL", "Registered employee data received: " + employeeData.size() + " employees");
                        registeredEmployees.setValue(employeeData);
                        registeredCount.setValue(employeeData.size());
                    } else {
                        Log.e("API_ERROR", "Failed to fetch registered employees");
                        registeredEmployees.setValue(new ArrayList<>());
                        registeredCount.setValue(0);
                    }
                });
    }

    public void fetchUnregisteredEmployees(String searchQuery, Context context) {
        isLoadingUnregistered.setValue(true);

        SharedPreferences sharedPreferences = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String token = sharedPreferences.getString("access_token", null);

        if (token == null) {
            Log.e("TOKEN_ERROR", "Access token is null!");
            unregisteredEmployees.setValue(new ArrayList<>());
            unregisteredCount.setValue(0);
            isLoadingUnregistered.setValue(false);
            return;
        }

        Log.d("API_CALL", "Fetching Unregistered Employee Data...");

        ApiRepository.getInstance(context).getUnregisteredEmployees(searchQuery, context)
                .observeForever(employeeData -> {
                    isLoadingUnregistered.setValue(false);

                    if (employeeData != null) {
                        Log.d("API_CALL", "Unregistered employee data received: " + employeeData.size() + " employees");
                        unregisteredEmployees.setValue(employeeData);
                        unregisteredCount.setValue(employeeData.size());
                    } else {
                        Log.e("API_ERROR", "Failed to fetch unregistered employees");
                        unregisteredEmployees.setValue(new ArrayList<>());
                        unregisteredCount.setValue(0);
                    }
                });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // Clear references to prevent memory leaks
        registeredEmployees.setValue(null);
        unregisteredEmployees.setValue(null);
    }
}