package com.dvicheck.backend.service;

import com.dvicheck.backend.dto.UpdatePreferencesRequest;
import com.dvicheck.backend.dto.UserProfileDto;
import com.dvicheck.backend.exception.DvicheckException;
import com.dvicheck.backend.model.User;
import com.dvicheck.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(UUID userId) {
        User user = findById(userId);
        return toProfileDto(user);
    }

    @Transactional
    public UserProfileDto updatePreferences(UUID userId, UpdatePreferencesRequest req) {
        User user = findById(userId);

        if (req.householdSize() != null) {
            user.setHouseholdSize(req.householdSize());
        }
        if (req.currency() != null) {
            user.setCurrency(req.currency().toUpperCase());
        }
        if (req.notificationsEnabled() != null) {
            user.setNotificationsEnabled(req.notificationsEnabled());
        }

        User saved = userRepository.save(user);
        return toProfileDto(saved);
    }

    @Transactional
    public void savePushToken(UUID userId, String token) {
        User user = findById(userId);
        user.setPushToken(token);
        userRepository.save(user);
    }

    private UserProfileDto toProfileDto(User user) {
        return new UserProfileDto(
            user.getId(),
            user.getPhone(),
            user.getHouseholdSize(),
            user.getCurrency(),
            user.getNotificationsEnabled(),
            user.getCreatedAt()
        );
    }

    @Transactional
    public User findOrCreateByPhone(String phone) {
        return userRepository.findByPhone(phone)
                .orElseGet(() -> userRepository.save(User.builder().phone(phone).build()));
    }

    @Transactional(readOnly = true)
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> DvicheckException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public User findByPhone(String phone) {
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> DvicheckException.notFound("User"));
    }
}
