package com.smartcampus.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AdminActionRepository adminActionRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAction(long adminId, String action, String targetType, long targetId, String note) {
        AdminAction adminAction = AdminAction.builder()
                .adminId(adminId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .note(note)
                .createdAt(Instant.now())
                .build();
        adminActionRepository.save(adminAction);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminAction> getActions() {
        return adminActionRepository.findAllByOrderByCreatedAtDesc();
    }
}
