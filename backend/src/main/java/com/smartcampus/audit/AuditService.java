package com.smartcampus.audit;

import java.util.List;

/**
 * Service for auditing administrative actions across the platform.
 * Relied upon by payment and admin management controllers.
 */
public interface AuditService {

    void recordAction(long adminId, String action, String targetType, long targetId, String note);

    List<AdminAction> getActions();
}
