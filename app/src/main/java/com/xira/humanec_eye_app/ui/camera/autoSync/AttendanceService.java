package com.xira.humanec_eye_app.ui.camera.autoSync;

import android.content.Context;
import android.os.Environment;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class AttendanceService {
    private static final String TAG = "AttendanceService";
    private static final String ATTENDANCE_FILE = "attendance_records.json";
    private static final long DUPLICATE_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes in milliseconds
    private final Context context;
    private static final AtomicBoolean isSyncing = new AtomicBoolean(false);
    private long lastPunchTime = 0;
    private static final long COOLDOWN = 5000; // 5 sec

    // Background executor to prevent File I/O from blocking the main thread
    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor();

    public AttendanceService(Context context) {
        this.context = context;
        writeAppLog("DEBUG", "AttendanceService initialized.", null);
    }

    // Static to share across all AttendanceService instances - prevents duplicates
    private static final Set<String> pendingSyncs = new HashSet<>();
    private static final Object SYNC_LOCK = new Object();

    private final List<Attendance> attendanceCache = new ArrayList<>();
    private boolean isDataLoaded = false;
    String organizationId = "";
    String organizationName = "";

    /**
     * Custom Logger: Writes to Logcat AND an app-specific log file on external storage.
     * Uses getExternalFilesDir() to satisfy Scoped Storage rules without requiring extra permissions.
     */
    private void writeAppLog(String level, String msg, Throwable t) {
        // 1. Standard Logcat
        String fullMsg = msg + (t != null ? " | Exception: " + t.getMessage() : "");
        if (level.equals("ERROR")) Log.e(TAG, fullMsg, t);
        else if (level.equals("WARN")) Log.w(TAG, fullMsg);
        else Log.d(TAG, fullMsg);

        // 2. Background File Logging to App-Specific Directory
        logExecutor.execute(() -> {
            try {
                // Uses Context's external files directory to avoid EACCES Permission Denied on Android 10+
                File downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (downloadsDir != null && !downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }

                if (downloadsDir != null) {
                    File logFile = new File(downloadsDir, "AttendanceAppLogs.txt");

                    String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
                    String logEntry = timeStamp + " [" + level + "] " + TAG + ": " + fullMsg + "\n";

                    try (FileWriter writer = new FileWriter(logFile, true)) {
                        writer.append(logEntry);
                        writer.flush();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to write to log file", e);
            }
        });
    }

    public void addAttendance(String empCode, String empName, String orgId, String orgName, long timestamp) {
        writeAppLog("DEBUG", "Entering addAttendance() for empCode: " + empCode + " at " + timestamp, null);

        synchronized (SYNC_LOCK) {
            writeAppLog("DEBUG", "Acquired SYNC_LOCK in addAttendance()", null);
            String recordKey = empCode + "_" + timestamp;
            organizationId = orgId;
            organizationName = orgName;

            if (pendingSyncs.contains(recordKey)) {
                writeAppLog("WARN", "Skipping duplicate: Record already in pending syncs - " + recordKey, null);
                return;
            }

            writeAppLog("DEBUG", "Loading current attendance records for duplicate check.", null);
            List<Attendance> records = loadAttendanceRecords();

            writeAppLog("DEBUG", "Checking for exact match duplicates.", null);
            // Check for exact duplicate (same empCode and timestamp) - regardless of sync status
            for (Attendance record : records) {
                if (record.empCode.equals(empCode) && record.timestamp == timestamp) {
                    writeAppLog("WARN", "Skipping duplicate: Exact match found for " + empCode + " at " + timestamp + " (synced=" + record.synced + ")", null);
                    return;
                }
            }

            writeAppLog("DEBUG", "Checking for 5-minute threshold duplicates.", null);
            // Check if the same employee has an attendance record within the last 5 minutes
            for (Attendance record : records) {
                if (record.empCode.equals(empCode)) {
                    long timeDifference = Math.abs(timestamp - record.timestamp);
                    if (timeDifference < DUPLICATE_THRESHOLD_MS) {
                        if (record.synced) {
                            writeAppLog("WARN", "Skipping: Employee " + empCode + " ALREADY SYNCED within 5 minutes. Existing sync at " + record.timestamp + ", time diff: " + (timeDifference / 1000) + "s", null);
                        } else {
                            writeAppLog("WARN", "Skipping: Employee " + empCode + " has unsynced record within 5 minutes. Time diff: " + (timeDifference / 1000) + "s", null);
                        }
                        return;
                    }
                }
            }

            writeAppLog("DEBUG", "Checking pending syncs for timestamp proximity.", null);
            // Also check pending syncs for timestamp proximity
            for (String pendingKey : pendingSyncs) {
                if (pendingKey.startsWith(empCode + "_")) {
                    try {
                        long pendingTimestamp = Long.parseLong(pendingKey.substring(empCode.length() + 1));
                        long timeDifference = Math.abs(timestamp - pendingTimestamp);
                        if (timeDifference < DUPLICATE_THRESHOLD_MS) {
                            writeAppLog("WARN", "Skipping duplicate: Employee " + empCode + " already in pending sync within 5 minutes", null);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        writeAppLog("ERROR", "Error parsing pending sync timestamp", e);
                    }
                }
            }

            writeAppLog("DEBUG", "Validation passed. Adding new record to list.", null);
            records.add(new Attendance(empCode, empName, timestamp, false));
            pendingSyncs.add(recordKey);

            writeAppLog("DEBUG", "Saving updated attendance records to disk.", null);
            saveAttendanceRecords(records);
            writeAppLog("DEBUG", "Added new attendance record for " + empCode + " at " + timestamp, null);
        }

        writeAppLog("DEBUG", "Checking network connectivity for sync.", null);
        if (NetworkUtils.isNetworkConnected(context)) {
            writeAppLog("DEBUG", "Network is connected. Triggering syncAttendance.", null);
            new Handler(Looper.getMainLooper()).post(() -> {
                syncAttendance(() -> writeAppLog("DEBUG", "Auto-sync onSuccess triggered.", null),
                        () -> writeAppLog("WARN", "Auto-sync onFailure triggered.", null));
            });
        } else {
            writeAppLog("WARN", "No network connection. Sync deferred.", null);
        }
    }

    public void syncAttendance(Runnable onSuccess, Runnable onFailure) {
        writeAppLog("DEBUG", "Entering syncAttendance().", null);
        List<Attendance> unsyncedRecords = getUnsyncedAttendance();

        if (unsyncedRecords.isEmpty()) {
            writeAppLog("DEBUG", "No unsynced attendance records to sync. Aborting sync.", null);
            onSuccess.run();
            return;
        }

        int batchSize = Math.min(5, unsyncedRecords.size());
        writeAppLog("DEBUG", "Total unsynced records: " + unsyncedRecords.size() + ". Batch size set to: " + batchSize, null);

        if (isSyncing.getAndSet(true)) {
            writeAppLog("WARN", "Sync already in progress, skipping.", null);
            onFailure.run();
            return;
        }

        try {
            writeAppLog("DEBUG", "Preparing batch for sync.", null);
            List<Attendance> recordsToSync = new ArrayList<>(unsyncedRecords.subList(0, batchSize));

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            List<Map<String, Object>> attendanceList = new ArrayList<>();

            for (Attendance record : recordsToSync) {
                writeAppLog("DEBUG", "Mapping record for sync: " + record.empCode + " at " + record.timestamp, null);
                Map<String, Object> attendanceMap = new HashMap<>();
                attendanceMap.put("emp_code", record.empCode);
                attendanceMap.put("log_timestamp", dateFormat.format(new Date(record.timestamp)));
                attendanceList.add(attendanceMap);
                pendingSyncs.add(record.empCode + "_" + record.timestamp);
            }

            writeAppLog("DEBUG", "Starting API sync request for " + attendanceList.size() + " records.", null);

            new Handler(Looper.getMainLooper()).post(() -> {
                writeAppLog("DEBUG", "Calling ApiRepository bulkAttendanceMark.", null);
                LiveData<Boolean> syncLiveData = ApiRepository.getInstance(context).bulkAttendanceMark(attendanceList, context);

                Observer<Boolean> observer = new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean success) {
                        writeAppLog("DEBUG", "Received API Response. Success: " + success, null);
                        syncLiveData.removeObserver(this);
                        isSyncing.set(false);

                        if (Boolean.TRUE.equals(success)) {
                            writeAppLog("DEBUG", "Attendance synced successfully, updating local records.", null);
                            List<Attendance> allRecords = loadAttendanceRecords();

                            writeAppLog("DEBUG", "Iterating allRecords to mark synced and remove from pending.", null);
                            for (Attendance record : allRecords) {
                                if (recordsToSync.stream().anyMatch(r ->
                                        r.empCode.equals(record.empCode) && r.timestamp == record.timestamp)) {
                                    writeAppLog("DEBUG", "Marking record as synced: " + record.empCode, null);
                                    record.synced = true;
                                    punchIn(record.empName, record.empCode);
                                    pendingSyncs.remove(record.empCode + "_" + record.timestamp);
                                }
                            }

                            writeAppLog("DEBUG", "Saving synchronized records to disk.", null);
                            saveAttendanceRecords(allRecords);
                            onSuccess.run();

                            if (!getUnsyncedAttendance().isEmpty()) {
                                writeAppLog("DEBUG", "More unsynced records found. Triggering recursive syncAttendance.", null);
                                syncAttendance(() -> {}, () -> {});
                            }
                        } else {
                            writeAppLog("ERROR", "Failed to sync attendance, retaining unsynced records.", null);
                            for (Attendance record : recordsToSync) {
                                pendingSyncs.remove(record.empCode + "_" + record.timestamp);
                                writeAppLog("DEBUG", "Removed from pendingSyncs due to failure: " + record.empCode, null);
                            }
                            onFailure.run();
                        }
                    }
                };
                syncLiveData.observeForever(observer);
            });
        } catch (Exception e) {
            isSyncing.set(false);
            writeAppLog("ERROR", "Error during sync preparation", e);
            onFailure.run();
        }
    }

    public void punchIn(String employeeName, String employeeId) {
        writeAppLog("DEBUG", "Entering punchIn() for: " + employeeId + " (" + employeeName + ")", null);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        writeAppLog("DEBUG", "Querying Firestore for last punch status.", null);
        db.collection("punch_logs")
                .whereEqualTo("user_id", employeeId)
                .orderBy("time", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    writeAppLog("DEBUG", "Firestore query successful.", null);
                    String type = "punch_in"; // default

                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        String lastType = doc.getString("type");
                        writeAppLog("DEBUG", "Last punch type found: " + lastType, null);

                        if ("punch_in".equalsIgnoreCase(lastType)) {
                            type = "punch_out";
                        } else if ("punch_out".equalsIgnoreCase(lastType)) {
                            type = "punch_in";
                        }
                    } else {
                        writeAppLog("DEBUG", "No previous punch logs found. Defaulting to punch_in.", null);
                    }

                    writeAppLog("DEBUG", "Determined next punch type: " + type + ". Saving punch.", null);
                    savePunch(type, employeeName, employeeId);
                })
                .addOnFailureListener(e -> {
                    writeAppLog("ERROR", "Firestore fetch failed. Falling back to default punch_in.", e);
                    savePunch("punch_in", employeeName, employeeId);
                });
    }

    private void savePunch(String type, String employeeName, String employeeId) {
        writeAppLog("DEBUG", "Entering savePunch() for " + employeeId + " with type: " + type, null);
        long now = System.currentTimeMillis();

        if ((now - lastPunchTime) < COOLDOWN) {
            writeAppLog("WARN", "Duplicate punch ignored due to COOLDOWN limit.", null);
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
        data.put("time", com.google.firebase.firestore.FieldValue.serverTimestamp());
        data.put("readable_time", getFormattedTime());

        writeAppLog("DEBUG", "Pushing punch data to Firestore collection 'punch_logs'.", null);
        db.collection("punch_logs")
                .add(data)
                .addOnSuccessListener(doc -> writeAppLog("DEBUG", "Successfully saved punch type to Firestore: " + type, null))
                .addOnFailureListener(e -> writeAppLog("ERROR", "Error saving punch to Firestore.", e));
    }

    private String getFormattedTime() {
        writeAppLog("DEBUG", "Formatting current time.", null);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' hh:mm:ss a");
        String dateTime = now.format(formatter);
        int offsetSeconds = now.getOffset().getTotalSeconds();
        int hours = offsetSeconds / 3600;
        int minutes = Math.abs((offsetSeconds % 3600) / 60);
        String offset = String.format("UTC%+d:%02d", hours, minutes);
        writeAppLog("DEBUG", "Formatted time result: " + dateTime + " " + offset, null);
        return dateTime + " " + offset;
    }

    public List<Attendance> getUnsyncedAttendance() {
        writeAppLog("DEBUG", "Entering getUnsyncedAttendance().", null);
        synchronized (SYNC_LOCK) {
            writeAppLog("DEBUG", "Acquired SYNC_LOCK in getUnsyncedAttendance(). Loading internal records.", null);
            List<Attendance> allRecords = loadAttendanceRecordsInternal();
            List<Attendance> result = new ArrayList<>();

            if (allRecords.isEmpty()) {
                writeAppLog("DEBUG", "No records found internally. Returning empty list.", null);
                return result;
            }

            writeAppLog("DEBUG", "Processing " + allRecords.size() + " total records for unsynced filtration.", null);
            Map<String, List<Long>> syncedMap = new HashMap<>();

            for (Attendance record : allRecords) {
                if (record.synced) {
                    syncedMap.computeIfAbsent(record.empCode, k -> new ArrayList<>()).add(record.timestamp);
                }
            }

            Map<String, Attendance> earliestUnsynced = new HashMap<>();
            List<Attendance> duplicatesToMarkSynced = new ArrayList<>();

            for (Attendance record : allRecords) {
                if (record.synced) continue;

                String empCode = record.empCode;
                writeAppLog("DEBUG", "Evaluating unsynced record for: " + empCode, null);

                if (isDuplicateOfSynced(record, syncedMap.get(empCode))) {
                    writeAppLog("DEBUG", "Record determined as duplicate of already synced record.", null);
                    duplicatesToMarkSynced.add(record);
                    continue;
                }

                Attendance existing = earliestUnsynced.get(empCode);
                if (existing == null) {
                    writeAppLog("DEBUG", "Adding to earliestUnsynced map.", null);
                    earliestUnsynced.put(empCode, record);
                } else {
                    long diff = Math.abs(record.timestamp - existing.timestamp);
                    if (diff < DUPLICATE_THRESHOLD_MS) {
                        writeAppLog("DEBUG", "Threshold breach detected within unsynced records.", null);
                        if (record.timestamp < existing.timestamp) {
                            writeAppLog("DEBUG", "Keeping earlier record.", null);
                            duplicatesToMarkSynced.add(existing);
                            earliestUnsynced.put(empCode, record);
                        } else {
                            writeAppLog("DEBUG", "Marking newer duplicate as synced.", null);
                            duplicatesToMarkSynced.add(record);
                        }
                    } else {
                        writeAppLog("DEBUG", "Outside threshold. Handling separately.", null);
                        earliestUnsynced.put(empCode, record);
                    }
                }
            }

            if (!duplicatesToMarkSynced.isEmpty()) {
                writeAppLog("DEBUG", "Marking " + duplicatesToMarkSynced.size() + " duplicates as synced internally.", null);
                for (Attendance record : duplicatesToMarkSynced) {
                    record.synced = true;
                }
                saveAttendanceRecordsInternal(allRecords);
            }

            result.addAll(earliestUnsynced.values());
            writeAppLog("DEBUG", "Returning " + result.size() + " unique unsynced records.", null);
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
        writeAppLog("DEBUG", "Entering saveAttendanceRecords() wrapper.", null);
        synchronized (SYNC_LOCK) {
            writeAppLog("DEBUG", "Acquired SYNC_LOCK. Delegating to saveAttendanceRecordsInternal.", null);
            saveAttendanceRecordsInternal(records);
        }
    }

    private void saveAttendanceRecordsInternal(List<Attendance> records) {
        writeAppLog("DEBUG", "Entering saveAttendanceRecordsInternal() with " + records.size() + " records.", null);
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

            writeAppLog("DEBUG", "Writing json payload to temporary file.", null);
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(jsonArray.toString());
                writer.flush();
            }

            if (file.exists()) {
                writeAppLog("DEBUG", "Main file exists. Managing backup.", null);
                if (backupFile.exists()) backupFile.delete();
                if (!file.renameTo(backupFile)) {
                    writeAppLog("WARN", "Failed to create backup, attempting direct write.", null);
                }
            }

            if (file.exists() && !file.delete()) {
                writeAppLog("ERROR", "Failed to delete existing file for atomic save.", null);
                if (backupFile.exists()) backupFile.renameTo(file);
                return;
            }

            if (!tempFile.renameTo(file)) {
                writeAppLog("ERROR", "Failed to rename temp file to primary file.", null);
                if (backupFile.exists()) backupFile.renameTo(file);
                return;
            }

            if (backupFile.exists()) {
                writeAppLog("DEBUG", "Cleaning up backup file.", null);
                backupFile.delete();
            }

            writeAppLog("DEBUG", "SAVE operation completed successfully for " + records.size() + " records.", null);
        } catch (Exception e) {
            writeAppLog("ERROR", "Exception thrown in saveAttendanceRecordsInternal", e);
        }
    }

    private List<Attendance> loadAttendanceRecords() {
        writeAppLog("DEBUG", "Entering loadAttendanceRecords() wrapper.", null);
        synchronized (SYNC_LOCK) {
            writeAppLog("DEBUG", "Acquired SYNC_LOCK. Delegating to loadAttendanceRecordsInternal.", null);
            return loadAttendanceRecordsInternal();
        }
    }

    private List<Attendance> loadAttendanceRecordsInternal() {
        writeAppLog("DEBUG", "Entering loadAttendanceRecordsInternal().", null);
        List<Attendance> records = new ArrayList<>();
        try {
            File file = new File(context.getFilesDir(), ATTENDANCE_FILE);
            File tempFile = new File(context.getFilesDir(), ATTENDANCE_FILE + ".tmp");
            File backupFile = new File(context.getFilesDir(), ATTENDANCE_FILE + ".bak");

            writeAppLog("DEBUG", "Evaluating file existence and backup recovery states.", null);
            if (!file.exists()) {
                if (backupFile.exists()) {
                    if (backupFile.renameTo(file)) writeAppLog("DEBUG", "Recovered from backup file.", null);
                    else writeAppLog("WARN", "Failed to recover from backup file.", null);
                } else if (tempFile.exists()) {
                    if (tempFile.renameTo(file)) writeAppLog("DEBUG", "Recovered from temp file.", null);
                    else writeAppLog("WARN", "Failed to recover from temp file.", null);
                }
            }

            if (file.exists()) {
                writeAppLog("DEBUG", "Reading primary file contents.", null);
                StringBuilder jsonString = new StringBuilder();
                try (FileReader reader = new FileReader(file)) {
                    int character;
                    while ((character = reader.read()) != -1) {
                        jsonString.append((char) character);
                    }
                }

                String content = jsonString.toString().trim();
                if (content.isEmpty() || !content.startsWith("[")) {
                    writeAppLog("ERROR", "Invalid JSON content detected. Attempting backup read.", null);
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
                    writeAppLog("DEBUG", "Parsing JSON payload.", null);
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
                    writeAppLog("DEBUG", "Loaded " + records.size() + " records successfully.", null);

                    for (Attendance record : records) {
                        if (record.synced) {
                            pendingSyncs.remove(record.empCode + "_" + record.timestamp);
                        }
                    }
                }
            } else {
                writeAppLog("DEBUG", "No attendance file exists yet.", null);
            }
        } catch (Exception e) {
            writeAppLog("ERROR", "Exception thrown in loadAttendanceRecordsInternal.", e);
        }
        return records;
    }

    public JSONArray getAllAttendanceData() {
        writeAppLog("DEBUG", "Entering getAllAttendanceData().", null);
        JSONArray jsonArray = new JSONArray();

        try {
            File file = new File(context.getFilesDir(), ATTENDANCE_FILE);
            File tempFile = new File(context.getFilesDir(), ATTENDANCE_FILE + ".tmp");

            if (!file.exists() && tempFile.exists()) {
                writeAppLog("WARN", "Main file missing. Attempting rename of temp file.", null);
                if (!tempFile.renameTo(file)) {
                    writeAppLog("WARN", "Failed to recover from temp file.", null);
                }
            }

            if (file.exists()) {
                writeAppLog("DEBUG", "Reading data for raw JSON extraction.", null);
                StringBuilder jsonString = new StringBuilder();
                try (FileReader reader = new FileReader(file)) {
                    int character;
                    while ((character = reader.read()) != -1) {
                        jsonString.append((char) character);
                    }
                }
                jsonArray = new JSONArray(jsonString.toString());
                writeAppLog("DEBUG", "Successfully extracted JSONArray block.", null);
            }
        } catch (Exception e) {
            writeAppLog("ERROR", "Error extracting raw attendance records JSON", e);
        }
        return jsonArray;
    }

    private void ensureDataLoaded() {
        writeAppLog("DEBUG", "Entering ensureDataLoaded().", null);
        synchronized (SYNC_LOCK) {
            if (!isDataLoaded) {
                writeAppLog("DEBUG", "Data not loaded. Initializing Cache.", null);
                attendanceCache.clear();
                attendanceCache.addAll(loadAttendanceRecordsInternal());
                isDataLoaded = true;
            } else {
                writeAppLog("DEBUG", "Data is already loaded.", null);
            }
        }
    }

    public void saveAttendance(Attendance attendance) {
        writeAppLog("DEBUG", "Entering saveAttendance() wrapper for object payload.", null);
        synchronized (SYNC_LOCK) {
            ensureDataLoaded();
            attendance.synced = false;
            attendanceCache.add(attendance);
            writeAppLog("DEBUG", "Object injected into cache. Saving internal array.", null);
            saveAttendanceRecordsInternal(attendanceCache);
        }
    }

    public boolean hasUnsyncedData() {
        writeAppLog("DEBUG", "Evaluating hasUnsyncedData().", null);
        synchronized (SYNC_LOCK) {
            ensureDataLoaded();
            for (Attendance record : attendanceCache) {
                if (!record.synced) {
                    writeAppLog("DEBUG", "Found unsynced data state: TRUE.", null);
                    return true;
                }
            }
            writeAppLog("DEBUG", "No unsynced data state: FALSE.", null);
            return false;
        }
    }

    public void markAsSynced(List<Attendance> syncedList) {
        writeAppLog("DEBUG", "Entering markAsSynced() for list size: " + syncedList.size(), null);
        synchronized (SYNC_LOCK) {
            for (Attendance synced : syncedList) {
                writeAppLog("DEBUG", "Flagging record as synced in cache.", null);
                synced.synced = true;
            }
            writeAppLog("DEBUG", "Saving cache to persistent storage.", null);
            saveAttendanceRecordsInternal(attendanceCache);
        }
    }
}