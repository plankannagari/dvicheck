package com.dvicheck.backend.dto;

import java.time.LocalDate;

public record UpdateBillRequest(
        String storeName,
        String billType,
        LocalDate purchaseDate
) {}
