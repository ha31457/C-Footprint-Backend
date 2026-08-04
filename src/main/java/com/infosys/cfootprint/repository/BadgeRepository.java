package com.infosys.cfootprint.repository;

import com.infosys.cfootprint.model.Badge;
import com.infosys.cfootprint.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BadgeRepository extends JpaRepository<Badge, UUID> {
    List<Badge> findByUser(User user);
    boolean existsByUserAndBadgeType(User user, String badgeType);
}
