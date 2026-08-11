package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.AdminCreateUserRequest;
import com.infosys.cfootprint.dto.UserResponse;
import com.infosys.cfootprint.exception.BadRequestException;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.RefreshTokenRepository;
import com.infosys.cfootprint.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(user -> !user.getRole().equals("ROLE_ADMIN")) // Only return non-admin users
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse createUser(AdminCreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken!");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already in use!");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .mobileNumber(request.getMobileNumber())
                .age(request.getAge())
                .gender(request.getGender())
                .role("ROLE_USER")
                .isEnabled(true)
                .isDisabled(false)
                .build();

        User saved = userRepository.save(user);
        return mapToResponse(saved);
    }

    @Autowired
    private com.infosys.cfootprint.repository.OrganizationRepository organizationRepository;

    @Transactional
    public void disableUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found with ID: " + userId));

        if (user.getRole().equals("ROLE_ADMIN")) {
            throw new BadRequestException("Cannot disable an administrator account.");
        }

        if (user.getRole().equals("ROLE_ORG_ADMIN") && user.getOrganization() != null) {
            com.infosys.cfootprint.model.Organization org = user.getOrganization();

            // Find all users belonging to this organization and convert them to normal users
            List<User> orgEmployees = userRepository.findAll().stream()
                    .filter(u -> u.getOrganization() != null && u.getOrganization().getId().equals(org.getId()))
                    .collect(Collectors.toList());

            for (User emp : orgEmployees) {
                emp.setOrganization(null);
                userRepository.save(emp);
            }

            // Remove org admin's link to org before deleting the org record
            user.setOrganization(null);
            userRepository.save(user);

            // Delete the organization
            organizationRepository.delete(org);
        }

        user.setDisabled(true);
        userRepository.save(user);

        // Force logout by deleting their refresh tokens
        refreshTokenRepository.deleteByUser(user);
    }

    @Transactional
    public UserResponse enableUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found with ID: " + userId));

        user.setDisabled(false);
        User saved = userRepository.save(user);
        return mapToResponse(saved);
    }

    private UserResponse mapToResponse(User user) {
        return userService.mapToResponse(user);
    }
}
