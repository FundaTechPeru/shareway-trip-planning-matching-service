package pe.edu.upc.shareway.tripplanning.shared.api;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceInfoController {

  @GetMapping("/")
  Map<String, Object> root() {
    return serviceInfo();
  }

  @GetMapping("/api")
  Map<String, Object> api() {
    return serviceInfo();
  }

  private Map<String, Object> serviceInfo() {
    return Map.of(
        "service", "ShareWay Trip Planning & Matching Service",
        "status", "UP",
        "description", "Microservice for trip requests, driver availability and matching proposals.",
        "health", "/actuator/health",
        "swagger", "/swagger-ui.html",
        "openapi", "/v3/api-docs",
        "endpoints", List.of(
            "GET /api/v1/trip-requests",
            "POST /api/v1/trip-requests",
            "POST /api/v1/driver-availabilities",
            "GET /api/v1/trip-requests/{tripRequestId}/match-proposals",
            "POST /api/v1/trip-requests/{tripRequestId}/match-proposals"
        )
    );
  }
}
