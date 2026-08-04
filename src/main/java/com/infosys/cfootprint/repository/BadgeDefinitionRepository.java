package com.infosys.cfootprint.repository;

import com.infosys.cfootprint.model.BadgeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BadgeDefinitionRepository extends JpaRepository<BadgeDefinition, UUID> {
    Optional<BadgeDefinition> findByBadgeType(String badgeType);
    boolean existsByBadgeType(String badgeType);
}
