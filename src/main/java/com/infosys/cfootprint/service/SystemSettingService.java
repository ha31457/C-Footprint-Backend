package com.infosys.cfootprint.service;

import com.infosys.cfootprint.model.SystemSetting;
import com.infosys.cfootprint.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemSettingService {

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    @Transactional(readOnly = true)
    public boolean isFeatureEnabled(String configKey) {
        return systemSettingRepository.findById(configKey)
                .map(setting -> "true".equalsIgnoreCase(setting.getConfigValue()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getSettings() {
        List<SystemSetting> settings = systemSettingRepository.findAll();
        Map<String, Boolean> map = new HashMap<>();
        for (SystemSetting setting : settings) {
            map.put(setting.getConfigKey(), "true".equalsIgnoreCase(setting.getConfigValue()));
        }
        return map;
    }

    @Transactional
    public Map<String, Boolean> updateSettings(Map<String, Boolean> newSettings) {
        for (Map.Entry<String, Boolean> entry : newSettings.entrySet()) {
            SystemSetting setting = systemSettingRepository.findById(entry.getKey())
                    .orElseGet(() -> SystemSetting.builder().configKey(entry.getKey()).build());
            setting.setConfigValue(String.valueOf(entry.getValue()));
            systemSettingRepository.save(setting);
        }
        return getSettings();
    }
}
