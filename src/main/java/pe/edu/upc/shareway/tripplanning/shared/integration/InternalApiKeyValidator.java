package pe.edu.upc.shareway.tripplanning.shared.integration;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the service without INTERNAL_API_KEY outside the dev, test and local profiles,
 * where the shared development value from {@code application.properties} is used instead.
 */
@Component
public class InternalApiKeyValidator {

  /** The development value shared by every ShareWay service; it must stay in sync with them. */
  static final String DEVELOPMENT_API_KEY = "dev-only-shareway-internal-api-key";
  private static final Set<String> RELAXED_PROFILES = Set.of("dev", "test", "local");

  private final InternalApiProperties properties;
  private final Environment environment;

  public InternalApiKeyValidator(InternalApiProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
  }

  @PostConstruct
  void validate() {
    if (Arrays.stream(environment.getActiveProfiles()).anyMatch(RELAXED_PROFILES::contains)) {
      return;
    }
    String apiKey = properties.apiKey();
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "INTERNAL_API_KEY must be set in production. Configure the INTERNAL_API_KEY environment "
              + "variable with the key shared by the ShareWay services.");
    }
    if (DEVELOPMENT_API_KEY.equals(apiKey)) {
      throw new IllegalStateException(
          "INTERNAL_API_KEY is still the development value. Set the production key shared by the "
              + "ShareWay services.");
    }
  }
}
