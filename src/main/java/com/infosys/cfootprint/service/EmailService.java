package com.infosys.cfootprint.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@cfootprint.com}")
    private String fromEmail;

    public void sendVerificationOtp(String toEmail, String otp) {
        String subject = "Verify your Carbon Footprint Tracker Account";
        String htmlContent = "<div style=\"font-family: Arial, sans-serif; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px; max-width: 600px;\">" +
                "<h2 style=\"color: #2b6cb0;\">Welcome to Carbon Footprint Tracker!</h2>" +
                "<p>Thank you for signing up. To complete your registration, please verify your email using the following 6-digit OTP:</p>" +
                "<div style=\"font-size: 24px; font-weight: bold; background: #edf2f7; padding: 15px; text-align: center; border-radius: 4px; letter-spacing: 5px; color: #2d3748; margin: 20px 0;\">" +
                otp +
                "</div>" +
                "<p>This OTP is valid for 10 minutes. If you did not sign up for this account, please ignore this email.</p>" +
                "<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;\" />" +
                "<p style=\"font-size: 12px; color: #718096;\">Carbon Footprint Monitoring and Sustainability Analytics</p>" +
                "</div>";

        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    public void sendPasswordResetOtp(String toEmail, String otp) {
        String subject = "Reset your Carbon Footprint Tracker Password";
        String htmlContent = "<div style=\"font-family: Arial, sans-serif; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px; max-width: 600px;\">" +
                "<h2 style=\"color: #c53030;\">Password Reset Request</h2>" +
                "<p>We received a request to reset your password. Please use the following 6-digit OTP to complete the reset process:</p>" +
                "<div style=\"font-size: 24px; font-weight: bold; background: #edf2f7; padding: 15px; text-align: center; border-radius: 4px; letter-spacing: 5px; color: #2d3748; margin: 20px 0;\">" +
                otp +
                "</div>" +
                "<p>This OTP is valid for 10 minutes. If you did not request a password reset, please secure your account immediately.</p>" +
                "<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;\" />" +
                "<p style=\"font-size: 12px; color: #718096;\">Carbon Footprint Monitoring and Sustainability Analytics</p>" +
                "</div>";

        sendHtmlEmail(toEmail, subject, htmlContent);
    }

    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info("SMTP email successfully sent to {}", to);
        } catch (MessagingException e) {
            logger.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Email sending failed: " + e.getMessage());
        }
    }
}
