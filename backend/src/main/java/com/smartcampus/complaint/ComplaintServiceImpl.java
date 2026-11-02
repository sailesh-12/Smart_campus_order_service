package com.smartcampus.complaint;

import com.smartcampus.audit.AuditService;
import com.smartcampus.common.exception.ConflictException;
import com.smartcampus.common.exception.ForbiddenException;
import com.smartcampus.common.exception.NotFoundException;
import com.smartcampus.complaint.dto.ComplaintResponse;
import com.smartcampus.complaint.dto.CreateComplaintRequest;
import com.smartcampus.complaint.dto.UpdateComplaintRequest;
import com.smartcampus.order.OrderService;
import com.smartcampus.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final OrderService orderService;
    private final AuditService auditService;
    private final Clock clock;

    /** A student may complain only about their own order, and only once it was actually placed and paid for. */
    @Override
    @Transactional
    public ComplaintResponse createComplaint(CreateComplaintRequest request, long userId) {
        if (orderService.studentIdOf(request.orderId()) != userId) {
            throw new ForbiddenException("You can only complain about your own orders");
        }
        if (orderService.statusOf(request.orderId()) == OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("INVALID_TRANSITION", "Pay for or cancel the order before raising a complaint");
        }
        Complaint complaint = Complaint.builder()
                .orderId(request.orderId())
                .userId(userId)
                .text(request.text().trim())
                .status(ComplaintStatus.OPEN)
                .createdAt(clock.instant())
                .build();
        return toResponse(complaintRepository.save(complaint));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplaintResponse> getMyComplaints(long userId) {
        return complaintRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplaintResponse> getComplaintsAdmin(ComplaintStatus status) {
        List<Complaint> complaints = status == null
                ? complaintRepository.findAllByOrderByCreatedAtDesc()
                : complaintRepository.findByStatusOrderByCreatedAtDesc(status);
        return complaints.stream().map(this::toResponse).toList();
    }

    /** OPEN -> IN_REVIEW -> RESOLVED / REJECTED. Closed complaints stay closed. Writes an admin_actions row. */
    @Override
    @Transactional
    public ComplaintResponse updateComplaintAdmin(long complaintId, UpdateComplaintRequest request, long adminUserId) {
        Complaint complaint = getComplaintEntity(complaintId);
        if (isClosed(complaint.getStatus())) {
            throw new ConflictException("INVALID_TRANSITION", "Complaint is already " + complaint.getStatus());
        }
        if (request.status() == ComplaintStatus.OPEN && complaint.getStatus() != ComplaintStatus.OPEN) {
            throw new ConflictException("INVALID_TRANSITION", "A complaint under review cannot go back to OPEN");
        }
        complaint.setStatus(request.status());
        if (request.adminNote() != null) {
            complaint.setAdminNote(request.adminNote());
        }
        if (isClosed(request.status())) {
            complaint.setResolvedAt(clock.instant());
        }
        complaint = complaintRepository.save(complaint);
        auditService.recordAction(adminUserId, "UPDATE_COMPLAINT", "COMPLAINT", complaintId,
                truncate(request.status() + (request.adminNote() == null ? "" : ": " + request.adminNote())));
        return toResponse(complaint);
    }

    @Override
    @Transactional
    public void linkRefundToComplaint(long complaintId, long refundPaymentId) {
        Complaint complaint = getComplaintEntity(complaintId);
        complaint.setRefundPaymentId(refundPaymentId);
        complaint.setStatus(ComplaintStatus.RESOLVED);
        if (complaint.getResolvedAt() == null) {
            complaint.setResolvedAt(clock.instant());
        }
        complaintRepository.save(complaint);
    }

    @Override
    @Transactional(readOnly = true)
    public Complaint getComplaintEntity(long complaintId) {
        return complaintRepository.findById(complaintId)
                .orElseThrow(() -> new NotFoundException("Complaint not found with id: " + complaintId));
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnresolved() {
        return complaintRepository.countByStatusIn(List.of(ComplaintStatus.OPEN, ComplaintStatus.IN_REVIEW));
    }

    private static boolean isClosed(ComplaintStatus status) {
        return status == ComplaintStatus.RESOLVED || status == ComplaintStatus.REJECTED;
    }

    private static String truncate(String s) {
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    private ComplaintResponse toResponse(Complaint c) {
        return new ComplaintResponse(c.getId(), c.getOrderId(), c.getUserId(), c.getText(), c.getStatus(),
                c.getAdminNote(), c.getCreatedAt(), c.getResolvedAt(), c.getRefundPaymentId());
    }
}
