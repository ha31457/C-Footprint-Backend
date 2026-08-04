package com.infosys.cfootprint.service;

import com.infosys.cfootprint.exception.BadRequestException;
import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.OtpTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class OtpTokenService {

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    private final SecureRandom random = new SecureRandom();

    @Transactional
    public OtpToken generateOtp(User user, String purpose) {
        // Delete any existing OTP for this user and purpose
        otpTokenRepository.deleteByUserAndPurpose(user, purpose);

        String otp = String.format("%06d", random.nextInt(900000) + 100000);

        OtpToken otpToken = OtpToken.builder()
                .otp(otp)
                .purpose(purpose)
                .user(user)
                .expiryDate(Instant.now().plusSeconds(600)) // 10 minutes expiration
                .build();

        return otpTokenRepository.save(otpToken);
    }

    public OtpToken validateOtp(String otp, String purpose) {
        OtpToken otpToken = otpTokenRepository.findByOtpAndPurpose(otp, purpose)
                .orElseThrow(() -> new BadRequestException("Invalid OTP code!"));

        if (otpToken.getExpiryDate().isBefore(Instant.now())) {
            otpTokenRepository.delete(otpToken);
            throw new BadRequestException("OTP code has expired!");
        }

        return otpToken;
    }

    @Transactional
    public void deleteOtp(OtpToken otpToken) {
        otpTokenRepository.delete(otpToken);
    }

    @Transactional
    public void cleanExpiredOtps() {
        otpTokenRepository.deleteByExpiryDateBefore(Instant.now());
    }
}
