package pe.edu.upc.shareway.tripplanning.shared.security;

import java.util.Arrays;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves the {@link AuthenticatedUser} from the {@code Authorization: Bearer} header that the API
 * Gateway forwards unchanged. The X-User-Id and X-User-Role headers are not trusted. When the
 * handler method carries {@link RequiresRole}, the token role must be one of the listed roles.
 */
public class AuthenticatedUserArgumentResolver implements HandlerMethodArgumentResolver {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwtService;

  public AuthenticatedUserArgumentResolver(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return AuthenticatedUser.class.equals(parameter.getParameterType());
  }

  @Override
  public AuthenticatedUser resolveArgument(MethodParameter parameter,
      ModelAndViewContainer mavContainer, NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    String header = webRequest.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      throw new UnauthenticatedException("Missing bearer token");
    }
    AuthenticatedUser user = jwtService.authenticate(header.substring(BEARER_PREFIX.length()).trim())
        .orElseThrow(() -> new UnauthenticatedException("Invalid or expired token"));

    RequiresRole requiresRole = parameter.getMethodAnnotation(RequiresRole.class);
    if (requiresRole != null
        && Arrays.stream(requiresRole.value()).noneMatch(role -> role == user.role())) {
      throw new ForbiddenException("This operation requires the role "
          + String.join(" or ", Arrays.stream(requiresRole.value()).map(Enum::name).toList()));
    }
    return user;
  }
}
