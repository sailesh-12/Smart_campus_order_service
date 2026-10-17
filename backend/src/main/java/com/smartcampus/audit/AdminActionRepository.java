package com.smartcampus.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    List<AdminAction> findByTargetTypeAndTargetId(String targetType, Long targetId);

    List<AdminAction> findAllByOrderByCreatedAtDesc();
}
