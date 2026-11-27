package pe.edu.upc.shareway.tripplanning.shared.security;

import java.util.Arrays;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Builds the {@link GatewayUser} from the headers set by the API Gateway. The JWT is not
 * validated here: the gateway already did it and this service has no signing secret.
 */
public class GatewayUserArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return GatewayUser.class.equals(parameter.getParameterType());
  }

  @Override
  public GatewayUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
    UUID userId = parseUserId(webRequest.getHeader(GatewayUser.USER_ID_HEADER));
    UserRole role = UserRole.parse(webRequest.getHeader(GatewayUser.USER_ROLE_HEADER)).orElse(null);
    GatewayUser user = new GatewayUser(userId, role);

    RequiresRole requiresRole = parameter.getMethodAnnotation(RequiresRole.class);
    if (requiresRole != null && Arrays.stream(requiresRole.value()).noneMatch(r -> r == role)) {
      throw new ForbiddenException("This operation requires " + GatewayUser.USER_ROLE_HEADER
          + " " + String.join(" or ", Arrays.stream(requiresRole.value()).map(Enum::name).toList()));
    }
    return user;
  }

  private UUID parseUserId(String header) {
    if (header == null || header.isBlank()) {
      throw new UnauthenticatedException("Missing " + GatewayUser.USER_ID_HEADER + " header");
    }
    String value = header.trim();
    try {
      // UUID.fromString accepts shortened forms such as "1-2-3-4-5"; require the canonical length.
      if (value.length() != 36) {
        throw new IllegalArgumentException(value);
      }
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      throw new UnauthenticatedException(GatewayUser.USER_ID_HEADER + " header must be a UUID");
    }
  }
}
