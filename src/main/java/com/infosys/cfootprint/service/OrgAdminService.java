package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.*;
import com.infosys.cfootprint.exception.BadRequestException;
import com.infosys.cfootprint.model.*;
import com.infosys.cfootprint.repository.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrgAdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private BadgeRepository badgeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ActivityLogService activityLogService;

    @Transactional
    public UserResponse createEmployee(User orgAdmin, OrgCreateEmployeeRequest request) {
        if (orgAdmin.getOrganization() == null) {
            throw new BadRequestException("You are not associated with any organization.");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken!");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered!");
        }

        User employee = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getTemporaryPassword()))
                .role("ROLE_USER")
                .isEnabled(true) // Set to true so they can log in to reset their password
                .isDisabled(false)
                .isTempPassword(true)
                .organization(orgAdmin.getOrganization())
                .build();

        User saved = userRepository.save(employee);

        // Send temporary password email to the employee
        try {
            emailService.sendTempPasswordEmail(saved.getEmail(), saved.getUsername(), request.getTemporaryPassword(), orgAdmin.getOrganization().getName());
        } catch (Exception e) {
            System.err.println("Failed to send temp password email: " + e.getMessage());
        }

        return userService.mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getEmployees(User orgAdmin) {
        if (orgAdmin.getOrganization() == null) {
            return Collections.emptyList();
        }
        return userRepository.findAll().stream()
                .filter(u -> u.getOrganization() != null && u.getOrganization().getId().equals(orgAdmin.getOrganization().getId()))
                .filter(u -> !u.getRole().equals("ROLE_ORG_ADMIN"))
                .map(userService::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ActivityLogResponse> getEmployeeActivities(User orgAdmin, UUID employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new BadRequestException("Employee not found"));

        if (orgAdmin.getOrganization() == null || employee.getOrganization() == null ||
                !employee.getOrganization().getId().equals(orgAdmin.getOrganization().getId())) {
            throw new BadRequestException("Access denied: This user does not belong to your organization.");
        }

        return activityLogRepository.findByUserOrderByLogDateDesc(employee).stream()
                .map(log -> ActivityLogResponse.builder()
                        .id(log.getId())
                        .category(log.getCategory())
                        .activityType(log.getActivityType())
                        .quantity(log.getQuantity())
                        .unit(log.getUnit())
                        .co2Emission(Math.round(log.getCo2Emission() * 100.0) / 100.0)
                        .logDate(log.getLogDate())
                        .imageProofId(log.getImageProofId())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOrgSummary(User orgAdmin) {
        if (orgAdmin.getOrganization() == null) {
            throw new BadRequestException("No organization associated.");
        }

        UUID orgId = orgAdmin.getOrganization().getId();

        List<User> employees = userRepository.findAll().stream()
                .filter(u -> u.getOrganization() != null && u.getOrganization().getId().equals(orgId))
                .collect(Collectors.toList());

        List<UUID> employeeIds = employees.stream().map(User::getId).collect(Collectors.toList());

        List<ActivityLog> logs = activityLogRepository.findAll().stream()
                .filter(log -> employeeIds.contains(log.getUser().getId()))
                .collect(Collectors.toList());

        List<Goal> goals = goalRepository.findAll().stream()
                .filter(g -> employeeIds.contains(g.getUser().getId()))
                .collect(Collectors.toList());

        long totalEmployees = employees.size();
        long activeEmployees = logs.stream().map(log -> log.getUser().getId()).distinct().count();
        long totalLogs = logs.size();
        double totalCo2 = logs.stream().mapToDouble(ActivityLog::getCo2Emission).sum();
        double averageCo2PerEmployee = totalEmployees > 0 ? (totalCo2 / totalEmployees) : 0.0;

        long completedGoals = goals.stream().filter(g -> "COMPLETED".equalsIgnoreCase(g.getStatus())).count();
        double goalsSuccessRate = goals.isEmpty() ? 0.0 : ((double) completedGoals / goals.size()) * 100.0;

        long badgesAwarded = 0;
        for (User emp : employees) {
            badgesAwarded += badgeRepository.findByUser(emp).size();
        }

        Map<String, Double> categoryBreakdown = logs.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getCategory,
                        Collectors.summingDouble(ActivityLog::getCo2Emission)
                ));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("organizationName", orgAdmin.getOrganization().getName());
        summary.put("totalEmployees", totalEmployees);
        summary.put("activeEmployees", activeEmployees);
        summary.put("totalLogs", totalLogs);
        summary.put("totalCo2", Math.round(totalCo2 * 100.0) / 100.0);
        summary.put("averageCo2PerEmployee", Math.round(averageCo2PerEmployee * 100.0) / 100.0);
        summary.put("averageCo2PerUser", Math.round(averageCo2PerEmployee * 100.0) / 100.0);
        summary.put("badgesAwarded", badgesAwarded);
        summary.put("totalGoals", (long) goals.size());
        summary.put("completedGoals", completedGoals);
        summary.put("goalsSuccessRate", Math.round(goalsSuccessRate * 100.0) / 100.0);
        summary.put("categoryBreakdown", categoryBreakdown);

        return summary;
    }

    public String generateOrgCsv(User orgAdmin) {
        Map<String, Object> data = getOrgSummary(orgAdmin);
        StringBuilder csv = new StringBuilder();

        csv.append("Carbon Footprint Organization Report - ").append(data.get("organizationName")).append("\n");
        csv.append("Generated On: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");

        csv.append("Metric,Value\n");
        csv.append("Total Employees,").append(data.get("totalEmployees")).append("\n");
        csv.append("Active Employees,").append(data.get("activeEmployees")).append("\n");
        csv.append("Total Activity Logs,").append(data.get("totalLogs")).append("\n");
        csv.append("Total CO2 Footprint (kg),").append(data.get("totalCo2")).append("\n");
        csv.append("Average Footprint Per Employee (kg),").append(data.get("averageCo2PerEmployee")).append("\n");
        csv.append("Total Goals set,").append(data.get("totalGoals")).append("\n");
        csv.append("Completed Goals,").append(data.get("completedGoals")).append("\n");
        csv.append("Goals Success Rate (%),").append(data.get("goalsSuccessRate")).append("\n\n");

        csv.append("CO2 Emission Category Breakdown\n");
        csv.append("Category,Total CO2 Emission (kg)\n");
        Map<String, Double> breakdown = (Map<String, Double>) data.get("categoryBreakdown");
        for (Map.Entry<String, Double> entry : breakdown.entrySet()) {
            csv.append(entry.getKey()).append(",").append(Math.round(entry.getValue() * 100.0) / 100.0).append("\n");
        }

        return csv.toString();
    }

    public byte[] generateOrgPdf(User orgAdmin) {
        Map<String, Object> data = getOrgSummary(orgAdmin);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        com.lowagie.text.Document document = new com.lowagie.text.Document(PageSize.A4, 36, 36, 54, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Document Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Font.BOLD, java.awt.Color.DARK_GRAY);
            Paragraph title = new Paragraph("C-Footprint Organization Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(5);
            document.add(title);

            Font subFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.BOLD, new java.awt.Color(47, 133, 90));
            Paragraph subtitle = new Paragraph((String) data.get("organizationName"), subFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(15);
            document.add(subtitle);

            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.ITALIC, java.awt.Color.GRAY);
            Paragraph meta = new Paragraph("Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), metaFont);
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(25);
            document.add(meta);

            // Table of metrics
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(90);
            table.setSpacingAfter(20);

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD, java.awt.Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.NORMAL, java.awt.Color.BLACK);

            PdfPCell h1 = new PdfPCell(new Phrase("Employee Analytics Metric", headerFont));
            h1.setBackgroundColor(new java.awt.Color(47, 133, 90));
            h1.setPadding(8);
            h1.setHorizontalAlignment(Element.ALIGN_LEFT);

            PdfPCell h2 = new PdfPCell(new Phrase("Value", headerFont));
            h2.setBackgroundColor(new java.awt.Color(47, 133, 90));
            h2.setPadding(8);
            h2.setHorizontalAlignment(Element.ALIGN_CENTER);

            table.addCell(h1);
            table.addCell(h2);

            addTableCell(table, "Total Employees", String.valueOf(data.get("totalEmployees")), cellFont);
            addTableCell(table, "Active Employees", String.valueOf(data.get("activeEmployees")), cellFont);
            addTableCell(table, "Total Activity Logs", String.valueOf(data.get("totalLogs")), cellFont);
            addTableCell(table, "Total CO2 Emissions (kg)", String.valueOf(data.get("totalCo2")), cellFont);
            addTableCell(table, "Average Footprint per Employee (kg)", String.valueOf(data.get("averageCo2PerEmployee")), cellFont);
            addTableCell(table, "Total Goals set", String.valueOf(data.get("totalGoals")), cellFont);
            addTableCell(table, "Completed Goals", String.valueOf(data.get("completedGoals")), cellFont);
            addTableCell(table, "Goals Success Rate (%)", data.get("goalsSuccessRate") + "%", cellFont);

            document.add(table);

            // Category breakdown Table
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Font.BOLD, java.awt.Color.DARK_GRAY);
            Paragraph sectionTitle = new Paragraph("CO2 Emission Category Breakdown", sectionFont);
            sectionTitle.setSpacingBefore(15);
            sectionTitle.setSpacingAfter(10);
            document.add(sectionTitle);

            PdfPTable catTable = new PdfPTable(2);
            catTable.setWidthPercentage(90);

            PdfPCell c1 = new PdfPCell(new Phrase("Activity Category", headerFont));
            c1.setBackgroundColor(new java.awt.Color(49, 130, 206));
            c1.setPadding(8);
            c1.setHorizontalAlignment(Element.ALIGN_LEFT);

            PdfPCell c2 = new PdfPCell(new Phrase("Total CO2 Emitted (kg)", headerFont));
            c2.setBackgroundColor(new java.awt.Color(49, 130, 206));
            c2.setPadding(8);
            c2.setHorizontalAlignment(Element.ALIGN_CENTER);

            catTable.addCell(c1);
            catTable.addCell(c2);

            Map<String, Double> breakdown = (Map<String, Double>) data.get("categoryBreakdown");
            for (Map.Entry<String, Double> entry : breakdown.entrySet()) {
                addTableCell(catTable, entry.getKey(), Math.round(entry.getValue() * 100.0) / 100.0 + " kg", cellFont);
            }

            if (breakdown.isEmpty()) {
                addTableCell(catTable, "No category logs yet", "0.00 kg", cellFont);
            }

            document.add(catTable);
            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }

        return out.toByteArray();
    }

    private void addTableCell(PdfPTable table, String label, String value, Font font) {
        PdfPCell cell1 = new PdfPCell(new Phrase(label, font));
        cell1.setPadding(6);
        cell1.setHorizontalAlignment(Element.ALIGN_LEFT);

        PdfPCell cell2 = new PdfPCell(new Phrase(value, font));
        cell2.setPadding(6);
        cell2.setHorizontalAlignment(Element.ALIGN_CENTER);

        table.addCell(cell1);
        table.addCell(cell2);
    }

    public byte[] generateOrgWord(User orgAdmin) {
        Map<String, Object> data = getOrgSummary(orgAdmin);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph titleParagraph = doc.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText("Carbon Footprint Organization Report");
            titleRun.setFontSize(18);
            titleRun.setBold(true);
            titleRun.setFontFamily("Arial");
            titleRun.setColor("2F855A");

            XWPFParagraph subParagraph = doc.createParagraph();
            subParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun subRun = subParagraph.createRun();
            subRun.setText((String) data.get("organizationName"));
            subRun.setFontSize(14);
            subRun.setBold(true);
            subRun.setColor("2B6CB0");

            XWPFParagraph metaParagraph = doc.createParagraph();
            metaParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun metaRun = metaParagraph.createRun();
            metaRun.setText("Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            metaRun.setFontSize(10);
            metaRun.setItalic(true);
            metaRun.setColor("718096");

            doc.createParagraph();

            XWPFParagraph heading = doc.createParagraph();
            XWPFRun headingRun = heading.createRun();
            headingRun.setText("Key Employee Sustainability Metrics");
            headingRun.setFontSize(14);
            headingRun.setBold(true);
            headingRun.setColor("2D3748");

            XWPFTable table = doc.createTable();
            table.setWidth("100%");

            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("Metric Description");
            headerRow.getCell(0).setColor("2F855A");
            headerRow.createCell().setText("Value");
            headerRow.getCell(1).setColor("2F855A");

            addWordRow(table, "Total Employees", String.valueOf(data.get("totalEmployees")));
            addWordRow(table, "Active Employees", String.valueOf(data.get("activeEmployees")));
            addWordRow(table, "Total Activity Logs", String.valueOf(data.get("totalLogs")));
            addWordRow(table, "Total CO2 Emissions", data.get("totalCo2") + " kg");
            addWordRow(table, "Average Footprint per Employee", data.get("averageCo2PerEmployee") + " kg");
            addWordRow(table, "Total Goals set", String.valueOf(data.get("totalGoals")));
            addWordRow(table, "Completed Goals", String.valueOf(data.get("completedGoals")));
            addWordRow(table, "Goals Success Rate", data.get("goalsSuccessRate") + "%");

            doc.createParagraph();

            XWPFParagraph breakHeading = doc.createParagraph();
            XWPFRun breakRun = breakHeading.createRun();
            breakRun.setText("CO2 Emission Category Breakdown");
            breakRun.setFontSize(14);
            breakRun.setBold(true);
            breakRun.setColor("2D3748");

            XWPFTable catTable = doc.createTable();
            catTable.setWidth("100%");

            XWPFTableRow catHeader = catTable.getRow(0);
            catHeader.getCell(0).setText("Activity Category");
            catHeader.getCell(0).setColor("3182CE");
            catHeader.createCell().setText("Total CO2 Emitted (kg)");
            catHeader.getCell(1).setColor("3182CE");

            Map<String, Double> breakdown = (Map<String, Double>) data.get("categoryBreakdown");
            for (Map.Entry<String, Double> entry : breakdown.entrySet()) {
                addWordRow(catTable, entry.getKey(), Math.round(entry.getValue() * 100.0) / 100.0 + " kg");
            }

            doc.write(out);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Word doc: " + e.getMessage(), e);
        }

        return out.toByteArray();
    }

    private void addWordRow(XWPFTable table, String metric, String value) {
        XWPFTableRow row = table.createRow();
        row.getCell(0).setText(metric);
        row.getCell(1).setText(value);
    }

    @Transactional
    public UserResponse setupOrganization(User orgAdmin, SetupOrganizationRequest request) {
        if (orgAdmin.getOrganization() != null) {
            throw new BadRequestException("Organization is already set up for this administrator.");
        }

        String orgName = request.getOrganizationName().trim();
        if (organizationRepository.existsByName(orgName)) {
            throw new BadRequestException("Organization name already exists! Please choose a different name.");
        }

        Organization organization = Organization.builder()
                .id(UUID.randomUUID())
                .name(orgName)
                .industry(request.getIndustry().trim())
                .address(request.getAddress().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .createdAt(LocalDateTime.now())
                .build();

        organization = organizationRepository.save(organization);
        orgAdmin.setOrganization(organization);
        User saved = userRepository.save(orgAdmin);

        return userService.mapToResponse(saved);
    }

    @Transactional
    public void disableEmployee(User orgAdmin, UUID employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new BadRequestException("Employee not found"));

        if (orgAdmin.getOrganization() == null || employee.getOrganization() == null ||
                !employee.getOrganization().getId().equals(orgAdmin.getOrganization().getId())) {
            throw new BadRequestException("Access denied: This user does not belong to your organization.");
        }

        if (employee.getRole().equals("ROLE_ORG_ADMIN") || employee.getRole().equals("ROLE_ADMIN")) {
            throw new BadRequestException("Cannot disable an administrator account.");
        }

        employee.setDisabled(true);
        userRepository.save(employee);

        // Force logout by deleting refresh tokens
        refreshTokenRepository.deleteByUser(employee);
    }

    @Transactional
    public UserResponse enableEmployee(User orgAdmin, UUID employeeId) {
        User employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new BadRequestException("Employee not found"));

        if (orgAdmin.getOrganization() == null || employee.getOrganization() == null ||
                !employee.getOrganization().getId().equals(orgAdmin.getOrganization().getId())) {
            throw new BadRequestException("Access denied: This user does not belong to your organization.");
        }

        employee.setDisabled(false);
        User saved = userRepository.save(employee);
        return userService.mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public AdminUserAnalyticsResponse getOrgUserAnalytics(User orgAdmin) {
        UUID orgId = orgAdmin.getOrganization().getId();
        List<User> employees = userRepository.findAll().stream()
                .filter(u -> u.getOrganization() != null && u.getOrganization().getId().equals(orgId))
                .filter(u -> !u.getRole().equals("ROLE_ORG_ADMIN"))
                .collect(Collectors.toList());

        long total = employees.size();
        long enabled = employees.stream().filter(User::isEnabled).count();
        long disabled = total - enabled;

        return AdminUserAnalyticsResponse.builder()
                .totalUsers(total)
                .enabledUsers(enabled)
                .disabledUsers(disabled)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminActivityAnalyticsResponse getOrgActivityAnalytics(User orgAdmin, String range) {
        java.time.LocalDate today = java.time.LocalDate.now();
        UUID orgId = orgAdmin.getOrganization().getId();

        List<User> employees = userRepository.findAll().stream()
                .filter(u -> u.getOrganization() != null && u.getOrganization().getId().equals(orgId))
                .collect(Collectors.toList());
        List<UUID> employeeIds = employees.stream().map(User::getId).collect(Collectors.toList());

        List<ActivityLog> orgLogs = activityLogRepository.findAll().stream()
                .filter(log -> employeeIds.contains(log.getUser().getId()))
                .collect(Collectors.toList());

        long totalLogs = orgLogs.size();
        long logsToday = orgLogs.stream().filter(log -> log.getLogDate().equals(today)).count();

        java.time.LocalDate startDate;
        switch (range.toLowerCase()) {
            case "daily":
                startDate = today;
                break;
            case "weekly":
                startDate = today.minusDays(6);
                break;
            case "monthly":
                startDate = today.minusDays(29);
                break;
            case "yearly":
                startDate = today.minusYears(1);
                break;
            default:
                startDate = null;
        }

        List<ActivityLog> filteredLogsForBreakdown = orgLogs;
        if (startDate != null) {
            filteredLogsForBreakdown = orgLogs.stream()
                    .filter(log -> !log.getLogDate().isBefore(startDate) && !log.getLogDate().isAfter(today))
                    .collect(Collectors.toList());
        }

        double rangeTotalCo2 = filteredLogsForBreakdown.stream().mapToDouble(ActivityLog::getCo2Emission).sum();

        Map<String, Double> categorySums = filteredLogsForBreakdown.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getCategory,
                        Collectors.summingDouble(ActivityLog::getCo2Emission)
                ));

        List<CategoryBreakdownDTO> breakdown = new ArrayList<>();
        List<String> categories = Arrays.asList("transport", "electricity", "food", "shopping", "waste", "water", "heating", "other");
        for (String cat : categories) {
            double co2 = categorySums.getOrDefault(cat, 0.0);
            double pct = rangeTotalCo2 > 0 ? (co2 / rangeTotalCo2) * 100 : 0.0;
            breakdown.add(CategoryBreakdownDTO.builder()
                    .category(cat)
                    .co2Emission(Math.round(co2 * 100.0) / 100.0)
                    .percentage(Math.round(pct * 100.0) / 100.0)
                    .build());
        }

        List<TrendDTO> trend = activityLogService.calculateTrend(orgLogs, range);

        return AdminActivityAnalyticsResponse.builder()
                .totalLogs(totalLogs)
                .logsLoggedToday(logsToday)
                .totalCo2EmissionKgs(Math.round(rangeTotalCo2 * 100.0) / 100.0)
                .categoryBreakdown(breakdown)
                .trend(trend)
                .build();
    }

    @Transactional(readOnly = true)
    public AdminFilteredActivitiesResponse getOrgActivities(User orgAdmin, String range, java.time.LocalDate date, String category) {
        UUID orgId = orgAdmin.getOrganization().getId();

        List<User> employees = userRepository.findAll().stream()
                .filter(u -> u.getOrganization() != null && u.getOrganization().getId().equals(orgId))
                .collect(Collectors.toList());
        List<UUID> employeeIds = employees.stream().map(User::getId).collect(Collectors.toList());

        List<ActivityLog> orgLogs = activityLogRepository.findAll().stream()
                .filter(log -> employeeIds.contains(log.getUser().getId()))
                .collect(Collectors.toList());

        java.time.LocalDate today = java.time.LocalDate.now();
        List<ActivityLog> filteredLogs = orgLogs;

        if (date != null) {
            filteredLogs = filteredLogs.stream()
                    .filter(log -> log.getLogDate().equals(date))
                    .collect(Collectors.toList());
        } else if (range != null) {
            java.time.LocalDate startDate;
            switch (range.toLowerCase()) {
                case "daily":
                    startDate = today.minusDays(6);
                    break;
                case "weekly":
                    startDate = today.minusDays(27);
                    break;
                case "monthly":
                    startDate = today.minusMonths(6);
                    break;
                case "yearly":
                    startDate = today.minusYears(3);
                    break;
                default:
                    startDate = null;
            }
            if (startDate != null) {
                filteredLogs = filteredLogs.stream()
                        .filter(log -> !log.getLogDate().isBefore(startDate) && !log.getLogDate().isAfter(today))
                        .collect(Collectors.toList());
            }
        }

        if (category != null && !category.trim().isEmpty()) {
            filteredLogs = filteredLogs.stream()
                    .filter(log -> log.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }

        List<AdminActivityLogResponse> list = filteredLogs.stream()
                .map(log -> AdminActivityLogResponse.builder()
                        .id(log.getId())
                        .category(log.getCategory())
                        .activityType(log.getActivityType())
                        .quantity(log.getQuantity())
                        .unit(log.getUnit())
                        .co2Emission(Math.round(log.getCo2Emission() * 100.0) / 100.0)
                        .logDate(log.getLogDate())
                        .userId(log.getUser().getId())
                        .username(log.getUser().getUsername())
                        .userEmail(log.getUser().getEmail())
                        .imageProofId(log.getImageProofId())
                        .build())
                .collect(Collectors.toList());

        double totalCo2 = list.stream().mapToDouble(AdminActivityLogResponse::getCo2Emission).sum();

        Map<String, Double> categoryBreakdown = list.stream()
                .collect(Collectors.groupingBy(
                        AdminActivityLogResponse::getCategory,
                        Collectors.summingDouble(AdminActivityLogResponse::getCo2Emission)
                ));

        return AdminFilteredActivitiesResponse.builder()
                .activities(list)
                .totalCo2Emission(Math.round(totalCo2 * 100.0) / 100.0)
                .categoryBreakdown(categoryBreakdown)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminLeaderboardResponse> getOrgLeaderboard(User orgAdmin) {
        UUID orgId = orgAdmin.getOrganization().getId();
        List<User> employees = userRepository.findAll().stream()
                .filter(u -> u.getOrganization() != null && u.getOrganization().getId().equals(orgId))
                .filter(u -> "ROLE_USER".equals(u.getRole()))
                .collect(Collectors.toList());

        List<AdminLeaderboardResponse> entries = new ArrayList<>();
        double DAILY_UNLOGGED_EMISSION_PENALTY = 15.0;

        for (User user : employees) {
            java.time.LocalDate createdDate = user.getCreatedAt() != null 
                    ? user.getCreatedAt().toLocalDate() 
                    : java.time.LocalDate.now();
            long totalDays = java.time.temporal.ChronoUnit.DAYS.between(createdDate, java.time.LocalDate.now()) + 1;
            
            List<ActivityLog> logs = activityLogRepository.findByUserOrderByLogDateDesc(user);
            long loggedDays = logs.stream()
                    .map(ActivityLog::getLogDate)
                    .distinct()
                    .count();
            
            long unloggedDays = Math.max(0, totalDays - loggedDays);
            double actualCo2 = logs.stream()
                    .mapToDouble(ActivityLog::getCo2Emission)
                    .sum();
            
            double totalCo2 = actualCo2 + (unloggedDays * DAILY_UNLOGGED_EMISSION_PENALTY);

            entries.add(AdminLeaderboardResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .isEnabled(user.isEnabled())
                    .isDisabled(user.isDisabled())
                    .avatar(user.getAvatar())
                    .avatarUrl(com.infosys.cfootprint.util.AvatarUtils.getAvatarUrl(user))
                    .totalCo2Emission(Math.round(totalCo2 * 100.0) / 100.0)
                    .totalLogsCount((long) logs.size())
                    .build());
        }

        entries.sort(Comparator.comparing(AdminLeaderboardResponse::getTotalCo2Emission));

        int rank = 1;
        for (AdminLeaderboardResponse entry : entries) {
            entry.setRank(rank++);
        }

        return entries;
    }
}
