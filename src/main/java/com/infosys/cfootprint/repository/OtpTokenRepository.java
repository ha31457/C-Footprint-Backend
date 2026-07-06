package com.infosys.cfootprint.repository;

import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, UUID> {

    Optional<OtpToken> findByOtpAndPurpose(String otp, String purpose);

    Optional<OtpToken> findByUserAndPurpose(User user, String purpose);

    @Modifying
    int deleteByUserAndPurpose(User user, String purpose);

    @Modifying
    void deleteByExpiryDateBefore(Instant now);
}
