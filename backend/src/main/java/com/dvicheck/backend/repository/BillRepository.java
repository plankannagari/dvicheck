package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.Bill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface BillRepository extends JpaRepository<Bill, UUID> {

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.user.id = :userId AND b.purchaseDate >= :fromDate")
    long countBillsSince(@Param("userId") UUID userId, @Param("fromDate") LocalDate fromDate);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Bill b WHERE b.user.id = :userId AND b.purchaseDate >= :fromDate")
    BigDecimal sumTotalSince(@Param("userId") UUID userId, @Param("fromDate") LocalDate fromDate);

    @Query("SELECT COALESCE(SUM(b.avoidableAmount), 0) FROM Bill b WHERE b.user.id = :userId AND b.purchaseDate >= :fromDate")
    BigDecimal sumAvoidableSince(@Param("userId") UUID userId, @Param("fromDate") LocalDate fromDate);

    @Query("SELECT b FROM Bill b WHERE b.user.id = :userId ORDER BY b.purchaseDate DESC, b.createdAt DESC")
    List<Bill> findRecentByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(b.totalAmount), 0) FROM Bill b WHERE b.user.id = :userId AND b.purchaseDate BETWEEN :from AND :to")
    BigDecimal sumTotalBetween(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT b FROM Bill b WHERE b.user.id = :userId "
        + "AND LOWER(b.storeName) LIKE LOWER(CONCAT('%', :search, '%')) "
        + "ORDER BY b.purchaseDate DESC, b.createdAt DESC")
    Page<Bill> findByUserIdAndStoreNameContainingIgnoreCase(
        @Param("userId") UUID userId, @Param("search") String search, Pageable pageable);

    @Query("SELECT COUNT(b) FROM Bill b WHERE b.user.id = :userId AND b.purchaseDate BETWEEN :from AND :to")
    long countBillsBetween(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(SUM(b.avoidableAmount), 0) FROM Bill b WHERE b.user.id = :userId AND b.purchaseDate BETWEEN :from AND :to")
    BigDecimal sumAvoidableBetween(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT b.storeName, COALESCE(SUM(b.totalAmount), 0), COUNT(b) FROM Bill b "
        + "WHERE b.user.id = :userId AND b.purchaseDate BETWEEN :from AND :to "
        + "GROUP BY b.storeName ORDER BY SUM(b.totalAmount) DESC")
    List<Object[]> findStoreTotalsBetween(
        @Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);
}
