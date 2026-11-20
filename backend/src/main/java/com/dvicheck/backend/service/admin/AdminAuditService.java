package com.dvicheck.backend.service.admin;

import com.dvicheck.backend.dto.AdminAuditEntryDto;
import com.dvicheck.backend.model.AdminAuditLog;
import com.dvicheck.backend.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Writes admin_audit_log rows (Day 38). Call it from INSIDE the admin action's own
 * @Transactional method so the action and its audit row commit or roll back together.
 *
 * `details` must NEVER contain a phone number or an amount (money, totals, prices).
 * Use ids, day counts, dates and the admin's short reason only. It is truncated to 500
 * chars (the column size) so a long reason can't fail the action.
 */
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    public static final String GRANT_PRO = "GRANT_PRO";
    public static final String REVOKE_BONUS_PRO = "REVOKE_BONUS_PRO";
    public static final String REGENERATE_COACH = "REGENERATE_COACH";
    public static final String USER_LOOKUP = "USER_LOOKUP";

    static final int MAX_DETAILS = 500;

    private final AdminAuditLogRepository repository;

    public AdminAuditLog record(UUID adminId, String action, UUID targetUserId, String details) {
        String safeDetails = details == null || details.length() <= MAX_DETAILS
            ? details
            : details.substring(0, MAX_DETAILS);
        return repository.save(AdminAuditLog.builder()
            .adminId(adminId)
            .action(action)
            .targetUserId(targetUserId)
            .details(safeDetails)
            .build());
    }

    // GET /api/admin/audit — newest first
    public List<AdminAuditEntryDto> recent() {
        return repository.findTop50ByOrderByCreatedAtDesc().stream()
            .map(e -> new AdminAuditEntryDto(e.getAction(), e.getTargetUserId(), e.getDetails(),
                e.getCreatedAt(), e.getAdminId()))
            .toList();
    }
}
