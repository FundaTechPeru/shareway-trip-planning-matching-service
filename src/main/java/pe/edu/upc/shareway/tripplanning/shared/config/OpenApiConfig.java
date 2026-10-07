package pe.edu.upc.shareway.tripplanning.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI sharewayTripPlanningOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("ShareWay Trip Planning & Matching API")
            .version("v1")
            .description("API for trip requests, driver availability and matching proposals."))
        .servers(List.of(new Server()
            .url("http://localhost:8081")
            .description("Local Docker environment")));
  }
}
