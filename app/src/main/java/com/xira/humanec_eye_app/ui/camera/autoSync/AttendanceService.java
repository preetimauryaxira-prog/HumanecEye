package com.xira.humanec_eye_app.ui.camera.autoSync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.xira.humanec_eye_app.api.ApiRepository;
import com.xira.humanec_eye_app.model.Attendance;
import com.xira.humanec_eye_app.utils.NetworkUtils;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class AttendanceService {
    private static final String TAG = "AttendanceService";
    private static final String ATTENDANCE_FILE = "attendance_records.json";
    private static final long DUPLICATE_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes in milliseconds
    private final Context context;
    private static final AtomicBoolean isSyncing = new AtomicBoolean(false);
    private long lastPunchTime = 0;
    private static final long COOLDOWN = 5000; // 5 sec

    public AttendanceService(Context context) {
        this.context = context;
    }

    // Static to share across all AttendanceService instances - prevents duplicates
    private static final Set<String> pendingSyncs = new HashSet<>();
    private static final Object SYNC_LOCK = new Object();

    private final List<Attendance> attendanceCache = new ArrayList<>();
    private boolean isDataLoaded = false;
    String organizationId = "";
    String organizationName = "";

    public void addAttendance(String empCode, String empName, String orgId, String orgName,long timestamp) {
        Log.d(TAG, "Skipping duplicate: Record already in pending syncs - ");

        synchronized (SYNC_LOCK) {
            String recordKey = empCode + "_" + timestamp;
            organizationId = orgId;
            organizationName = orgName;
            if (pendingSyncs.contains(recordKey)) {
                Log.d(TAG, "Skipping duplicate: Record already in pending syncs - " + recordKey);
                return;
            }

            List<Attendance> records = loadAttendanceRecords();
          
            // Check for exact duplicate (same empCode and timestamp) - regardless of sync status
            for (Attendance record : records) {
                if (record.empCode.equals(empCode) && record.timestamp == timestamp) {
                    Log.d(TAG, "Skipping duplicate: Exact match found for " + empCode + " at " + timestamp + 
                            " (synced=" + record.synced + ")");
                    return;
                }
            }

            // Check if the same employee has an attendance record within the last 5 minutes
            // This checks BOTH synced and unsynced records to prevent duplicate API calls
            for (Attendance record : records) {
                if (record.empCode.equals(empCode)) {
                    long timeDifference = Math.abs(timestamp - record.timestamp);
                    if (timeDifference < DUPLICATE_THRESHOLD_MS) {
                        if (record.synced) {
                            // Already synced record exists within 5 minutes - MUST NOT sync again
                            Log.d(TAG, "Skipping: Employee " + empCode + " ALREADY SYNCED within 5 minutes. " +
                                    "Existing sync at " + record.timestamp + ", time diff: " + (timeDifference / 1000) + "s");
                        } else {
                            // Unsynced record exists within 5 minutes
                            Log.d(TAG, "Skipping: Employee " + empCode + " has unsynced record within 5 minutes. " +
                                    "Time diff: " + (timeDifference / 1000) + "s");
                        }
                        return;
                    }
                }
            }

            // Also check pending syncs for timestamp proximity
            for (String pendingKey : pendingSyncs) {
                if (pendingKey.startsWith(empCode + "_")) {
                    try {
                        long pendingTimestamp = Long.parseLong(pendingKey.substring(empCode.length() + 1));
                        long timeDifference = Math.abs(timestamp - pendingTimestamp);
                        if (timeDifference < DUPLICATE_THRESHOLD_MS) {
                            Log.d(TAG, "Skipping duplicate: Employee " + empCode + " already in pending sync within 5 minutes");
                            return;
                        }
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing pending sync timestamp", e);
                    }
                }
            }

            records.add(new Attendance(empCode, empName, timestamp, false));
            pendingSyncs.add(recordKey);
            saveAttendanceRecords(records);
            Log.d(TAG, "Added new attendance record for " + empCode + " at " + timestamp);
        }

        if (NetworkUtils.isNetworkConnected(context)) {
            new Handler(Looper.getMainLooper()).post(() -> {
                syncAttendance(() -> {}, () -> {});
            });
        }
    }

    public void syncAttendance(Runnable onSuccess, Runnable onFailure) {
        List<Attendance> unsyncedRecords = getUnsyncedAttendance();
        if (unsyncedRecords.isEmpty()) {
            Log.d(TAG, "No unsynced attendance records to sync");
            onSuccess.run();
            return;
        }

        int batchSize = Math.min(5, unsyncedRecords.size());

        if (isSyncing.getAndSet(true)) {
            Log.w(TAG, "Sync already in progress, skipping");
            onFailure.run();
            return;
        }

        try {
//            List<Attendance> recordsToSync = new ArrayList<>(unsyncedRecords);
            List<Attendance> recordsToSync = new ArrayList<>(unsyncedRecords.subList(0, batchSize));

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            List<Map<String, Object>> attendanceList = new ArrayList<>();

            for (Attendance record : recordsToSync) {
                Map<String, Object> attendanceMap = new HashMap<>();
                attendanceMap.put("code", record.empCode);
                attendanceMap.put("loG_DATETIME", dateFormat.format(new Date(record.timestamp)));
                attendanceList.add(attendanceMap);
                pendingSyncs.add(record.empCode + "_" + record.timestamp);
            }

            Log.d(TAG, "Starting sync for " + attendanceList.size() + " unSynced attendance records");

            new Handler(Looper.getMainLooper()).post(() -> {
                LiveData<Boolean> syncLiveData = ApiRepository.getInstance(context).bulkAttendanceMark(attendanceList, context);
                Observer<Boolean> observer = new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean success) {
                        syncLiveData.removeObserver(this);
                        isSyncing.set(false);

                        if (Boolean.TRUE.equals(success))  {
                            Log.d(TAG, "Attendance synced successfully, updating records");
                            List<Attendance> allRecords = loadAttendanceRecords();

                            // Update synced status and remove from pending
                            for (Attendance record : allRecords) {
                                if (recordsToSync.stream().anyMatch(r ->
                                        r.empCode.equals(record.empCode) && r.timestamp == record.timestamp)) {
                                    record.synced = true;
                                    punchIn(record.empName, record.empCode);
                                    pendingSyncs.remove(record.empCode + "_" + record.timestamp);
                                }
                            }
                            saveAttendanceRecords(allRecords);
                            onSuccess.run();
                            if (!getUnsyncedAttendance().isEmpty()) {
                                syncAttendance(() -> {}, () -> {});
                            }
                        } else {


                            Log.d(TAG, "Failed to sync attendance, retaining unsynced records");
                            // Clean up pending syncs for failed records
                            for (Attendance record : recordsToSync) {
                                pendingSyncs.remove(record.empCode + "_" + record.timestamp);
                            }

                            onFailure.run();

                        }
                    }
                };
                syncLiveData.observeForever(observer);
            });
        } catch (Exception e) {
            isSyncing.set(false);
            Log.e(TAG, "Error during sync preparation", e);
            onFailure.run();

        }
    }

    public void punchIn(String employeeName, String employeeId) {

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("punch_logs")
                .whereEqualTo("user_id", employeeId)
                .orderBy("time", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    String type = "punch_in"; // default

                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {

                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);

                        String lastType = doc.getString("type");

                        if ("punch_in".equalsIgnoreCase(lastType)) {
                            type = "punch_out";
                        } else if ("punch_out".equalsIgnoreCase(lastType)) {
                            type = "punch_in";
                        }
                    }

                    // ✅ Final save call
                    savePunch(type,employeeName, employeeId);

                })
                .addOnFailureListener(e -> {
                    Log.e("PUNCH", "Fetch failed: ", e);

                    // optional fallback
                    savePunch("punch_in", employeeName,employeeId);
                });
    }


    private void savePunch(String type, String employeeName, String employeeId) {

        long now = System.currentTimeMillis();

        // ✅ Cooldown check (duplicate रोकने के लिए)
        if ((now - lastPunchTime) < COOLDOWN) {
            Log.d("PUNCH", "Duplicate punch ignored");
            return;
        }
        lastPunchTime = now;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> data = new HashMap<>();
        data.put("user_name", employeeName);
        data.put("user_id", employeeId);
        data.put("organization_id", organizationId);
        data.put("organization_name", organizationName);
        data.put("type", type);

        // ✅ Firebase server time (for sorting)
        data.put("time", com.google.firebase.firestore.FieldValue.serverTimestamp());

        // ✅ Readable time (for UI)
        data.put("readable_time", getFormattedTime());

        db.collection("punch_logs")
                .add(data)
                .addOnSuccessListener(doc ->
                        Log.d("PUNCH", "Saved: " + type)
                )
                .addOnFailureListener(e ->
                        Log.e("PUNCH", "Error: ", e)
                );
    }
    private String getFormattedTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' hh:mm:ss a");

        String dateTime = now.format(formatter);

        int offsetSeconds = now.getOffset().getTotalSeconds();
        int hours = offsetSeconds / 3600;
        int minutes = Math.abs((offsetSeconds % 3600) / 60);

        String offset = String.format("UTC%+d:%02d", hours, minutes);

        return dateTime + " " + offset;
    }


//    public List<Attendance> getUnsyncedAttendance() {
//        synchronized (SYNC_LOCK) {
//            List<Attendance> records = loadAttendanceRecordsInternal();
//            List<Attendance> unsyncedRecords = new ArrayList<>();
//
//            // First, collect all synced records per employee for duplicate checking
//            Map<String, List<Long>> syncedTimestampsPerEmployee = new HashMap<>();
//            for (Attendance record : records) {
//                if (record.synced) {
//                    String empCode = record.empCode;
//                    if (!syncedTimestampsPerEmployee.containsKey(empCode)) {
//                        syncedTimestampsPerEmployee.put(empCode, new ArrayList<>());
//                    }
//                    syncedTimestampsPerEmployee.get(empCode).add(record.timestamp);
//                }
//            }
//
//            // Map to track the earliest unsynced attendance per employee to avoid duplicates within 5 minutes
//            Map<String, Attendance> earliestPerEmployee = new HashMap<>();
//            List<Attendance> recordsToMarkAsSynced = new ArrayList<>();
//
//            for (Attendance record : records) {
//                if (!record.synced) {
//                    String empCode = record.empCode;
//
//                    // First, check if this unsynced record is within 5 minutes of ANY SYNCED record
//                    // If so, it's a duplicate and should NOT be synced again
//                    boolean isDuplicateOfSynced = false;
//                    if (syncedTimestampsPerEmployee.containsKey(empCode)) {
//                        for (Long syncedTimestamp : syncedTimestampsPerEmployee.get(empCode)) {
//                            long timeDifference = Math.abs(record.timestamp - syncedTimestamp);
//                            if (timeDifference < DUPLICATE_THRESHOLD_MS) {
//                                Log.d(TAG, "Filtering unsynced record for " + empCode +
//                                      " - already has SYNCED record within 5 minutes. Time diff: " + (timeDifference / 1000) + "s");
//                                isDuplicateOfSynced = true;
//                                recordsToMarkAsSynced.add(record); // Mark as synced to prevent future attempts
//                                break;
//                            }
//                        }
//                    }
//
//                    if (isDuplicateOfSynced) {
//                        continue;
//                    }
//
//                    // Check if we already have an unsynced record for this employee
//                    if (earliestPerEmployee.containsKey(empCode)) {
//                        Attendance existing = earliestPerEmployee.get(empCode);
//                        long timeDifference = Math.abs(record.timestamp - existing.timestamp);
//
//                        // If within 5 minutes, keep only the earlier one (first punch)
//                        if (timeDifference < DUPLICATE_THRESHOLD_MS) {
//                            // Keep the earlier record, skip this one
//                            if (record.timestamp < existing.timestamp) {
//                                recordsToMarkAsSynced.add(existing);
//                                earliestPerEmployee.put(empCode, record);
//                            } else {
//                                recordsToMarkAsSynced.add(record);
//                            }
//                            Log.d(TAG, "Filtering duplicate unsynced record for " + empCode +
//                                  " within 5 minutes - keeping earliest punch only");
//                            continue;
//                        }
//                    }
//                    earliestPerEmployee.put(empCode, record);
//                }
//            }
//
//            // Mark duplicate records as synced to prevent future attempts
//            if (!recordsToMarkAsSynced.isEmpty()) {
//                for (Attendance record : recordsToMarkAsSynced) {
//                    record.synced = true;
//                }
//                saveAttendanceRecordsInternal(records);
//                Log.d(TAG, "Marked " + recordsToMarkAsSynced.size() + " duplicate records as synced");
//            }
//
//            unsyncedRecords.addAll(earliestPerEmployee.values());
//            Log.d(TAG, "Found " + unsyncedRecords.size() + " unique unsynced records after filtering all duplicates");
//            return unsyncedRecords;
//        }
//    }

    public List<Attendance> getUnsyncedAttendance() {
        synchronized (SYNC_LOCK) {

            List<Attendance> allRecords = loadAttendanceRecordsInternal();
            List<Attendance> result = new ArrayList<>();

            if (allRecords.isEmpty()) return result;

            // Map: empCode → list of synced timestamps
            Map<String, List<Long>> syncedMap = new HashMap<>();

            // Step 1: Collect synced timestamps per employee
            for (Attendance record : allRecords) {
                if (record.synced) {
                    syncedMap
                            .computeIfAbsent(record.empCode, k -> new ArrayList<>())
                            .add(record.timestamp);
                }
            }

            // Map: empCode → earliest unsynced attendance
            Map<String, Attendance> earliestUnsynced = new HashMap<>();

            List<Attendance> duplicatesToMarkSynced = new ArrayList<>();

            // Step 2: Process unsynced records
            for (Attendance record : allRecords) {

                if (record.synced) continue;

                String empCode = record.empCode;

                // 🔹 Check duplicate against already synced records
                if (isDuplicateOfSynced(record, syncedMap.get(empCode))) {
                    duplicatesToMarkSynced.add(record);
                    continue;
                }

                // 🔹 Check duplicate among unsynced records (5 min rule)
                Attendance existing = earliestUnsynced.get(empCode);

                if (existing == null) {
                    earliestUnsynced.put(empCode, record);
                } else {
                    long diff = Math.abs(record.timestamp - existing.timestamp);

                    if (diff < DUPLICATE_THRESHOLD_MS) {
                        // Keep earlier record only
                        if (record.timestamp < existing.timestamp) {
                            duplicatesToMarkSynced.add(existing);
                            earliestUnsynced.put(empCode, record);
                        } else {
                            duplicatesToMarkSynced.add(record);
                        }
                    } else {
                        // If outside threshold → treat separately
                        earliestUnsynced.put(empCode, record);
                    }
                }
            }

            // Step 3: Mark duplicates as synced
            if (!duplicatesToMarkSynced.isEmpty()) {
                for (Attendance record : duplicatesToMarkSynced) {
                    record.synced = true;
                }
                saveAttendanceRecordsInternal(allRecords);
            }

            result.addAll(earliestUnsynced.values());
            return result;
        }
    }


    private boolean isDuplicateOfSynced(Attendance record, List<Long> syncedTimestamps) {

        if (syncedTimestamps == null) return false;

        for (Long syncedTime : syncedTimestamps) {
            long diff = Math.abs(record.timestamp - syncedTime);
            if (diff < DUPLICATE_THRESHOLD_MS) {
                return true;
            }
        }
        return false;
    }


    private void saveAttendanceRecords(List<Attendance> records) {
        synchronized (SYNC_LOCK) {
            saveAttendanceRecordsInternal(records);
        }
    }

    // Internal method without synchronization - must be called from synchronized block
    private void saveAttendanceRecordsInternal(List<Attendance> records) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Attendance record : records) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("empCode", record.empCode);
                jsonObject.put("empName", record.empName);
                jsonObject.put("timestamp", record.timestamp);
                jsonObject.put("synced", record.synced);
                jsonArray.put(jsonObject);


            }

            File file = new File(context.getFilesDir(), ATTENDANCE_FILE);
            File tempFile = new File(context.getFilesDir(), ATTENDANCE_FILE + ".tmp");
            File backupFile = new File(context.getFilesDir(), ATTENDANCE_FILE + ".bak");

            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(jsonArray.toString());
                writer.flush();
            }

            // Create backup of existing file before replacing
            if (file.exists()) {
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                if (!file.renameTo(backupFile)) {
                    Log.w(TAG, "Failed to create backup, attempting direct write");
                }
            }

            // On Windows/Android, renameTo fails if destination exists - delete first
            if (file.exists() && !file.delete()) {
                Log.e(TAG, "Failed to delete existing file for atomic save");
                // Try to restore from backup
                if (backupFile.exists()) {
                    backupFile.renameTo(file);
                }
                return;
            }

            if (!tempFile.renameTo(file)) {
                Log.e(TAG, "Failed to rename temp file");
                // Restore from backup if available
                if (backupFile.exists()) {
                    backupFile.renameTo(file);
                }
                return;
            }

            // Clean up backup file on success
            if (backupFile.exists()) {
                backupFile.delete();
            }

            Log.d("SAVE", "start");
            Log.d("SAVE", jsonArray.toString());
            Log.d("SAVE", "end");


            Log.d(TAG, "Saved " + records.size() + " records");
        } catch (Exception e) {
            Log.e(TAG, "Error saving attendance records", e);
        }
    }

    private List<Attendance> loadAttendanceRecords() {
        synchronized (SYNC_LOCK) {
            return loadAttendanceRecordsInternal();
        }
    }

    // Internal method without synchronization - must be called from synchronized block
    private List<Attendance> loadAttendanceRecordsInternal() {
        List<Attendance> records = new ArrayList<>();
        try {
            File file = new File(context.getFilesDir(), ATTENDANCE_FILE);
            File tempFile = new File(context.getFilesDir(), ATTENDANCE_FILE + ".tmp");
            File backupFile = new File(context.getFilesDir(), ATTENDANCE_FILE + ".bak");

            // Recovery priority: main file > backup file > temp file
            if (!file.exists()) {
                if (backupFile.exists()) {
                    if (backupFile.renameTo(file)) {
                        Log.d(TAG, "Recovered from backup file");
                    } else {
                        Log.w(TAG, "Failed to recover from backup file");
                    }
                } else if (tempFile.exists()) {
                    if (tempFile.renameTo(file)) {
                        Log.d(TAG, "Recovered from temp file");
                    } else {
                        Log.w(TAG, "Failed to recover from temp file");
                    }
                }
            }

            if (file.exists()) {
                StringBuilder jsonString = new StringBuilder();
                try (FileReader reader = new FileReader(file)) {
                    int character;
                    while ((character = reader.read()) != -1) {
                        jsonString.append((char) character);
                    }
                }

                String content = jsonString.toString().trim();
                if (content.isEmpty() || !content.startsWith("[")) {
                    Log.e(TAG, "Invalid JSON content, attempting backup recovery");
                    // Try backup file
                    if (backupFile.exists()) {
                        StringBuilder backupJson = new StringBuilder();
                        try (FileReader reader = new FileReader(backupFile)) {
                            int character;
                            while ((character = reader.read()) != -1) {
                                backupJson.append((char) character);
                            }
                        }
                        content = backupJson.toString().trim();
                    }
                }

                if (!content.isEmpty() && content.startsWith("[")) {
                    JSONArray jsonArray = new JSONArray(content);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        records.add(new Attendance(
                                jsonObject.getString("empCode"),
                                jsonObject.getString("empName"),
                                jsonObject.getLong("timestamp"),
                                jsonObject.optBoolean("synced")
                        ));
                    }
                    Log.d(TAG, "Loaded " + records.size() + " records from " + file.getAbsolutePath());
                    for (Attendance record : records) {
                        if (record.synced) {
                            pendingSyncs.remove(record.empCode + "_" + record.timestamp);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading attendance records", e);
        }
        return records;
    }

    public  JSONArray getAllAttendanceData() {
        JSONArray jsonArray=new JSONArray();

        try {
            File file = new File(context.getFilesDir(), ATTENDANCE_FILE);
            File tempFile = new File(context.getFilesDir(), ATTENDANCE_FILE + ".tmp");

            // If temp file exists but main file doesn't, recover from temp
            if (!file.exists() && tempFile.exists()) {
                if (!tempFile.renameTo(file)) {
                    Log.w(TAG, "Failed to recover from temp file");
                }
            }

            if (file.exists()) {
                StringBuilder jsonString = new StringBuilder();
                try (FileReader reader = new FileReader(file)) {
                    int character;
                    while ((character = reader.read()) != -1) {
                        jsonString.append((char) character);
                    }
                }

               jsonArray  = new JSONArray(jsonString.toString());

            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading attendance records", e);
        }
        return jsonArray;
    }


//    public void syncAttendance(Runnable onSuccess, Runnable onFailure) {
//
//        if (isSyncing.get()) {
//            Log.w(TAG, "Sync already running");
//            return;
//        }
//
//        List<Attendance> unsyncedRecords = getUnsyncedAttendance();
//
//        if (unsyncedRecords.isEmpty()) {
//            onSuccess.run();
//            return;
//        }
//
//        isSyncing.set(true);
//
//        int batchSize = Math.min(5, unsyncedRecords.size());
//        List<Attendance> recordsToSync =
//                new ArrayList<>(unsyncedRecords.subList(0, batchSize));
//
//        SimpleDateFormat dateFormat =
//                new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
//
//        List<Map<String, Object>> attendanceList = new ArrayList<>();
//
//        for (Attendance record : recordsToSync) {
//
//            Map<String, Object> map = new HashMap<>();
//            map.put("code", record.empCode);
//            map.put("loG_DATETIME",
//                    dateFormat.format(new Date(record.timestamp)));
//
//            attendanceList.add(map);
//        }
//
//        LiveData<Boolean> liveData =
//                ApiRepository.getInstance(context)
//                        .bulkAttendanceMark(attendanceList, context);
//
//        Observer<Boolean> observer = new Observer<Boolean>() {
//            @Override
//            public void onChanged(Boolean success) {
//
//                liveData.removeObserver(this);
//                isSyncing.set(false);
//
//                if (Boolean.TRUE.equals(success)) {
//
//                    // Use cached markAsSynced instead of reload
//                    markAsSynced(recordsToSync);
//
//                    onSuccess.run();
//
//                } else {
//
//                    onFailure.run();
//                }
//            }
//        };
//
//        liveData.observeForever(observer);
//    }

    // =============================
    // Load Data Once
    // =============================
    private void ensureDataLoaded() {
        synchronized (SYNC_LOCK) {
            if (!isDataLoaded) {
                attendanceCache.clear();
                attendanceCache.addAll(loadAttendanceRecordsInternal());
                isDataLoaded = true;
            }
        }
    }

    // =============================
    // Save Attendance (Offline Safe)
    // =============================
    public void saveAttendance(Attendance attendance) {
        synchronized (SYNC_LOCK) {

            ensureDataLoaded();

            attendance.synced = false;

            attendanceCache.add(attendance);

            saveAttendanceRecordsInternal(attendanceCache);
        }
    }

    // =============================
    // Lightweight Check (Handler use)
    // =============================
    public boolean hasUnsyncedData() {
        synchronized (SYNC_LOCK) {
            ensureDataLoaded();
            for (Attendance record : attendanceCache) {
                if (!record.synced) {
                    return true;
                }
            }
            return false;
        }
    }

    // =============================
    // Get Unsynced Records (Filtered)
    // =============================
//    public List<Attendance> getUnsyncedAttendance() {
//
//        synchronized (SYNC_LOCK) {
//
//            ensureDataLoaded();
//
//            Map<String, Attendance> earliestPerEmployee = new HashMap<>();
//
//            for (Attendance record : attendanceCache) {
//
//                if (!record.synced) {
//
//                    String empCode = record.empCode;
//
//                    if (earliestPerEmployee.containsKey(empCode)) {
//
//                        Attendance existing = earliestPerEmployee.get(empCode);
//                        long diff = Math.abs(record.timestamp - existing.timestamp);
//
//                        if (diff < DUPLICATE_THRESHOLD_MS) {
//
//                            if (record.timestamp < existing.timestamp) {
//                                earliestPerEmployee.put(empCode, record);
//                            }
//
//                            continue;
//                        }
//                    }
//
//                    earliestPerEmployee.put(empCode, record);
//                }
//            }
//
//            return new ArrayList<>(earliestPerEmployee.values());
//        }
//    }

    // =============================
    // Mark As Synced After SUCCESS
    // =============================
    public void markAsSynced(List<Attendance> syncedList) {

        synchronized (SYNC_LOCK) {

            for (Attendance synced : syncedList) {
                synced.synced = true;
            }

            saveAttendanceRecordsInternal(attendanceCache);
        }
    }

    // =============================
    // File Read
    // =============================
//    private List<Attendance> loadAttendanceRecordsInternal() {
//
//        List<Attendance> records = new ArrayList<>();
//
//        try {
//
//            File file = new File(context.getFilesDir(), ATTENDANCE_FILE);
//
//            if (!file.exists()) return records;
//
//            StringBuilder jsonString = new StringBuilder();
//
//            try (FileReader reader = new FileReader(file)) {
//                int character;
//                while ((character = reader.read()) != -1) {
//                    jsonString.append((char) character);
//                }
//            }
//
//            String content = jsonString.toString().trim();
//
//            if (!content.isEmpty()) {
//
//                JSONArray jsonArray = new JSONArray(content);
//
//                for (int i = 0; i < jsonArray.length(); i++) {
//
//                    JSONObject obj = jsonArray.getJSONObject(i);
//
//                    records.add(new Attendance(
//                            obj.getString("empCode"),
//                            obj.getString("empName"),
//                            obj.getLong("timestamp"),
//                            obj.optBoolean("synced")
//                    ));
//                }
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        return records;
//    }

    // =============================
    // File Write
    // =============================
//    private void saveAttendanceRecordsInternal(List<Attendance> records) {
//
//        try {
//
//            JSONArray jsonArray = new JSONArray();
//
//            for (Attendance record : records) {
//
//                JSONObject obj = new JSONObject();
//                obj.put("empCode", record.empCode);
//                obj.put("empName", record.empName);
//                obj.put("timestamp", record.timestamp);
//                obj.put("synced", record.synced);
//
//                jsonArray.put(obj);
//            }
//
//            File file = new File(context.getFilesDir(), ATTENDANCE_FILE);
//
//            try (FileWriter writer = new FileWriter(file, false)) {
//                writer.write(jsonArray.toString());
//            }
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }


}