package com.smartcampus.complaint;

/**
 * Status lifecycle of a student order complaint.
 * Matches V3__complaints.sql check constraint.
 */
public enum ComplaintStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED,
    REJECTED
}
