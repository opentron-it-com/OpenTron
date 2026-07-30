package org.opentron.backend.controllers;

import java.util.*;
import org.springframework.web.bind.annotation.*;

/**
 * Agent Templates API
 * Provides predefined agent templates for the Agents page
 */
@RestController
@RequestMapping("/v1")
public class AgentTemplatesController {

    /**
     * GET /v1/agent-templates
     * Returns available agent templates
     */
    @GetMapping("/agent-templates")
    public Map<String, Object> getAgentTemplates() {
        List<Map<String, Object>> templates = Arrays.asList(
                Map.of(
                        "id", "template-backend",
                        "name", "Backend Specialist",
                        "description", "Optimizes Java, Spring Boot, and database performance",
                        "skills", Arrays.asList(
                                "java_optimization",
                                "spring_boot_configuration",
                                "database_query_optimization",
                                "cache_optimization"
                        ),
                        "category", "CODE"
                ),
                Map.of(
                        "id", "template-frontend",
                        "name", "Frontend Specialist",
                        "description", "Optimizes React, TypeScript, and UI performance",
                        "skills", Arrays.asList(
                                "react_optimization",
                                "component_design",
                                "css_optimization",
                                "responsive_design"
                        ),
                        "category", "FRONTEND"
                ),
                Map.of(
                        "id", "template-qa",
                        "name", "QA Specialist",
                        "description", "Handles testing, debugging, and code review",
                        "skills", Arrays.asList(
                                "unit_testing",
                                "integration_testing",
                                "debugging",
                                "code_review"
                        ),
                        "category", "QA"
                ),
                Map.of(
                        "id", "template-devops",
                        "name", "DevOps Specialist",
                        "description", "Monitors performance, metrics, and health checks",
                        "skills", Arrays.asList(
                                "performance_monitoring",
                                "metrics_collection",
                                "health_checks",
                                "alerting"
                        ),
                        "category", "DEVOPS"
                ),
                Map.of(
                        "id", "template-webdesign",
                        "name", "Web Design Specialist",
                        "description", "Optimizes UI/UX, accessibility, and responsive design",
                        "skills", Arrays.asList(
                                "accessibility",
                                "responsive_design",
                                "ui_design",
                                "css_optimization"
                        ),
                        "category", "WEBDESIGN"
                )
        );

        return Map.of(
                "templates", templates,
                "count", templates.size(),
                "status", "ok"
        );
    }

    /**
     * GET /v1/agent-templates/{id}
     * Get a specific agent template
     */
    @SuppressWarnings("unchecked")
@GetMapping("/agent-templates/{id}")
    public Map<String, Object> getAgentTemplate(@PathVariable String id) {
        Map<String, Object> templates = Map.ofEntries(
                Map.entry("template-backend", Map.of(
                        "id", "template-backend",
                        "name", "Backend Specialist",
                        "description", "Optimizes Java, Spring Boot, and database performance",
                        "skills", Arrays.asList(
                                "java_optimization",
                                "spring_boot_configuration",
                                "database_query_optimization",
                                "cache_optimization"
                        ),
                        "category", "CODE",
                        "model", "mistral",
                        "system_prompt", "You are a backend specialist..."
                )),
                Map.entry("template-frontend", Map.of(
                        "id", "template-frontend",
                        "name", "Frontend Specialist",
                        "description", "Optimizes React, TypeScript, and UI performance",
                        "skills", Arrays.asList(
                                "react_optimization",
                                "component_design",
                                "css_optimization",
                                "responsive_design"
                        ),
                        "category", "FRONTEND",
                        "model", "mistral",
                        "system_prompt", "You are a frontend specialist..."
                ))
        );

        Map<String, Object> template = (Map<String, Object>) templates.get(id);
        if (template == null) {
            return Map.of("error", "Template not found", "status", "not_found");
        }

        return template;
    }
}
