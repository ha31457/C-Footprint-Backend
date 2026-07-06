package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.UserResponse;
import com.infosys.cfootprint.exception.BadRequestException;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.RefreshTokenRepository;
import com.infosys.cfootprint.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(user -> !user.getRole().equals("ROLE_ADMIN")) // Only return non-admin users
                .map(user -> UserResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .mobileNumber(user.getMobileNumber())
                        .age(user.getAge())
                        .gender(user.getGender())
                        .isEnabled(user.isEnabled())
                        .isDisabled(user.isDisabled())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void disableUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found with ID: " + userId));

        if (user.getRole().equals("ROLE_ADMIN")) {
            throw new BadRequestException("Cannot disable an administrator account.");
        }

        user.setDisabled(true);
        userRepository.save(user);

        // Force logout by deleting their refresh tokens
        refreshTokenRepository.deleteByUser(user);
    }
}
