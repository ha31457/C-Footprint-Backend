package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.service.SystemSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class PublicSettingsController {

    @Autowired
    private SystemSettingService systemSettingService;

    @GetMapping
    public ResponseEntity<Map<String, Boolean>> getPublicSettings() {
        return ResponseEntity.ok(systemSettingService.getSettings());
    }
}
