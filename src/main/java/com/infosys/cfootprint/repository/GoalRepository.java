package com.infosys.cfootprint.repository;

import com.infosys.cfootprint.model.Goal;
import com.infosys.cfootprint.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByUserOrderByStartDateDesc(User user);

    Optional<Goal> findByUserAndStatus(User user, String status);

    List<Goal> findByUserAndStatusOrderByStartDateDesc(User user, String status);

    List<Goal> findByUserAndStatusAndPeriodType(User user, String status, String periodType);
}
