package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.ComplaintResponse;
import com.infosys.cfootprint.dto.CreateComplaintRequest;
import com.infosys.cfootprint.dto.ReplyComplaintRequest;
import com.infosys.cfootprint.exception.BadRequestException;
import com.infosys.cfootprint.model.SupportComplaint;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.repository.SupportComplaintRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SupportService {

    public static final List<String> CATEGORIES = List.of(
            "Technical Issue",
            "Account Issue",
            "Bug Report",
            "Feature Request",
            "Analytics Issue",
            "Email Issue",
            "Activity Logging Issue",
            "Other"
    );

    @Autowired
    private SupportComplaintRepository supportComplaintRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    public List<String> getCategories() {
        return CATEGORIES;
    }

    @Transactional
    public ComplaintResponse createComplaint(CreateComplaintRequest request) {
        if (!userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is not registered in our system.");
        }

        boolean isValidCategory = CATEGORIES.stream()
                .anyMatch(c -> c.equalsIgnoreCase(request.getCategory()));
        if (!isValidCategory) {
            throw new BadRequestException("Invalid support category selected.");
        }

        SupportComplaint complaint = SupportComplaint.builder()
                .email(request.getEmail())
                .category(request.getCategory())
                .complaintText(request.getComplaintText())
                .createdAt(LocalDateTime.now())
                .isResolved(false)
                .build();

        SupportComplaint saved = supportComplaintRepository.save(complaint);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> getAllComplaints() {
        return supportComplaintRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ComplaintResponse replyToComplaint(UUID id, ReplyComplaintRequest request) {
        SupportComplaint complaint = supportComplaintRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Complaint not found with ID: " + id));

        complaint.setReplyText(request.getReplyText());
        complaint.setResolved(true);
        complaint.setRepliedAt(LocalDateTime.now());

        SupportComplaint saved = supportComplaintRepository.save(complaint);

        // Send Email to User
        try {
            String subject = "Reply to your support complaint";
            String htmlContent = "<div style=\"font-family: Arial, sans-serif; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px; max-width: 600px;\">" +
                    "<h2 style=\"color: #2b6cb0;\">Support Complaint Reply</h2>" +
                    "<p>Hello,</p>" +
                    "<p>An administrator has replied to your support complaint:</p>" +
                    "<div style=\"background: #f7fafc; padding: 15px; border-left: 4px solid #cbd5e0; margin-bottom: 20px;\">" +
                    "<strong>Your Complaint:</strong><br/>" +
                    "<p style=\"margin-top: 5px; color: #4a5568;\">" + complaint.getComplaintText() + "</p>" +
                    "</div>" +
                    "<div style=\"background: #ebf8ff; padding: 15px; border-left: 4px solid #3182ce; margin-bottom: 20px;\">" +
                    "<strong>Admin Reply:</strong><br/>" +
                    "<p style=\"margin-top: 5px; color: #2b6cb0;\">" + request.getReplyText() + "</p>" +
                    "</div>" +
                    "<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;\" />" +
                    "<p style=\"font-size: 12px; color: #718096;\">Carbon Footprint Tracker - Support System</p>" +
                    "</div>";

            emailService.sendHtmlEmail(complaint.getEmail(), subject, htmlContent);
        } catch (Exception e) {
            System.err.println("Failed to send support reply email: " + e.getMessage());
        }

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> getComplaintsByEmail(String email) {
        return supportComplaintRepository.findByEmailOrderByCreatedAtDesc(email).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> getOrgComplaints(com.infosys.cfootprint.model.User orgAdmin) {
        if (orgAdmin.getOrganization() == null) {
            return java.util.Collections.emptyList();
        }
        UUID orgId = orgAdmin.getOrganization().getId();

        java.util.Set<String> employeeEmails = userRepository.findAll().stream()
                .filter(u -> u.getOrganization() != null && u.getOrganization().getId().equals(orgId))
                .map(u -> u.getEmail().toLowerCase())
                .collect(java.util.stream.Collectors.toSet());

        return supportComplaintRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> employeeEmails.contains(c.getEmail().toLowerCase()))
                .map(this::mapToResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public ComplaintResponse replyToComplaintOrg(UUID id, ReplyComplaintRequest request, com.infosys.cfootprint.model.User orgAdmin) {
        SupportComplaint complaint = supportComplaintRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Complaint not found with ID: " + id));

        com.infosys.cfootprint.model.User employee = userRepository.findByEmail(complaint.getEmail())
                .orElseThrow(() -> new BadRequestException("Complaint user not found"));

        if (orgAdmin.getOrganization() == null || employee.getOrganization() == null ||
                !employee.getOrganization().getId().equals(orgAdmin.getOrganization().getId())) {
            throw new BadRequestException("Access denied: This complaint does not belong to your organization.");
        }

        return replyToComplaint(id, request);
    }

    private ComplaintResponse mapToResponse(SupportComplaint complaint) {
        return ComplaintResponse.builder()
                .id(complaint.getId())
                .email(complaint.getEmail())
                .category(complaint.getCategory())
                .complaintText(complaint.getComplaintText())
                .replyText(complaint.getReplyText())
                .isResolved(complaint.isResolved())
                .createdAt(complaint.getCreatedAt())
                .repliedAt(complaint.getRepliedAt())
                .build();
    }
}
