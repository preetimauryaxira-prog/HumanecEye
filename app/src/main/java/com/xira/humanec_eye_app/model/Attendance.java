package com.xira.humanec_eye_app.model;

public class Attendance {
    public String empCode;
    public String empName;
    public long timestamp;
    private static final long ATTENDANCE_COOLDOWN_MS = 5 * 60 * 1000;
    public boolean synced;

    public Attendance(String empCode, String empName, long timestamp, boolean synced) {
        this.empCode = empCode;
        this.empName = empName;
        this.timestamp = timestamp;
        this.synced=synced;
    }

    public boolean isWithinCooldown(long currentTime) {
        return (currentTime - this.timestamp) < ATTENDANCE_COOLDOWN_MS;
    }
}