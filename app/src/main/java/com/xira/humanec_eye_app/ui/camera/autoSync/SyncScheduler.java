package com.xira.humanec_eye_app.ui.camera.autoSync;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.BackoffPolicy;
import java.util.concurrent.TimeUnit;

public class SyncScheduler {
    private static final String TAG = "SyncScheduler";
    private static final long INITIAL_DELAY = 2 * 60_000L; // 2 minutes
    private static final long MIN_BACKOFF_DELAY = 10_000L; // 10 seconds

    public static void scheduleSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncWorkRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .setInitialDelay(INITIAL_DELAY, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        MIN_BACKOFF_DELAY,
                        TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                TAG,
                ExistingWorkPolicy.REPLACE,
                syncWorkRequest
        );

        Log.d(TAG, "Scheduled sync with initial delay of 2 minutes");
    }
}