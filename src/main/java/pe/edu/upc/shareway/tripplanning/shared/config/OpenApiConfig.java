package pe.edu.upc.shareway.tripplanning.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import java.util.Arrays;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import pe.edu.upc.shareway.tripplanning.shared.security.GatewayUser;
import pe.edu.upc.shareway.tripplanning.shared.security.RequiresRole;
import pe.edu.upc.shareway.tripplanning.shared.security.UserRole;

@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI sharewayTripPlanningOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("ShareWay Trip Planning & Matching API")
            .version("v1")
            .description("API for trip requests, driver availability and matching proposals. "
                + "Requests are expected to come through the ShareWay API Gateway, which validates "
                + "the JWT and forwards the user identity in the X-User-Id (UUID) and X-User-Role "
                + "(PASSENGER, DRIVER or ADMIN) headers."))
        .servers(List.of(new Server()
            .url("http://localhost:8081")
            .description("Local Docker environment")));
  }

  /** Documents the gateway identity headers on every operation that receives a {@link GatewayUser}. */
  @Bean
  OperationCustomizer gatewayUserHeadersCustomizer() {
    return (Operation operation, HandlerMethod handlerMethod) -> {
      boolean usesGatewayUser = Arrays.stream(handlerMethod.getMethodParameters())
          .anyMatch(parameter -> GatewayUser.class.equals(parameter.getParameterType()));
      if (!usesGatewayUser) {
        return operation;
      }

      RequiresRole requiresRole = handlerMethod.getMethodAnnotation(RequiresRole.class);
      List<String> allowedRoles = requiresRole == null
          ? Arrays.stream(UserRole.values()).map(Enum::name).toList()
          : Arrays.stream(requiresRole.value()).map(Enum::name).toList();

      StringSchema roleSchema = new StringSchema();
      allowedRoles.forEach(roleSchema::addEnumItem);

      operation.addParametersItem(new HeaderParameter()
          .name(GatewayUser.USER_ID_HEADER)
          .required(true)
          .description("Authenticated user id (JWT subject), set by the API Gateway.")
          .schema(new UUIDSchema()));
      operation.addParametersItem(new HeaderParameter()
          .name(GatewayUser.USER_ROLE_HEADER)
          .required(requiresRole != null)
          .description(requiresRole == null
              ? "Authenticated user role, set by the API Gateway. ADMIN sees every resource."
              : "Authenticated user role, set by the API Gateway. Must be "
                  + String.join(" or ", allowedRoles) + ".")
          .schema(roleSchema));

      if (operation.getResponses() == null) {
        operation.setResponses(new ApiResponses());
      }
      operation.getResponses().addApiResponse("401", new ApiResponse()
          .description("Missing or invalid X-User-Id header")
          .content(new Content().addMediaType("*/*", new MediaType()
              .schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
      return operation;
    };
  }
}
