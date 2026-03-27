package com.xira.humanec_eye_app.ui.camera.autoSync;

import static android.content.Context.POWER_SERVICE;

import android.app.Notification;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.xira.humanec_eye_app.R;
import com.xira.humanec_eye_app.utils.NetworkUtils;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";
    private static final long SYNC_INTERVAL = 2 * 60 * 1000; // 2 minutes in milliseconds

    private final AttendanceService attendanceService;
    private final Context context;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        this.attendanceService = new AttendanceService(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "SyncWorker started");
        //setForegroundAsync(createForegroundInfo());
        try {
            if (checkBatteryOptimization()) {
                Log.d(TAG, "Battery optimization enabled, delaying sync");
                scheduleNextSync();
                return Result.success();
            }

            if (NetworkUtils.isNetworkConnected(context)) {
                Log.d(TAG, "Internet available, attempting sync");
                boolean syncResult = syncData();

                if (syncResult) {
                    Log.d(TAG, "Sync completed successfully");
                } else {
                    Log.d(TAG, "Sync failed");
                }
            } else {
                Log.d(TAG, "No internet connection - skipping this attempt");
            }

            // Always schedule next sync regardless of result
            scheduleNextSync();
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Error during sync", e);
            scheduleNextSync();
            return Result.success();
        }
    }

    private ForegroundInfo createForegroundInfo() {

        Notification notification =
                new NotificationCompat.Builder(getApplicationContext(), "sync_channel")
                        .setContentTitle("Data Sync")
                        .setContentText("Sync in progress...")
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .build();

        return new ForegroundInfo(1, notification);
    }


    private boolean syncData() {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {false};

        new Handler(Looper.getMainLooper()).post(() -> {
            attendanceService.syncAttendance(
                    () -> {
                        Log.d(TAG, "Sync successful");
                        result[0] = true;
                        latch.countDown();
                    },
                    () -> {
                        Log.d(TAG, "Sync failed");
                        latch.countDown();
                    }
            );
        });

        try {
            latch.await(2, TimeUnit.MINUTES);
            return result[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void scheduleNextSync() {
        WorkManager.getInstance(context).enqueueUniqueWork(
                TAG,
               ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(SyncWorker.class)
                        .setInitialDelay(SYNC_INTERVAL, TimeUnit.MILLISECONDS)
                        .build()
        );
        Log.d(TAG, "Next sync scheduled in 2 minutes");
    }

//    private void scheduleNextSync() {
//        WorkManager.getInstance(context).enqueueUniqueWork(
//                TAG,
//                ExistingWorkPolicy.KEEP,
//                new OneTimeWorkRequest.Builder(SyncWorker.class)
//                        .setInitialDelay(SYNC_INTERVAL, TimeUnit.MILLISECONDS)
//                        .build()
//        );
//        Log.d(TAG, "Next sync scheduled in 2 minutes");
//    }


    private boolean checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(POWER_SERVICE);
            // Return true (should delay sync) if battery optimization IS restricting the app
            return !pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return false; // Pre-M devices don't have this restriction
    }
}