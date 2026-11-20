package com.dvicheck.backend.repository;

import com.dvicheck.backend.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRO RULE agreement (Day 38): the four copies — User.isProAt, findProUserIds, countPaid,
 * countBonusOnly — must agree.
 *
 * DB-backed: this project has no Testcontainers/H2, so (like BackendApplicationTests) this
 * runs against the configured Postgres (the dev DB locally). @DataJpaTest wraps each test in
 * a transaction that is ROLLED BACK, so the users created here never persist.
 * replace = NONE keeps the real Postgres instead of looking for an embedded database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProRuleAgreementTest {

    @Autowired UserRepository userRepository;

    @Test
    void allFourCopiesOfTheProRuleAgree() {
        Instant now = Instant.now();
        Instant future = now.plus(10, ChronoUnit.DAYS);
        Instant past = now.minus(10, ChronoUnit.DAYS);

        UUID paidActive = save("+15550038001", future, null);
        UUID paidExpiredBonusActive = save("+15550038002", past, future);
        UUID bonusOnly = save("+15550038003", null, future);
        UUID bothExpired = save("+15550038004", past, past);
        UUID bothNull = save("+15550038005", null, null);
        UUID paidAndBonus = save("+15550038006", future, future);
        userRepository.flush();

        List<UUID> proIds = userRepository.findProUserIds(now);
        Set<UUID> proSet = new HashSet<>(proIds);

        // The counts split the same population the list returns
        assertEquals(proIds.size(), userRepository.countPaid(now) + userRepository.countBonusOnly(now));

        // Each created case lands on the right side
        assertTrue(proSet.containsAll(List.of(paidActive, paidExpiredBonusActive, bonusOnly, paidAndBonus)));
        assertTrue(!proSet.contains(bothExpired) && !proSet.contains(bothNull));

        // And across EVERY user in the database, the query agrees with the Java rule
        for (User u : userRepository.findAll()) {
            assertEquals(u.isProAt(now), proSet.contains(u.getId()),
                "isProAt and findProUserIds disagree for user " + u.getId());
        }
    }

    private UUID save(String phone, Instant paidUntil, Instant bonusUntil) {
        return userRepository.save(User.builder()
            .phone(phone)
            .subscriptionExpiresAt(paidUntil)
            .referralProUntil(bonusUntil)
            .build()).getId();
    }
}
