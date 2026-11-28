package pe.edu.upc.shareway.tripplanning;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApplicationConfigurationTest {

  @Test
  void defaultProfileLoadsTheProductionPropertiesAndResolvesEnvironmentVariables() {
    // Test the actual deployment file. The test resources have a different application.properties.
    var config = Path.of("src/main/resources/application.properties").toAbsolutePath().toUri();
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues(
            "spring.config.location=" + config,
            "INTERNAL_API_KEY=test-production-internal-key",
            "JWT_SECRET=test-production-signing-key-with-more-than-32-characters",
            "PORT=10000")
        .run(application -> {
          var environment = application.getEnvironment();
          assertThat(environment.getProperty("app.internal.api-key"))
              .isEqualTo("test-production-internal-key");
          assertThat(environment.getProperty("app.jwt.secret"))
              .isEqualTo("test-production-signing-key-with-more-than-32-characters");
          assertThat(environment.getProperty("server.port")).isEqualTo("10000");
        });
  }
}
