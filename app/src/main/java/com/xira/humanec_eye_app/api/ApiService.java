package com.xira.humanec_eye_app.api;
import com.xira.humanec_eye_app.model.Business;
import com.xira.humanec_eye_app.model.Employee;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.PartMap;

public interface ApiService {
    @Multipart
    @PUT("Employee")
    Call<ResponseBody> deleteEmployee(
            @Header("Authorization") String token,
            @PartMap Map<String, RequestBody> params
    );

    @POST("/xiraeye/api/Authorization/api/Authorization/UserDetail")
    Call<List<Map<String, Object>>> getUserDetail(@Body Map<String,String> body);

    @GET("Business")
    Call<List<Business>> getAllBusiness(@Header("Authorization") String token);

    @POST("Employee/Records")
    Call<List<Employee>> getUnregisterEmployee(@Header("Authorization") String token, @Body Map<String, Object> params);

    @POST("Attendance/EmpRecords")
    Call<List<Employee>> getRegisteredEmployees(@Header("Authorization") String token, @Body Map<String, Object> params);


    @Multipart
    @PUT("Employee")
    Call<ResponseBody> registerEmployee(
            @Header("Authorization") String token,
            @PartMap Map<String, RequestBody> params,
            @Part MultipartBody.Part image1

    );

    @POST("Attendance")
    Call<ResponseBody> bulkAttendance(@Header("Authorization")String token,@Body RequestBody params);
    @POST("Authorization/api/Authorization/SendLoginOTP")
    Call<List<Map<String, Object>>> sendWithOTP(@Body Map<String,String> body);

    @POST("Authorization/api/Authorization/LoginWithOTP")
    Call<Map<String, Object>> loginWithOTP(@Body Map<String, String> body);

    @POST("Business/SwitchBusiness")
    Call<Map<String, Object>> switchBusiness(
            @Header("Authorization") String token, @Body Map<String, String> body);


    @POST("Authorization/api/Authorization/SetPin")
    Call<Boolean> setPin(@Header("Authorization") String token,@Body Map<String, String> body);
}
