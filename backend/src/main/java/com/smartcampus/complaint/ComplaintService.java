package com.smartcampus.complaint;

import com.smartcampus.complaint.dto.ComplaintResponse;
import com.smartcampus.complaint.dto.CreateComplaintRequest;
import com.smartcampus.complaint.dto.UpdateComplaintRequest;

import java.util.List;

/**
 * Service managing student order complaints and dispute resolution.
 */
public interface ComplaintService {

    ComplaintResponse createComplaint(CreateComplaintRequest request, long userId);

    List<ComplaintResponse> getMyComplaints(long userId);

    List<ComplaintResponse> getComplaintsAdmin(ComplaintStatus status);

    ComplaintResponse updateComplaintAdmin(long complaintId, UpdateComplaintRequest request, long adminUserId);

    void linkRefundToComplaint(long complaintId, long refundPaymentId);

    Complaint getComplaintEntity(long complaintId);
}
