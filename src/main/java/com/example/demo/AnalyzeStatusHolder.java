package com.example.demo;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AnalyzeStatusHolder {

    public enum Status { NOT_STARTED, STARTED, RUNNING, FINISHED }

    private volatile Status status = Status.NOT_STARTED;
    private volatile String runId;
    private volatile LocalDateTime startTime;
    private volatile LocalDateTime endTime;

    public synchronized void start() {
        if (isRunning()) {
            throw new IllegalStateException("Analyze already running");
        }
        this.status = Status.STARTED;
        this.runId = UUID.randomUUID().toString();
        this.startTime = LocalDateTime.now();
        this.endTime = null;
    }

    public synchronized void markRunning() {
        if (status == Status.STARTED) {
            this.status = Status.RUNNING;
        }
    }

    public synchronized void finish() {
        if (status != Status.NOT_STARTED) {
            this.status = Status.FINISHED;
            this.endTime = LocalDateTime.now();
        }
    }

    public boolean isRunning() {
        return status == Status.STARTED || status == Status.RUNNING;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("status", status.toString());
        map.put("runId", runId);
        map.put("startTime", startTime);
        map.put("endTime", endTime);

        if (startTime != null) {
            LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
            long elapsedSeconds = Duration.between(startTime, end).getSeconds();
            map.put("elapsedSeconds", elapsedSeconds);
        }

        return map;
    }
}
