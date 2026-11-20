package com.dvicheck.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * HARD STOP (Day 38): with app.auth.dev-mode=true the OTP 123456 logs into ANY phone
 * number — including an admin's — so the app refuses to start in production with it on.
 * Throwing from @PostConstruct aborts context startup.
 *
 * "Production" = an active Spring profile named prod or production. NOTE: as of Day 38 no
 * prod profile exists yet and nothing sets SPRING_PROFILES_ACTIVE, so this only protects a
 * deploy that actually activates one.
 */
@Slf4j
@Component
public class DevModeSafetyCheck {

    static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production");

    private final boolean devMode;
    private final Environment environment;

    public DevModeSafetyCheck(@Value("${app.auth.dev-mode}") boolean devMode, Environment environment) {
        this.devMode = devMode;
        this.environment = environment;
    }

    @PostConstruct
    void verify() {
        String[] profiles = environment.getActiveProfiles();
        log.info("app.auth.dev-mode={} (active profiles: {})", devMode,
            profiles.length == 0 ? "none" : String.join(",", profiles));
        check(devMode, profiles);
    }

    static void check(boolean devMode, String[] activeProfiles) {
        boolean production = Arrays.stream(activeProfiles)
            .anyMatch(p -> PRODUCTION_PROFILES.contains(p.toLowerCase()));
        if (devMode && production) {
            throw new IllegalStateException("app.auth.dev-mode must be false in production");
        }
    }
}
