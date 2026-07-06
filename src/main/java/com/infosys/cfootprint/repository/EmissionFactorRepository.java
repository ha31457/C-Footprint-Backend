package com.infosys.cfootprint.repository;

import com.infosys.cfootprint.model.EmissionFactor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmissionFactorRepository extends JpaRepository<EmissionFactor, UUID> {

    Optional<EmissionFactor> findByCategoryAndActivityType(String category, String activityType);
}
