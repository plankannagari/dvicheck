package com.dvicheck.backend.dto;

import java.util.List;

// Null fields mean "leave unchanged"; validation lives in BudgetAlertService.updateSettings()
public record UpdateBudgetAlertSettingsRequest(
        List<Integer> thresholds,
        Boolean paceAlertsEnabled
) {}
