package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.LineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LineItemRepository extends JpaRepository<LineItem, UUID> {

    List<LineItem> findByBillUserIdAndBillPurchaseDateBetween(UUID userId, LocalDate from, LocalDate to);
}
