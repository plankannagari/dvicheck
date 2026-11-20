package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    Optional<Referral> findByRefereeId(UUID refereeId);

    boolean existsByRefereeId(UUID refereeId);

    // Reciprocal check: does the reverse relationship already exist?
    boolean existsByReferrerIdAndRefereeId(UUID referrerId, UUID refereeId);

    long countByReferrerIdAndStatus(UUID referrerId, String status);

    // Compare-and-set: returns 1 only for the caller that actually moved PENDING ->
    // QUALIFIED, so two simultaneous scans can't both reward. Must run inside a
    // transaction (the caller's @Transactional).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Referral r set r.status = 'QUALIFIED', r.qualifiedAt = :now where r.id = :id and r.status = 'PENDING'")
    int markQualified(@Param("id") UUID id, @Param("now") Instant now);
}
