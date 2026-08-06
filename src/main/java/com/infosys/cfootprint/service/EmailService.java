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

    public void sendWelcomeEmail(String toEmail, String username) {
        String subject = "Welcome to Carbon Footprint Tracker! 🌱";
        String htmlContent = "<div style=\"font-family: Arial, sans-serif; padding: 25px; border: 1px solid #e2e8f0; border-radius: 12px; max-width: 600px; background-color: #f7fafc; color: #2d3748;\">" +
                "<div style=\"text-align: center; margin-bottom: 20px;\">" +
                "<h2 style=\"color: #2f855a; margin: 0; font-size: 26px;\">Welcome to Carbon Footprint Tracker! 🌱</h2>" +
                "</div>" +
                "<p style=\"font-size: 16px; line-height: 1.6;\">Hello <strong>" + username + "</strong>,</p>" +
                "<p style=\"font-size: 16px; line-height: 1.6;\">" +
                "Thank you for joining our community of sustainability champions! We are excited to support you on your journey to reduce your carbon footprint and make a positive impact on the planet." +
                "</p>" +
                "<div style=\"background-color: #ffffff; border-left: 4px solid #48bb78; padding: 15px; margin: 20px 0; border-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.05);\">" +
                "<h3 style=\"margin-top: 0; color: #2f855a;\">Here are your next steps:</h3>" +
                "<ul style=\"padding-left: 20px; line-height: 1.6; margin-bottom: 0;\">" +
                "<li><strong>Log Daily Activities:</strong> Track transport, food, energy, and waste emissions.</li>" +
                "<li><strong>Set Goals:</strong> Define reduction budgets to stay accountable.</li>" +
                "<li><strong>Earn Badges:</strong> Celebrate milestones as you build sustainable habits.</li>" +
                "<li><strong>Rise in Rank:</strong> Compete on the platform leaderboard for the lowest emission scores.</li>" +
                "</ul>" +
                "</div>" +
                "<p style=\"font-size: 16px; line-height: 1.6; text-align: center; margin-top: 25px;\">" +
                "Together, we can create a cleaner, greener future. Let's make every activity count!" +
                "</p>" +
                "<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 25px 0;\" />" +
                "<p style=\"font-size: 12px; color: #a0aec0; text-align: center; margin: 0;\">Carbon Footprint Monitoring and Sustainability Analytics</p>" +
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
