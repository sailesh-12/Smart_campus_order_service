package com.smartcampus.complaint;

import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.complaint.dto.ComplaintResponse;
import com.smartcampus.complaint.dto.CreateComplaintRequest;
import com.smartcampus.complaint.dto.UpdateComplaintRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintRepository complaintRepository;

    @Override
    @Transactional
    public ComplaintResponse createComplaint(CreateComplaintRequest request, long userId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplaintResponse> getMyComplaints(long userId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplaintResponse> getComplaintsAdmin(ComplaintStatus status) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public ComplaintResponse updateComplaintAdmin(long complaintId, UpdateComplaintRequest request, long adminUserId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    @Transactional
    public void linkRefundToComplaint(long complaintId, long refundPaymentId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new NotFoundException("Complaint not found with id: " + complaintId));
        complaint.setRefundPaymentId(refundPaymentId);
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setResolvedAt(Instant.now());
        complaintRepository.save(complaint);
    }

    @Override
    @Transactional(readOnly = true)
    public Complaint getComplaintEntity(long complaintId) {
        return complaintRepository.findById(complaintId)
                .orElseThrow(() -> new NotFoundException("Complaint not found with id: " + complaintId));
    }
}
