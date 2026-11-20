package com.dvicheck.backend.dto;

// Validated in AdminUserService (range comes from app.admin.max-grant-days)
public record AdminGrantProRequest(Integer days, String reason) {}
