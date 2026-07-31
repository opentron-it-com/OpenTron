package org.opentron.backend.controllers;

import org.opentron.backend.services.NetworkPolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.opentron.backend.services.SearchKeyService;
import org.opentron.backend.services.WebSearchService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/v1/settings")
public class SettingsController {

    private final NetworkPolicyService networkPolicyService;
    private final SearchKeyService searchKeyService;
    private final WebSearchService webSearchService;

    public SettingsController(NetworkPolicyService networkPolicyService) {
        this.networkPolicyService = networkPolicyService;
        this.searchKeyService = null;
        this.webSearchService = null;
    }

    @Autowired
    public SettingsController(NetworkPolicyService networkPolicyService, SearchKeyService searchKeyService, WebSearchService webSearchService) {
        this.networkPolicyService = networkPolicyService;
        this.searchKeyService = searchKeyService;
        this.webSearchService = webSearchService;
    }

    @GetMapping("/network")
    public ResponseEntity<Map<String, Object>> getNetworkPolicy() {
        return ResponseEntity.ok(Map.of("allow_internet", networkPolicyService.isInternetAllowed()));
    }

    @PostMapping("/network")
    public ResponseEntity<Map<String, Object>> setNetworkPolicy(@RequestBody Map<String, Object> payload) {
        Object v = payload.get("allow_internet");
        if (v instanceof Boolean) {
            networkPolicyService.setAllowInternet((Boolean) v);
            return ResponseEntity.ok(Map.of("allow_internet", networkPolicyService.isInternetAllowed()));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "allow_internet must be boolean"));
    }

    @GetMapping("/search-key")
    public ResponseEntity<Map<String, Object>> getSearchKey() {
        if (searchKeyService == null) return ResponseEntity.status(503).body(Map.of("error", "Search key service not available"));
        String k = searchKeyService.loadKey();
        return ResponseEntity.ok(Map.of("configured", k != null, "masked", k == null ? null : (k.length() > 8 ? k.substring(0,4) + "..." + k.substring(k.length()-4) : "****")));
    }

    @PostMapping("/search-key")
    public ResponseEntity<Map<String, Object>> setSearchKey(@RequestBody Map<String, Object> payload) {
        if (searchKeyService == null) return ResponseEntity.status(503).body(Map.of("error", "Search key service not available"));
        Object v = payload.get("key");
        Object p = payload.get("provider");
        try {
            String ks = v == null ? null : v.toString();
            String prov = p == null ? null : p.toString();
            searchKeyService.saveKey(ks, prov);
            return ResponseEntity.ok(Map.of("configured", ks != null && !ks.isBlank(), "provider", prov));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save key"));
        }
    }

    @PostMapping("/search/query")
    public ResponseEntity<Object> querySearch(@RequestBody Map<String, Object> payload) {
        if (webSearchService == null) return ResponseEntity.status(503).body(Map.of("error", "Search service not available"));
        Object q = payload.get("q");
        int limit = 3;
        try {
            Object l = payload.get("limit");
            if (l instanceof Number) limit = ((Number) l).intValue();
            if (q == null || q.toString().isBlank()) return ResponseEntity.badRequest().body(Map.of("error","q is required"));
            var results = webSearchService.search(q.toString(), limit);
            return ResponseEntity.ok(Map.of("results", results));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Search failed"));
        }
    }
}
