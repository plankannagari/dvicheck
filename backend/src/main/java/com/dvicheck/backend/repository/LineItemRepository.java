package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.LineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LineItemRepository extends JpaRepository<LineItem, UUID> {

    List<LineItem> findByBillUserIdAndBillPurchaseDateBetween(UUID userId, LocalDate from, LocalDate to);

    // [ItemCategory, SUM(totalPrice)] per category, purchaseDate inclusive on both ends.
    // Aggregated in the DB so callers never load item rows (or names) just to total them.
    @Query("SELECT li.category, COALESCE(SUM(li.totalPrice), 0) FROM LineItem li "
        + "WHERE li.bill.user.id = :userId AND li.bill.purchaseDate BETWEEN :from AND :to "
        + "GROUP BY li.category")
    List<Object[]> sumByCategoryBetween(
        @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
