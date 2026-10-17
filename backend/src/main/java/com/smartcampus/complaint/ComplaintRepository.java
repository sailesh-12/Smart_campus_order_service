package com.smartcampus.complaint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Complaint> findByOrderId(Long orderId);

    List<Complaint> findByStatusOrderByCreatedAtDesc(ComplaintStatus status);
}
