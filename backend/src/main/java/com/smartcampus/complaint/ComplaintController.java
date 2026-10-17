package com.smartcampus.complaint;

import com.smartcampus.common.security.CurrentUser;
import com.smartcampus.complaint.dto.ComplaintResponse;
import com.smartcampus.complaint.dto.CreateComplaintRequest;
import com.smartcampus.complaint.dto.UpdateComplaintRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;
    private final CurrentUser currentUser;

    @PostMapping("/complaints")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ComplaintResponse> createComplaint(@Valid @RequestBody CreateComplaintRequest request) {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/complaints/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<ComplaintResponse>> getMyComplaints() {
        throw new UnsupportedOperationException("TODO");
    }

    @GetMapping("/admin/complaints")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ComplaintResponse>> getComplaintsAdmin(
            @RequestParam(required = false) ComplaintStatus status) {
        throw new UnsupportedOperationException("TODO");
    }

    @PatchMapping("/admin/complaints/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComplaintResponse> updateComplaint(
            @PathVariable long id,
            @Valid @RequestBody UpdateComplaintRequest request) {
        throw new UnsupportedOperationException("TODO");
    }
}
