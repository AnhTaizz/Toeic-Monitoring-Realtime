package vn.edu.toeic.client.monitoring;

import java.time.Instant;

/** startInstant null: không đủ metadata để phân biệt hoàn hảo PID reuse. */
public record ProcessIdentity(String collectorSessionId, long pid, Instant startInstant) { }
