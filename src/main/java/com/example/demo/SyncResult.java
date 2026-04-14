package com.example.demo;

public class SyncResult {
    public int successCount;
    public int errorCount;
    public String status;
    public String message;

    public SyncResult(int successCount, int errorCount, String status, String message) {
        this.successCount = successCount;
        this.errorCount = errorCount;
        this.status = status;
        this.message = message;
    }

    @Override
    public String toString() {
        return String.format("SyncResult{status='%s', success=%d, errors=%d, message='%s'}",
            status, successCount, errorCount, message);
    }
}
