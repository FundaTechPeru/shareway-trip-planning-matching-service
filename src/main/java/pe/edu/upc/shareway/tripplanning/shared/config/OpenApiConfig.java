package pe.edu.upc.shareway.tripplanning.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import pe.edu.upc.shareway.tripplanning.shared.security.AuthenticatedUser;
import pe.edu.upc.shareway.tripplanning.shared.security.RequiresRole;

@Configuration
public class OpenApiConfig {

  static final String BEARER_AUTH = "bearerAuth";

  @Bean
  OpenAPI sharewayTripPlanningOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("ShareWay Trip Planning & Matching API")
            .version("v1")
            .description("API for trip requests, driver availabilities (trip groups) and matching "
                + "proposals. Every /api/v1 endpoint requires the HS256 access token issued by Driver "
                + "Management (Authorization: Bearer). The user is the sub claim (UUID) and the role "
                + "is the role claim (PASSENGER, DRIVER or ADMIN). The API Gateway forwards the header "
                + "unchanged. Trip group changes are sent to Booking & Pricing as events through an "
                + "outbox."))
        .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("Access token issued by Driver Management (POST /api/v1/auth/login).")))
        .servers(List.of(new Server()
            .url("http://localhost:8081")
            .description("Local Docker environment")));
  }

  /**
   * Adds the bearer requirement, the allowed roles and the 401 response to every operation that
   * receives an {@link AuthenticatedUser}.
   */
  @Bean
  OperationCustomizer bearerAuthCustomizer() {
    return (Operation operation, HandlerMethod handlerMethod) -> {
      boolean authenticated = Arrays.stream(handlerMethod.getMethodParameters())
          .anyMatch(parameter -> AuthenticatedUser.class.equals(parameter.getParameterType()));
      if (!authenticated) {
        return operation;
      }

      operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));

      RequiresRole requiresRole = handlerMethod.getMethodAnnotation(RequiresRole.class);
      String roles = requiresRole == null
          ? "Any authenticated role."
          : "Required role: " + String.join(" or ",
              Arrays.stream(requiresRole.value()).map(Enum::name).toList()) + ".";
      operation.setDescription(operation.getDescription() == null
          ? roles
          : operation.getDescription() + " " + roles);

      if (operation.getResponses() == null) {
        operation.setResponses(new ApiResponses());
      }
      operation.getResponses().addApiResponse("401", new ApiResponse()
          .description("Missing, invalid or expired bearer token")
          .content(new Content().addMediaType("*/*", new MediaType()
              .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
      return operation;
    };
  }
}
