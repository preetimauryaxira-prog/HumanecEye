package com.xira.humanec_eye_app.api;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.xira.humanec_eye_app.FirebaseLogger;
import com.xira.humanec_eye_app.model.Business;
import com.xira.humanec_eye_app.model.Employee;
import com.xira.humanec_eye_app.utils.ToastUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class ApiRepository {
    private static   ApiRepository instance;
    private final ApiService apiService;

    private ApiRepository(Context context) {
        apiService = ApiClient.getApiService(context);
    }

    public static ApiRepository getInstance(Context context) {

        if (instance == null) {
            instance = new ApiRepository(context);
        }

        return instance;
    }

    public String getToken(Context context){
        SharedPreferences sharedPreferences = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        return sharedPreferences.getString("access_token", null);
    }

    public MutableLiveData<Boolean> getUserDetail(String username){

        MutableLiveData<Boolean> alreadySet = new MutableLiveData<>();

        apiService.getUserDetail(Map.of("username", username)).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(@NonNull Call<List<Map<String, Object>>> call, @NonNull Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {

                        JSONArray jsonArray = new JSONArray(response.body());

                        // Check if array is not empty
                        if ( jsonArray.length() > 0 ) {
                            JSONObject firstObject = jsonArray.getJSONObject(0);
                            int pinValue = firstObject.getInt("PIN");

                            if (pinValue == 1) {
                                // PIN is 1, do something (return true)
                                Log.d("API_RESPONSE", "PIN is 1: Access Granted");
                                alreadySet.setValue(true);
                            } else {
                                // PIN is not 1, do something else (return false)
                                Log.d("API_RESPONSE", "PIN is not 1: Access Denied");
                                alreadySet.setValue(false);
                            }
                        } else {
                            Log.e("API_RESPONSE", "Empty JSON array received");
                            alreadySet.setValue(false);
                        }
                    } catch (Exception e) {
                        Log.e("API_RESPONSE", "Error parsing JSON", e);
                        alreadySet.setValue(false);
                    }
                } else {
                    Log.e("API_ERROR", "Response unsuccessful. Code: " + response.code() + ", Message: " + response.message());
                    alreadySet.setValue(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Map<String, Object>>> call, @NonNull Throwable t) {
                Log.e("API_FAILURE", "Error: " + t.getMessage(), t);
                alreadySet.setValue(false);
            }
        });

        return alreadySet;
    }

    public MutableLiveData<Boolean> sendWithOTP(String phoneNumber,Context context) {
        MutableLiveData<Boolean> otpResult = new MutableLiveData<>();

        apiService.sendWithOTP(Map.of("username", phoneNumber)).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(@NonNull Call<List<Map<String, Object>>> call, @NonNull Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {

                        JSONArray jsonArray = new JSONArray(response.body());

                        // Check if array is not empty
                        if ( jsonArray.length() > 0 ) {
                            JSONObject firstObject = jsonArray.getJSONObject(0);
                            int isActive = firstObject.getInt("IS_ACTIVE");

                            if (isActive == 1) {
                                // PIN is 1, do something (return true)
                                Log.d("API_RESPONSE", "PIN is 1: Access Granted");
                                otpResult.setValue(true);
                            } else {
                                ToastUtils.showErrorToast(context,"You Don't Have Access For Login");
                                otpResult.setValue(false);
                            }
                        } else {
                            Log.e("API_RESPONSE", "Empty JSON array received");

                            otpResult.setValue(false);
                        }
                    } catch (Exception e) {
                        Log.e("API_RESPONSE", "Error parsing JSON", e);

                        otpResult.setValue(false);
                    }
                } else {
                    Log.e("API_ERROR", "Response unsuccessful. Code: " + response.code() + ", Message: " + response.message());
                    ToastUtils.showErrorToast(context, "Response"+response.body());
                    otpResult.setValue(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Map<String, Object>>> call, @NonNull Throwable t) {
                ToastUtils.showErrorToast(context, "Error: " + t.getMessage());
                Log.e("API_FAILURE", "Error: " + t.getMessage(), t);
                otpResult.setValue(false);
            }
        });

        return otpResult;
    }

    public MutableLiveData<List<Business>> getAllBusiness(Context context) {
        MutableLiveData<List<Business>> businessData = new MutableLiveData<>();
        String token=getToken(context);
        if(token==null){
            return businessData;
        }
     

        apiService.getAllBusiness("Bearer " + token).enqueue(new Callback<List<Business>>() {
            @Override
            public void onResponse(@NonNull Call<List<Business>> call, @NonNull Response<List<Business>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d("RESPONSE", new Gson().toJson(response.body())); // JSON logging
                    businessData.postValue(new ArrayList<>(response.body())); // Force LiveData update
                } else {
                    Log.e("API_ERROR", "Error: " + response.code() + " " + response.message());
                    businessData.setValue(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Business>> call, @NonNull Throwable t) {
                Log.e("API_ERROR", "Failed to fetch business: " + t.getMessage());
                businessData.setValue(null);
            }
        });

        return businessData;
    }

    public MutableLiveData<Boolean> loginWithOTP(Map<String, String> body, Context context) {
        final MutableLiveData<Boolean> loginWithResult = new MutableLiveData<>();

        apiService.loginWithOTP(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> responseData = response.body();

                    if (responseData.containsKey("statusCode") && responseData.get("statusCode") instanceof Number) {
                        int statusCode = ((Number) Objects.requireNonNull(responseData.get("statusCode"))).intValue();
                        String message = (String) responseData.get("message");

                        Log.d("package:mine loginWithOTP", responseData.toString());

                        if (statusCode == 1) {
                            // Extract required data
                            String accessToken = (String) responseData.get("access_token");
                            String tokenExpiration = (String) responseData.get("token_expiration");
                            String userName = (String) responseData.get("userName");
                            String name = (String) responseData.get("name");
                            String empCode = (String) responseData.get("emP_CODE");
                            String phoneNumber = (String) responseData.get("phoneNumber");
                            boolean isSuperAdmin = (boolean) responseData.get("isSuperAdmin");
                            boolean isAdmin = (boolean) responseData.get("isAdmin");
                            boolean isHrHead = (boolean) responseData.get("isHrHead");

                            if(!isAdmin){
                               ToastUtils.showErrorToast(context,"Humanec Eye Login For Owners. You can't Access it.");
                               loginWithResult.setValue(false);
                               return;
                            }

                            SharedPreferences sharedPreferences = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("access_token", accessToken);
                            editor.putString("token_expiration", tokenExpiration);
                            editor.putString("userName", userName);
                            editor.putString("name", name);
                            editor.putString("empCode", empCode);
                            editor.putString("phoneNumber", phoneNumber);
                            editor.putBoolean("isSuperAdmin", isSuperAdmin);
                            editor.putBoolean("isAdmin", true);
                            editor.putBoolean("isHrHead", isHrHead);
                            editor.apply(); // Asynchronous commit

                            // Post success
                            loginWithResult.postValue(true);
                        } else {
                            ToastUtils.showErrorToast(context, "You Entered Incorrect OTP!");
                            loginWithResult.postValue(false);
                        }
                    } else {
                        ToastUtils.showErrorToast(context, "You Entered Incorrect OTP!");
                        loginWithResult.postValue(false);
                    }
                } else {
                    ToastUtils.showErrorToast(context,"Login Failed: Invalid response from server.");
                    loginWithResult.postValue(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                ToastUtils.showErrorToast(context,("Error: " + t.getMessage()));
                loginWithResult.postValue(false);
            }
        });

        return loginWithResult;
    }

    public MutableLiveData<Boolean> switchBusiness(String id, Context context) {
        final MutableLiveData<Boolean> loginWithResult = new MutableLiveData<>();
        String token=getToken(context);
        if(token==null){
            return loginWithResult;
        }
        apiService.switchBusiness("Bearer " + token, Map.of("id", id)).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                Log.d("Request", call.request().toString());
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> responseData = response.body();

                    if (responseData.containsKey("statusCode") && responseData.get("statusCode") instanceof Number) {
                        int statusCode = ((Number) Objects.requireNonNull(responseData.get("statusCode"))).intValue();
                        String message = (String) responseData.get("message");

                        Log.d("Switch Business", responseData.toString());

                        if (statusCode == 1) {
                            // Extract required data
                            String accessToken = (String) responseData.get("access_token");
                            String tokenExpiration = (String) responseData.get("token_expiration");
                            String userName = (String) responseData.get("userName");
                            String name = (String) responseData.get("name");
                            String email=(String) responseData.get("email");
                            boolean isSuperAdmin = (boolean) responseData.get("isSuperAdmin");
                            double orgID = (double) responseData.get("orgID");
                            String orgName=(String)responseData.get("orgName");
                            SharedPreferences sharedPreferences = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("access_token", accessToken);
                            editor.putString("token_expiration", tokenExpiration);
                            editor.putString("userName", userName);
                            editor.putString("name", name);
                            editor.putString("email",email);
                            editor.putBoolean("isSuperAdmin", isSuperAdmin);
                            editor.putString("orgID", String.valueOf(orgID));
                            editor.putString("orgName",String.valueOf(orgName));

                            editor.apply();

                            loginWithResult.postValue(true);
                        } else {
                            System.out.println("Login Failed: " + message);
                            loginWithResult.postValue(false);
                        }
                    } else {
                        System.out.println("Login Failed: Missing or invalid statusCode.");
                        loginWithResult.postValue(false);
                    }
                } else {
                    System.out.println("Login Failed: Invalid response from server.");
                    loginWithResult.postValue(false);
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                System.out.println("Error: " + t.getMessage());
                loginWithResult.postValue(false);
            }
        });

        return loginWithResult;
    }

    public MutableLiveData<List<Employee>> getRegisteredEmployees(String searchText, Context context) {

        final MutableLiveData<List<Employee>> employees = new MutableLiveData<>();
        String token=getToken(context);
        if(token==null){
            return employees;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("pageNo", 1);
        params.put("pageSize", 3000);
        params.put("searchText", searchText);
        params.put("orderBy", "");

        apiService.getRegisteredEmployees("Bearer " + token, params).enqueue(new Callback<List<Employee>>() {
            @Override
            public void onResponse(@NonNull Call<List<Employee>> call, @NonNull Response<List<Employee>> response) {
                Log.d("API_CALL", "Request" + call.request().toString());

                if (response.isSuccessful() && response.body() != null) {

                    Log.d("RESPONSE", new Gson().toJson(response.body())); // JSON logging
                    employees.postValue(new ArrayList<>(response.body())); // Force LiveData update
                } else {
                    Log.e("API_ERROR", "Error: " + response.code() + " " + response.message());
                    employees.setValue(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Employee>> call, @NonNull Throwable t) {
                Log.e("API_ERROR", "Failed to fetch business: " + t.getMessage());
                employees.setValue(null);
            }

        });
        return employees;
    }

    public MutableLiveData<List<Employee>> getUnregisteredEmployees(String searchQuery, Context context) {

        final MutableLiveData<List<Employee>> employees = new MutableLiveData<>();
        String token=getToken(context);
        if(token==null){
            return employees;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("pageNo", 1);
        params.put("pageSize", 3000);
        params.put("searchText", searchQuery);
        params.put("orderBy", "");

        apiService.getUnregisterEmployee("Bearer " + token, params).enqueue(new Callback<List<Employee>>() {
            @Override
            public void onResponse(@NonNull Call<List<Employee>> call, @NonNull Response<List<Employee>> response) {
                Log.d("API_CALL", "Request" + call.request().toString());

                if (response.isSuccessful() && response.body() != null) {

                    Log.d("RESPONSE", new Gson().toJson(response.body())); // JSON logging
                    employees.postValue(new ArrayList<>(response.body())); // Force LiveData update
                } else {
                    Log.e("API_ERROR", "Error: " + response.code() + " " + response.message());
                    employees.setValue(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Employee>> call, @NonNull Throwable t) {
                Log.e("API_ERROR", "Failed to fetch business: " + t.getMessage());
                employees.setValue(null);
            }

        });
        return employees;
    }
    public MutableLiveData<Boolean> setPin(String pin, Context context) {
        final MutableLiveData<Boolean> result = new MutableLiveData<>(false);
        String token=getToken(context);
        if(token==null){
            return result;
        }
        apiService.setPin("Bearer " + token, Map.of("pin", pin)).enqueue(new Callback<Boolean>() {
            @Override
            public void onResponse(@NonNull Call<Boolean> call, @NonNull Response<Boolean> response) {
                Log.d("API_CALL", "Request" + call.request().toString());

                if (response.isSuccessful() && response.body() != null) {

                    result.postValue(true);
                } else {
                    Log.e("API_ERROR", "Error: " + response.code() + " " + response.message());
                    result.postValue(false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<Boolean> call, @NonNull Throwable t) {
                Log.e("API_ERROR", "Failed to fetch business: " + t.getMessage());
                result.postValue(false);
            }


        });
        return result;
    }
    public LiveData<Boolean> deleteEmployee(String empCode, Context context) {
        MutableLiveData<Boolean> result = new MutableLiveData<>();

        String token=getToken(context);
        if(token==null){
            return result;
        }
        try {
            // Create parameter map
            Map<String, RequestBody> params = new HashMap<>();
            params.put("CODE", RequestBody.create(MediaType.parse("text/plain"), empCode));
            params.put("ATTN_FLAG", RequestBody.create(MediaType.parse("text/plain"), "false"));

            // Log request details
            Log.d("API_REQUEST", "📤 Sending delete request for employee: " + empCode);
            Log.d("API_REQUEST", "Token: Bearer " + token);

            // Call the API
            apiService.deleteEmployee("Bearer " + token, params).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            String responseData = response.body().string();
                            Log.d("API_RESPONSE", "✅ Response: " + responseData);
                            result.setValue(true);
                        } else {
                            String errorBody = null;
                            try {
                                if (response.errorBody() != null) {
                                    errorBody = response.errorBody().string();
                                }
                            } catch (Exception e) {
                                Log.e("API_ERROR", "Failed to read error body", e);
                            }

                            Log.e("API_ERROR", String.format("❌ Delete failed! Code: %d, Error: %s",
                                    response.code(), errorBody != null ? errorBody : response.message()));
                            result.setValue(false);
                        }
                    } catch (Exception e) {
                        Log.e("API_ERROR", "❌ Error parsing response", e);
                      result.setValue(false);
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e("API_ERROR", "❌ API call failed: " + t.getMessage(), t);
                    result.setValue(false);
                }
            });
        } catch (Exception e) {
            Log.e("API_ERROR", "❌ Error preparing delete request: " + e.getMessage(), e);
            result.setValue(false);
        }

        return result;
    }
//    public MutableLiveData<Boolean> bulkAttendanceMark(List<Map<String,Object>> data, Context context) {
//        // Initialize with null to represent "loading" state
//        MutableLiveData<Boolean> result = new MutableLiveData<>();
//
//        String token = getToken(context);
//        if (token == null) {
//            result.setValue(false); // No token available
//            return result;
//        }
//
//        try {
//            String jsonParams = new Gson().toJson(data);
//            RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), jsonParams);
//
//            apiService.bulkAttendance("Bearer " + token, requestBody).enqueue(new Callback<ResponseBody>() {
//                @Override
//                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
//                    System.out.println("Repsonse" +response.body());
//
////                    FirebaseLogger.Companion.getInstance().logMessageWithTimestamp("Token--"+token, );
//                    if(response.code()==201){
//                        result.setValue(true);
//                    }else{
//                        ToastUtils.showErrorToast(context,"Something went Wrong");
//                        result.setValue(false);
//                    }
//                }
//                public MutableLiveData<Boolean> bulkAttendanceMark(List<Map<String,Object>> data, Context context) {
//                    // Initialize with null to represent "loading" state
//                    MutableLiveData<Boolean> result = new MutableLiveData<>();
//
//                    String token = getToken(context);
//                    if (token == null) {
//                        result.setValue(false); // No token available
//                        return result;
//                    }
//
//                    try {
//                        String jsonParams = new Gson().toJson(data);
//                        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), jsonParams);
//
//                        apiService.bulkAttendance("Bearer " + token, requestBody).enqueue(new Callback<ResponseBody>() {
//                            @Override
//                            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
//                                System.out.println("Repsonse" +response.body());
//
////                    FirebaseLogger.Companion.getInstance().logMessageWithTimestamp("Token--"+token, );
//                                if(response.code()==201){
//                                    result.setValue(true);
//                                }else{
//                                    ToastUtils.showErrorToast(context,"Something went Wrong");
//                                    result.setValue(false);
//                                }
//                            }
//
//                            @Override
//                            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
//                                Log.d("AttendanceService","Error"+t.getMessage());
//                                FirebaseLogger.Companion.getInstance().logMessageWithTimestamp("API_EXCEPTION", "❌ Exception BulkApprove: " + t.getMessage());
//                                ToastUtils.showErrorToast(context,"Error"+t.getMessage());
//                                result.setValue(false);  // Fix: Notify observer of failure
//                            }
//                        });
//                    } catch (Exception e) {
//                        Log.e("API_EXCEPTION", "❌ Exception: " + e.getMessage());
//                        result.setValue(false);
//                    }
//
//                    return result;
//                }
//                @Override
//                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
//                    Log.d("AttendanceService","Error"+t.getMessage());
//                    FirebaseLogger.Companion.getInstance().logMessageWithTimestamp("API_EXCEPTION", "❌ Exception BulkApprove: " + t.getMessage());
//                    ToastUtils.showErrorToast(context,"Error"+t.getMessage());
//                    result.setValue(false);  // Fix: Notify observer of failure
//                }
//            });
//        } catch (Exception e) {
//            Log.e("API_EXCEPTION", "❌ Exception: " + e.getMessage());
//           result.setValue(false);
//        }
//
//        return result;
//    }

    public MutableLiveData<Boolean> bulkAttendanceMark(List<Map<String,Object>> data, Context context) {

        MutableLiveData<Boolean> result = new MutableLiveData<>();

        String token = getToken(context);
        if (token == null) {
            result.setValue(false);
            return result;
        }

        try {

            String jsonParams = new Gson().toJson(data);

            // 🔥 REQUEST LOG
//            saveLogToFile(context, "REQUEST:\n" + jsonParams);
//            copyLogToDownloads(context);

            RequestBody requestBody = RequestBody.create(
                    MediaType.parse("application/json"),
                    jsonParams
            );

            apiService.bulkAttendance("Bearer " + token, requestBody)
                    .enqueue(new Callback<ResponseBody>() {

                        @Override
                        public void onResponse(@NonNull Call<ResponseBody> call,
                                               @NonNull Response<ResponseBody> response) {

                            try {

                                String responseText = "";

                                if (response.body() != null) {
                                    responseText = response.body().string();
                                } else if (response.errorBody() != null) {
                                    responseText = response.errorBody().string();
                                }

                                // 🔥 RESPONSE LOG
//                                saveLogToFile(context,
//                                        "RESPONSE CODE: " + response.code() +
//                                                "\nRESPONSE BODY:\n" + responseText);
//                                copyLogToDownloads(context);

                                if (response.code() == 201) {
                                    result.setValue(true);

                                } else {
                                    ToastUtils.showErrorToast(context,"Something went Wrong");
                                    result.setValue(false);
                                }

                            } catch (Exception e) {
//                                saveLogToFile(context, "RESPONSE PARSE ERROR: " + e.getMessage());
//                                copyLogToDownloads(context);
                                result.setValue(false);
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ResponseBody> call,
                                              @NonNull Throwable t) {

                            // 🔥 FAILURE LOG
//                            saveLogToFile(context,
//                                    "API FAILURE:\n" + t.getMessage());
//
//                            copyLogToDownloads(context);

                            Log.d("AttendanceService","Error"+t.getMessage());

                            result.setValue(false);
                        }
                    });

        } catch (Exception e) {

//            saveLogToFile(context,
//                    "EXCEPTION OUTSIDE:\n" + e.getMessage());
//
//            copyLogToDownloads(context);

            result.setValue(false);
        }

        return result;
    }



    private void saveLogToFile(Context context, String message) {
        try {
            File file = new File(context.getExternalFilesDir(null), "sync_log.txt");
            FileWriter writer = new FileWriter(file, true);

            writer.append("\n============================\n");
            writer.append(new Date().toString() + "\n");
            writer.append(message);
            writer.append("\n");

            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void copyLogToDownloads(Context context) {

        try {

            File sourceFile = new File(
                    context.getExternalFilesDir(null),
                    "sync_log.txt"
            );

            if (!sourceFile.exists()) {
//                Toast.makeText(context, "Log file not found", Toast.LENGTH_LONG).show();
                Log.d("Log file not found","");
                return;
            }

            File destFile = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS),
                    "sync_log.txt"
            );

            FileInputStream in = new FileInputStream(sourceFile);
            FileOutputStream out = new FileOutputStream(destFile);

            byte[] buffer = new byte[1024];
            int length;

            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            in.close();
            out.close();

//            Toast.makeText(context, "Log copied to Downloads folder", Toast.LENGTH_LONG).show();
            Log.d("Log copied to Downloads folder","");

        } catch (Exception e) {
            e.printStackTrace();
//            Toast.makeText(context, "Error copying log: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.d("Error copying log: " + e.getMessage(),"");
        }
    }


    public LiveData<Boolean> registerEmployee(String empCode, String embedding, String imgPath, Context context) {
        MutableLiveData<Boolean> result = new MutableLiveData<>();

        String token = getToken(context);
        if (token == null) {
            Log.e("API_ERROR", "❌ Token is null");
            result.setValue(false);
            return result;
        }

        try {
            // Log file paths
            Log.d("FILE_PATHS", "Original image path: " + imgPath);
            File file1 = new File(imgPath);

            if (!file1.exists()) {
                Log.e("FILE_PATHS", "❌ File does not exist!");
                result.setValue(false);
                return result;
            }

            Log.d("FILE_PATHS", "File exists: " + file1.getAbsolutePath());
            Log.d("FILE_PATHS", "File size: " + file1.length() + " bytes");

            RequestBody requestFile1 = RequestBody.create(MediaType.parse("image/*"), file1);
            MultipartBody.Part imagePart1 = MultipartBody.Part.createFormData("IMG_ATTN", file1.getName(), requestFile1);

            Map<String, RequestBody> params = new HashMap<>();
            params.put("CODE", RequestBody.create(MediaType.parse("text/plain"), empCode));
            params.put("ATTN_FLAG", RequestBody.create(MediaType.parse("text/plain"), "true"));
            params.put("IMG_Base64", RequestBody.create(MediaType.parse("text/plain"), embedding));

            // Log request details
            Log.d("API_REQUEST", "📤 Sending registration request for employee: " + empCode);
            Log.d("API_REQUEST", "Image file: " + file1.getName());
            Log.d("API_REQUEST", "Image size: " + file1.length() + " bytes");

            // Call the API with authorization header
            Call<ResponseBody> call = apiService.registerEmployee("Bearer " + token, params, imagePart1);
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            String responseData = response.body().string();
                            Log.d("API_RESPONSE", "✅ Response: " + responseData);
                            result.setValue(true);
                        } else {
                            String errorBody = null;
                            try {
                                if (response.errorBody() != null) {
                                    errorBody = response.errorBody().string();
                                }
                            } catch (Exception e) {
                                Log.e("API_ERROR", "Failed to read error body", e);
                            }

                            Log.e("API_ERROR", String.format("❌ Registration failed! Code: %d, Error: %s",
                                    response.code(), errorBody != null ? errorBody : response.message()));
                            result.setValue(false);
                        }
                    } catch (Exception e) {
                        Log.e("API_ERROR", "❌ Error parsing response", e);
                        result.setValue(false);
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e("API_ERROR", "❌ API call failed: " + t.getMessage(), t);
                    result.setValue(false);
                }
            });
        } catch (Exception e) {

           Log.e("API_ERROR", "❌ Error preparing API request: " + e.getMessage(), e);
            result.setValue(false);
        }

        return result;
    }

}