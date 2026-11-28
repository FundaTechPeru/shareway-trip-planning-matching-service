package pe.edu.upc.shareway.tripplanning.shared.security;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityWebConfig implements WebMvcConfigurer {

  private final JwtService jwtService;

  public SecurityWebConfig(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new AuthenticatedUserArgumentResolver(jwtService));
  }
}
