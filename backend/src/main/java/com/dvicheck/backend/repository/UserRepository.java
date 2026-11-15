package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
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

    // Same rule as User.isPro() (subscriptionExpiresAt is an Instant, column TIMESTAMPTZ),
    // pushed into the query so a scheduled job doesn't load every user to filter in Java
    @Query("select u.id from User u where u.subscriptionExpiresAt > :now")
    List<UUID> findProUserIds(@Param("now") Instant now);
}
