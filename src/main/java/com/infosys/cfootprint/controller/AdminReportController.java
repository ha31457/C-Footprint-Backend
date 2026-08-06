package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.service.AdminReportService;
import com.infosys.cfootprint.service.SystemSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminReportController {

    @Autowired
    private SystemSettingService systemSettingService;

    @Autowired
    private AdminReportService adminReportService;

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Boolean>> getSettings() {
        return ResponseEntity.ok(systemSettingService.getSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Boolean>> updateSettings(@RequestBody Map<String, Boolean> request) {
        return ResponseEntity.ok(systemSettingService.updateSettings(request));
    }

    @GetMapping("/reports/summary")
    public ResponseEntity<Map<String, Object>> getReportSummary() {
        return ResponseEntity.ok(adminReportService.getPlatformReportData());
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(@RequestParam(defaultValue = "pdf") String format) {
        String filename = "platform_report_" + System.currentTimeMillis();
        byte[] data;
        MediaType mediaType;
        String finalFilename;

        switch (format.toLowerCase()) {
            case "csv":
                String csv = adminReportService.generateCsvReport();
                data = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                mediaType = MediaType.parseMediaType("text/csv");
                finalFilename = filename + ".csv";
                break;
            case "word":
            case "doc":
            case "docx":
                data = adminReportService.generateWordReport();
                mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                finalFilename = filename + ".docx";
                break;
            case "pdf":
            default:
                data = adminReportService.generatePdfReport();
                mediaType = MediaType.APPLICATION_PDF;
                finalFilename = filename + ".pdf";
                break;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + finalFilename + "\"")
                .contentType(mediaType)
                .body(data);
    }
}
