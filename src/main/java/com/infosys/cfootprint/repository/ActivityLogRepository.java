package com.infosys.cfootprint.repository;

import com.infosys.cfootprint.model.ActivityLog;
import com.infosys.cfootprint.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    List<ActivityLog> findByUserOrderByLogDateDesc(User user);

    List<ActivityLog> findByUserAndLogDate(User user, LocalDate date);

    List<ActivityLog> findByUserAndLogDateBetweenOrderByLogDateAsc(User user, LocalDate startDate, LocalDate endDate);

    List<ActivityLog> findByLogDate(LocalDate date);
}
