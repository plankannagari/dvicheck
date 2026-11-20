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

    // PRO RULE: keep in sync with User.isProAt (paid OR referral_pro_until). Four copies - change all or none.
    // (The four: User.isProAt, findProUserIds, countPaid, countBonusOnly.)
    // Pushed into the query so a scheduled job doesn't load every user to filter in Java.
    // NULL > :now is unknown in SQL, so a null column simply doesn't match (same as Java).
    @Query("select u.id from User u where u.subscriptionExpiresAt > :now or u.referralProUntil > :now")
    List<UUID> findProUserIds(@Param("now") Instant now);

    // PRO RULE: keep in sync with User.isProAt (paid OR referral_pro_until). Four copies - change all or none.
    // Paid = active RevenueCat subscription (whether or not referral time is also active)
    @Query("select count(u) from User u where u.subscriptionExpiresAt > :now")
    long countPaid(@Param("now") Instant now);

    // PRO RULE: keep in sync with User.isProAt (paid OR referral_pro_until). Four copies - change all or none.
    // Bonus only = referral/support days with no active paid subscription.
    // countPaid + countBonusOnly == findProUserIds(now).size() (checked in tests only).
    @Query("select count(u) from User u where (u.subscriptionExpiresAt is null or u.subscriptionExpiresAt <= :now) "
         + "and u.referralProUntil > :now")
    long countBonusOnly(@Param("now") Instant now);

    // Admin overview (Day 38): one row of aggregates — [total, new7d, new30d, onboarded,
    // withPushToken, notificationsEnabled]. List<Object[]> avoids Spring Data's nested-array
    // quirk for single-row multi-column results.
    @Query("select count(u), "
         + "coalesce(sum(case when u.createdAt >= :since7 then 1 else 0 end), 0), "
         + "coalesce(sum(case when u.createdAt >= :since30 then 1 else 0 end), 0), "
         + "coalesce(sum(case when u.onboardingCompleted = true then 1 else 0 end), 0), "
         + "coalesce(sum(case when u.pushToken is not null and u.pushToken <> '' then 1 else 0 end), 0), "
         + "coalesce(sum(case when u.notificationsEnabled = true then 1 else 0 end), 0) "
         + "from User u")
    List<Object[]> userAggregates(@Param("since7") Instant since7, @Param("since30") Instant since30);

    // [date, count] per UTC day. users.created_at is TIMESTAMP WITHOUT TIME ZONE holding UTC
    // wall-clock time (Hibernate writes Instants as UTC), so cast(... as date) is the UTC day
    // and the bound is a UTC LocalDateTime (no JDBC time-zone conversion on that column type).
    @Query(value = "select cast(created_at as date) as day, count(*) from users "
                 + "where created_at >= :since group by 1", nativeQuery = true)
    List<Object[]> countSignupsPerDay(@Param("since") java.time.LocalDateTime since);

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
