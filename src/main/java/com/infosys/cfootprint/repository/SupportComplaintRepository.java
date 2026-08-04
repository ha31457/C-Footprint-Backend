package com.infosys.cfootprint.repository;

import com.infosys.cfootprint.model.SupportComplaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SupportComplaintRepository extends JpaRepository<SupportComplaint, UUID> {
    List<SupportComplaint> findAllByOrderByCreatedAtDesc();
    List<SupportComplaint> findByEmailOrderByCreatedAtDesc(String email);
}
