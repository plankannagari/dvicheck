package com.dvicheck.backend.dto;

// POST /api/admin/users/lookup body — avoids URL-encoding the '+' of an E.164 number
public record AdminLookupRequest(String phone) {}
