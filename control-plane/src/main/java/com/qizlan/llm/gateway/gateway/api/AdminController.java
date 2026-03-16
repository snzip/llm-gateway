package com.qizlan.llm.gateway.gateway.api;

import com.qizlan.llm.gateway.gateway.service.AdminMetricsService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminMetricsService adminMetricsService;

    public AdminController(AdminMetricsService adminMetricsService) {
        this.adminMetricsService = adminMetricsService;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return Map.of("data", adminMetricsService.overview());
    }

    @GetMapping("/metrics/timeseries")
    public Map<String, Object> metricsTimeseries(@RequestParam(name = "bucket", required = false) String bucket) {
        return Map.of("data", adminMetricsService.timeseries(bucket));
    }

    @GetMapping("/metrics/cost-by-model")
    public Map<String, Object> costByModel() {
        return Map.of("data", adminMetricsService.costByModel());
    }

    @GetMapping("/organizations")
    public Map<String, Object> organizations() {
        return Map.of("data", adminMetricsService.organizations());
    }

    @GetMapping("/organizations/{orgId}")
    public Map<String, Object> organization(@PathVariable("orgId") String orgId) {
        return Map.of("data", adminMetricsService.organizationDetail(orgId));
    }

    @GetMapping("/organizations/{orgId}/projects")
    public Map<String, Object> organizationProjects(@PathVariable("orgId") String orgId) {
        return Map.of("data", adminMetricsService.organizationProjects(orgId));
    }

    @GetMapping("/organizations/{orgId}/projects/{projectId}/metrics")
    public Map<String, Object> projectMetrics(@PathVariable("orgId") String orgId, @PathVariable("projectId") String projectId) {
        return Map.of("data", adminMetricsService.projectMetrics(orgId, projectId));
    }

    @GetMapping("/organizations/{orgId}/projects/{projectId}/logs")
    public Map<String, Object> projectLogs(@PathVariable("orgId") String orgId, @PathVariable("projectId") String projectId) {
        return Map.of("data", adminMetricsService.projectLogs(orgId, projectId));
    }
}
