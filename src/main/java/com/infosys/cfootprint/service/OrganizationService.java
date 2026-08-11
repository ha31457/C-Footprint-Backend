package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.CreateOrgAdminRequest;
import com.infosys.cfootprint.dto.CreateOrganizationRequest;
import com.infosys.cfootprint.dto.UserResponse;
import com.infosys.cfootprint.exception.BadRequestException;
import com.infosys.cfootprint.model.Organization;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.OrganizationRepository;
import com.infosys.cfootprint.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Transactional
    public Organization createOrganization(CreateOrganizationRequest request) {
        if (organizationRepository.existsByName(request.getName())) {
            throw new BadRequestException("Organization name already exists!");
        }

        Organization org = Organization.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .createdAt(LocalDateTime.now())
                .build();

        return organizationRepository.save(org);
    }

    @Transactional(readOnly = true)
    public List<Organization> getAllOrganizations() {
        return organizationRepository.findAll();
    }

    @Transactional
    public UserResponse createOrgAdmin(UUID orgId, CreateOrgAdminRequest request) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new BadRequestException("Organization not found"));

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken!");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered!");
        }

        User orgAdmin = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_ORG_ADMIN")
                .isEnabled(true)
                .isDisabled(false)
                .organization(org)
                .build();

        User saved = userRepository.save(orgAdmin);
        return userService.mapToResponse(saved);
    }
}
