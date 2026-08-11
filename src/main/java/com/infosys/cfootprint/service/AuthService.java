package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.*;
import com.infosys.cfootprint.exception.BadRequestException;
import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.RefreshToken;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.security.CustomUserDetails;
import com.infosys.cfootprint.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.LockedException;

import com.infosys.cfootprint.util.AvatarUtils;
import com.infosys.cfootprint.model.Organization;
import com.infosys.cfootprint.repository.OrganizationRepository;
import java.time.LocalDateTime;

import java.util.Collections;
import java.util.UUID;

@Service
public class AuthService {

    @Value("${app.google.client-id:}")
    private String googleClientId;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private OtpTokenService otpTokenService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SystemSettingService systemSettingService;

    @Autowired
    private UserService userService;

    @Transactional
    public UserResponse registerUser(SignupRequest signupRequest) {
        if (userRepository.existsByUsername(signupRequest.getUsername())) {
            throw new BadRequestException("Username is already taken!");
        }

        if (userRepository.existsByEmail(signupRequest.getEmail())) {
            throw new BadRequestException("Email is already registered!");
        }

        Organization organization = null;
        String role = "ROLE_USER";

        if (signupRequest.isOrgAdmin()) {
            role = "ROLE_ORG_ADMIN";
        }

        // Create new user's account with determined role and isEnabled = false (waiting for OTP verify)
        User user = User.builder()
                .username(signupRequest.getUsername())
                .email(signupRequest.getEmail())
                .password(passwordEncoder.encode(signupRequest.getPassword()))
                .role(role)
                .mobileNumber(signupRequest.getMobileNumber())
                .age(signupRequest.getAge())
                .gender(signupRequest.getGender())
                .isEnabled(false)
                .organization(organization)
                .build();

        User savedUser = userRepository.save(user);

        // Generate and send verification OTP
        OtpToken otpToken = otpTokenService.generateOtp(savedUser, "EMAIL_VERIFICATION");
        emailService.sendVerificationOtp(savedUser.getEmail(), otpToken.getOtp());

        return userService.mapToResponse(savedUser);
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        OtpToken otpToken = otpTokenService.validateOtp(request.getOtp(), "EMAIL_VERIFICATION");
        User user = otpToken.getUser();

        if (!user.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new BadRequestException("OTP does not belong to the requested email address!");
        }

        user.setEnabled(true);
        userRepository.save(user);
        otpTokenService.deleteOtp(otpToken);
        emailService.sendWelcomeEmail(user.getEmail(), user.getUsername());
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("No user found with the email address: " + request.getEmail()));

        OtpToken otpToken = otpTokenService.generateOtp(user, "PASSWORD_RESET");
        emailService.sendPasswordResetOtp(user.getEmail(), otpToken.getOtp());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        OtpToken otpToken = otpTokenService.validateOtp(request.getOtp(), "PASSWORD_RESET");
        User user = otpToken.getUser();

        if (!user.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new BadRequestException("OTP does not belong to the requested email address!");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        otpTokenService.deleteOtp(otpToken);
    }

    @Transactional
    public JwtResponse authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsernameOrEmail(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        String jwt = jwtService.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());

        User user = userRepository.findById(userDetails.getId()).orElse(null);
        String avatar = user != null ? user.getAvatar() : "male-1";

        return JwtResponse.builder()
                .accessToken(jwt)
                .refreshToken(refreshToken.getToken())
                .id(userDetails.getId())
                .username(userDetails.getUsername())
                .email(userDetails.getEmail())
                .role(userDetails.getAuthorities().iterator().next().getAuthority())
                .avatar(avatar)
                .avatarUrl(AvatarUtils.getAvatarUrl(user))
                .organizationName(user != null && user.getOrganization() != null ? user.getOrganization().getName() : null)
                .isTempPassword(user != null && user.isTempPassword())
                .build();
    }

    @Transactional
    public JwtResponse authenticateGoogleUser(GoogleLoginRequest request) {
        if (!systemSettingService.isFeatureEnabled("google_signin_enabled")) {
            throw new BadRequestException("Google Sign-In is disabled by administrator.");
        }

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(request.getIdToken());
        } catch (Exception e) {
            throw new BadRequestException("Invalid Google ID token signature");
        }

        if (idToken == null) {
            throw new BadRequestException("Invalid or expired Google ID token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();

        if (email == null || email.isBlank()) {
            throw new BadRequestException("Google ID token payload does not contain an email address");
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            String username = email.split("@")[0];
            if (userRepository.existsByUsername(username)) {
                username = username + "_" + UUID.randomUUID().toString().substring(0, 4);
            }

            user = User.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role("ROLE_USER")
                    .isEnabled(true)
                    .isDisabled(false)
                    .build();

            user = userRepository.save(user);
        } else {
            if (user.isDisabled()) {
                throw new LockedException("Your account has been disabled by the admin.");
            }
            if (!user.isEnabled()) {
                user.setEnabled(true);
                user = userRepository.save(user);
            }
        }

        CustomUserDetails userDetails = CustomUserDetails.build(user);
        String jwt = jwtService.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());

        return JwtResponse.builder()
                .accessToken(jwt)
                .refreshToken(refreshToken.getToken())
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .avatar(user.getAvatar())
                .avatarUrl(AvatarUtils.getAvatarUrl(user))
                .organizationName(user.getOrganization() != null ? user.getOrganization().getName() : null)
                .isTempPassword(user.isTempPassword())
                .build();
    }

    @Transactional
    public UserResponse changeTemporaryPassword(UUID userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (!user.isTempPassword()) {
            throw new BadRequestException("This account does not have a temporary password set.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTempPassword(false);
        user.setEnabled(true); // Account is fully active now!
        User saved = userRepository.save(user);

        return userService.mapToResponse(saved);
    }
}
