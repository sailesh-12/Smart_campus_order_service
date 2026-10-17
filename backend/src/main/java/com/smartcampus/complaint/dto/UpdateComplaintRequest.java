package com.smartcampus.complaint.dto;

import com.smartcampus.complaint.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateComplaintRequest(
        @NotNull(message = "Complaint status is required")
        ComplaintStatus status,

        @Size(max = 1000)
        String adminNote
) {
}
