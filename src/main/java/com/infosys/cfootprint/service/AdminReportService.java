package com.infosys.cfootprint.service;

import com.infosys.cfootprint.model.ActivityLog;
import com.infosys.cfootprint.model.Goal;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.BadgeRepository;
import com.infosys.cfootprint.repository.GoalRepository;
import com.infosys.cfootprint.repository.UserRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
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
public class AdminReportService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private BadgeRepository badgeRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> getPlatformReportData() {
        List<User> users = userRepository.findAll();
        List<ActivityLog> logs = activityLogRepository.findAll();
        List<Goal> goals = goalRepository.findAll();
        long badgesCount = badgeRepository.count();

        long totalUsers = users.size();
        long activeUsers = logs.stream().map(log -> log.getUser().getId()).distinct().count();
        long totalLogs = logs.size();
        double totalCo2 = logs.stream().mapToDouble(ActivityLog::getCo2Emission).sum();
        double averageCo2PerUser = totalUsers > 0 ? (totalCo2 / totalUsers) : 0.0;

        long completedGoals = goals.stream().filter(g -> "COMPLETED".equalsIgnoreCase(g.getStatus())).count();
        double goalsSuccessRate = goals.isEmpty() ? 0.0 : ((double) completedGoals / goals.size()) * 100.0;

        Map<String, Double> categoryBreakdown = logs.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getCategory,
                        Collectors.summingDouble(ActivityLog::getCo2Emission)
                ));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalUsers", totalUsers);
        data.put("activeUsers", activeUsers);
        data.put("totalLogs", totalLogs);
        data.put("totalCo2", Math.round(totalCo2 * 100.0) / 100.0);
        data.put("averageCo2PerUser", Math.round(averageCo2PerUser * 100.0) / 100.0);
        data.put("totalGoals", (long) goals.size());
        data.put("completedGoals", completedGoals);
        data.put("goalsSuccessRate", Math.round(goalsSuccessRate * 100.0) / 100.0);
        data.put("badgesAwarded", badgesCount);
        data.put("categoryBreakdown", categoryBreakdown);

        return data;
    }

    @SuppressWarnings("unchecked")
    public String generateCsvReport() {
        Map<String, Object> data = getPlatformReportData();
        StringBuilder csv = new StringBuilder();
        
        csv.append("Carbon Footprint Platform Analytics Report\n");
        csv.append("Generated On: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
        
        csv.append("Metric,Value\n");
        csv.append("Total Registered Users,").append(data.get("totalUsers")).append("\n");
        csv.append("Active Users (Min 1 Log),").append(data.get("activeUsers")).append("\n");
        csv.append("Total Activity Logs,").append(data.get("totalLogs")).append("\n");
        csv.append("Total CO2 Footprint (kg),").append(data.get("totalCo2")).append("\n");
        csv.append("Average Footprint Per User (kg),").append(data.get("averageCo2PerUser")).append("\n");
        csv.append("Total Reduction Goals,").append(data.get("totalGoals")).append("\n");
        csv.append("Completed Goals,").append(data.get("completedGoals")).append("\n");
        csv.append("Goals Success Rate (%),").append(data.get("goalsSuccessRate")).append("\n");
        csv.append("Total Badges Awarded,").append(data.get("badgesAwarded")).append("\n\n");

        csv.append("CO2 Emission Category Breakdown\n");
        csv.append("Category,Total CO2 Emission (kg)\n");
        Map<String, Double> breakdown = (Map<String, Double>) data.get("categoryBreakdown");
        for (Map.Entry<String, Double> entry : breakdown.entrySet()) {
            csv.append(entry.getKey()).append(",").append(Math.round(entry.getValue() * 100.0) / 100.0).append("\n");
        }

        return csv.toString();
    }

    @SuppressWarnings("unchecked")
    public byte[] generatePdfReport() {
        Map<String, Object> data = getPlatformReportData();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        com.lowagie.text.Document document = new com.lowagie.text.Document(PageSize.A4, 36, 36, 54, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Document Header / Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Font.BOLD, java.awt.Color.DARK_GRAY);
            Paragraph title = new Paragraph("C-Footprint Tracker Platform Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);

            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.ITALIC, java.awt.Color.GRAY);
            Paragraph meta = new Paragraph("Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), metaFont);
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(30);
            document.add(meta);

            // Table of Metrics
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(90);
            table.setSpacingBefore(10);
            table.setSpacingAfter(20);

            // Define Table Header Font
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Font.BOLD, java.awt.Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.NORMAL, java.awt.Color.BLACK);

            // Set Header cells
            PdfPCell h1 = new PdfPCell(new Phrase("Sustainability Metric", headerFont));
            h1.setBackgroundColor(new java.awt.Color(47, 133, 90)); // Green Theme
            h1.setPadding(8);
            h1.setHorizontalAlignment(Element.ALIGN_LEFT);

            PdfPCell h2 = new PdfPCell(new Phrase("Current Platform Value", headerFont));
            h2.setBackgroundColor(new java.awt.Color(47, 133, 90));
            h2.setPadding(8);
            h2.setHorizontalAlignment(Element.ALIGN_CENTER);

            table.addCell(h1);
            table.addCell(h2);

            addTableCell(table, "Total Registered Users", String.valueOf(data.get("totalUsers")), cellFont);
            addTableCell(table, "Active Users (With Logs)", String.valueOf(data.get("activeUsers")), cellFont);
            addTableCell(table, "Total Activity Logs", String.valueOf(data.get("totalLogs")), cellFont);
            addTableCell(table, "Total CO2 Emissions (kg)", String.valueOf(data.get("totalCo2")), cellFont);
            addTableCell(table, "Average Footprint per User (kg)", String.valueOf(data.get("averageCo2PerUser")), cellFont);
            addTableCell(table, "Total Goals set", String.valueOf(data.get("totalGoals")), cellFont);
            addTableCell(table, "Completed Goals", String.valueOf(data.get("completedGoals")), cellFont);
            addTableCell(table, "Goals Success Rate (%)", data.get("goalsSuccessRate") + "%", cellFont);
            addTableCell(table, "Badges Awarded", String.valueOf(data.get("badgesAwarded")), cellFont);

            document.add(table);

            // Category Breakdown Title
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Font.BOLD, java.awt.Color.DARK_GRAY);
            Paragraph subTitle = new Paragraph("CO2 Emission Category Breakdown", subTitleFont);
            subTitle.setSpacingBefore(20);
            subTitle.setSpacingAfter(10);
            document.add(subTitle);

            // Category Breakdown Table
            PdfPTable catTable = new PdfPTable(2);
            catTable.setWidthPercentage(90);

            PdfPCell c1 = new PdfPCell(new Phrase("Activity Category", headerFont));
            c1.setBackgroundColor(new java.awt.Color(49, 130, 206)); // Blue Theme
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
            throw new RuntimeException("Failed to generate PDF document: " + e.getMessage(), e);
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

    @SuppressWarnings("unchecked")
    public byte[] generateWordReport() {
        Map<String, Object> data = getPlatformReportData();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (XWPFDocument doc = new XWPFDocument()) {
            // Title Paragraph
            XWPFParagraph titleParagraph = doc.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText("Carbon Footprint Platform Analytics Report");
            titleRun.setFontSize(20);
            titleRun.setBold(true);
            titleRun.setFontFamily("Arial");
            titleRun.setColor("2F855A");

            // Subtitle metadata
            XWPFParagraph metaParagraph = doc.createParagraph();
            metaParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun metaRun = metaParagraph.createRun();
            metaRun.setText("Generated on: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            metaRun.setFontSize(10);
            metaRun.setItalic(true);
            metaRun.setColor("718096");

            // Empty line
            doc.createParagraph();

            // Headings for Metrics
            XWPFParagraph heading = doc.createParagraph();
            XWPFRun headingRun = heading.createRun();
            headingRun.setText("Key Sustainability Metrics");
            headingRun.setFontSize(14);
            headingRun.setBold(true);
            headingRun.setColor("2D3748");

            // Create Table of Metrics
            XWPFTable table = doc.createTable();
            table.setWidth("100%");

            // Add Header Row
            XWPFTableRow headerRow = table.getRow(0);
            XWPFTableCell cellKey = headerRow.getCell(0);
            cellKey.setText("Sustainability Metric");
            cellKey.setColor("2F855A");
            XWPFTableCell cellVal = headerRow.createCell();
            cellVal.setText("Current Value");
            cellVal.setColor("2F855A");

            addWordRow(table, "Total Registered Users", String.valueOf(data.get("totalUsers")));
            addWordRow(table, "Active Users (With Logs)", String.valueOf(data.get("activeUsers")));
            addWordRow(table, "Total Activity Logs", String.valueOf(data.get("totalLogs")));
            addWordRow(table, "Total CO2 Emissions", data.get("totalCo2") + " kg");
            addWordRow(table, "Average Footprint per User", data.get("averageCo2PerUser") + " kg");
            addWordRow(table, "Total Goals set", String.valueOf(data.get("totalGoals")));
            addWordRow(table, "Completed Goals", String.valueOf(data.get("completedGoals")));
            addWordRow(table, "Goals Success Rate", data.get("goalsSuccessRate") + "%");
            addWordRow(table, "Badges Awarded", String.valueOf(data.get("badgesAwarded")));

            doc.createParagraph();

            // Breakdown Heading
            XWPFParagraph breakHeading = doc.createParagraph();
            XWPFRun breakRun = breakHeading.createRun();
            breakRun.setText("CO2 Emission Category Breakdown");
            breakRun.setFontSize(14);
            breakRun.setBold(true);
            breakRun.setColor("2D3748");

            // Breakdown Table
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
            throw new RuntimeException("Failed to generate Word document: " + e.getMessage(), e);
        }

        return out.toByteArray();
    }

    private void addWordRow(XWPFTable table, String metric, String value) {
        XWPFTableRow row = table.createRow();
        row.getCell(0).setText(metric);
        row.getCell(1).setText(value);
    }
}
