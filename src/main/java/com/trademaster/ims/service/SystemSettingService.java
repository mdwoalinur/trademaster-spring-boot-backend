package com.trademaster.ims.service;

import com.trademaster.ims.model.SystemSetting;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SystemSettingService {

    @Autowired
    private SystemSettingRepository settingRepository;

    public List<SystemSetting> getAllSettings() {
        return settingRepository.findAll();
    }

    public List<SystemSetting> getEditableSettings() {
        return settingRepository.findByIsEditableTrue();
    }

    public String getSettingValue(String key) {
        return settingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .orElse(null);
    }

    @Transactional
    public void updateSetting(String key, String value) {
        SystemSetting setting = settingRepository.findBySettingKey(key)
                .orElseThrow(() -> new RuntimeException("Setting not found: " + key));
        setting.setSettingValue(value);
        setting.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(setting);
    }

    @Transactional
    public void updateSettings(Map<String, String> updates) {
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            updateSetting(entry.getKey(), entry.getValue());
        }
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "SystemSetting")
    public SystemSetting createOrUpdate(SystemSetting setting) {
        Optional<SystemSetting> existing = settingRepository.findBySettingKey(setting.getSettingKey());
        if (existing.isPresent()) {
            SystemSetting s = existing.get();
            s.setSettingValue(setting.getSettingValue());
            s.setDescription(setting.getDescription());
            s.setDataType(setting.getDataType());
            s.setIsEditable(setting.getIsEditable());
            s.setUpdatedAt(LocalDateTime.now());
            return settingRepository.save(s);
        } else {
            setting.setCreatedAt(LocalDateTime.now());
            setting.setUpdatedAt(LocalDateTime.now());
            return settingRepository.save(setting);
        }
    }
}
