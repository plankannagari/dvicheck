package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    List<User> findByPushTokenIsNotNullAndNotificationsEnabledTrue();

    // Same rule as User.isProAt(now) — paid OR referral time (both Instant / TIMESTAMPTZ),
    // pushed into the query so a scheduled job doesn't load every user to filter in Java.
    // NULL > :now is unknown in SQL, so a null column simply doesn't match (same as Java).
    @Query("select u.id from User u where u.subscriptionExpiresAt > :now or u.referralProUntil > :now")
    List<UUID> findProUserIds(@Param("now") Instant now);

    // Codes are stored normalised (uppercase, no spaces/hyphens) — normalise input first
    Optional<User> findByReferralCode(String code);

    // Sets the invite code only if the user has none yet. A targeted UPDATE rather than
    // save(user): save() writes every column, so it could overwrite a concurrent webhook's
    // subscription_expires_at or a preferences save, and two concurrent first requests
    // would each store a different code. Returns 0 if a code was already set.
    // A collision with another user's code throws DataIntegrityViolationException.
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.referralCode = :code where u.id = :id and u.referralCode is null")
    int setReferralCodeIfAbsent(@Param("id") UUID id, @Param("code") String code);

    // SELECT ... FOR UPDATE: serialises referral rewards per user (cap checks, Pro-days
    // extension). Only meaningful inside a transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
}
