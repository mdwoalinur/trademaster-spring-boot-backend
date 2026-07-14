package com.trademaster.ims.controller;

import com.trademaster.ims.service.GlobalSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    public GlobalSearchController(GlobalSearchService globalSearchService) {
        this.globalSearchService = globalSearchService;
    }

    @GetMapping("/api/global-search")
    public ResponseEntity<List<Map<String, Object>>> searchResults(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(globalSearchService.searchResults(query, limit));
    }

    @GetMapping("/api/search/global")
    public ResponseEntity<Map<String, Object>> globalSearch(@RequestParam(defaultValue = "") String keyword) {
        return ResponseEntity.ok(globalSearchService.search(keyword));
    }
}
