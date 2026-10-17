package com.smartcampus.notify;

/**
 * Result outcome of a push notification dispatch attempt.
 */
public enum PushResult {
    SENT,
    INVALID_TOKEN,
    FAILED
}
