package com.trademaster.ims.controller;

import com.trademaster.ims.model.SystemSetting;
import com.trademaster.ims.service.SystemSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "http://localhost:4200")
public class SystemSettingController {

    @Autowired
    private SystemSettingService settingService;

    @GetMapping
    public List<SystemSetting> getAllSettings() {
        return settingService.getAllSettings();
    }

    @GetMapping("/editable")
    public List<SystemSetting> getEditableSettings() {
        return settingService.getEditableSettings();
    }

    @GetMapping("/{key}")
    public ResponseEntity<String> getSetting(@PathVariable String key) {
        String value = settingService.getSettingValue(key);
        return ResponseEntity.ok(value != null ? value : "");
    }

    @PutMapping
    public ResponseEntity<Void> updateSettings(@RequestBody Map<String, String> updates) {
        settingService.updateSettings(updates);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public ResponseEntity<SystemSetting> createOrUpdate(@RequestBody SystemSetting setting) {
        return ResponseEntity.ok(settingService.createOrUpdate(setting));
    }
}