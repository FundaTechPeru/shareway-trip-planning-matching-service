package pe.edu.upc.shareway.tripplanning.shared.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class InternalApiKeyValidatorTest {

	@Test
	void failsWhenKeyIsMissingOutsideTheRelaxedProfiles() {
		assertThatThrownBy(() -> validate(null, "prod"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("INTERNAL_API_KEY must be set");
		assertThatThrownBy(() -> validate(" "))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("INTERNAL_API_KEY must be set");
	}

	@Test
	void failsWhenKeyIsTheDevelopmentValueOutsideTheRelaxedProfiles() {
		assertThatThrownBy(() -> validate(InternalApiKeyValidator.DEVELOPMENT_API_KEY, "prod"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("development value");
	}

	@Test
	void acceptsAConfiguredKeyInProduction() {
		assertThatCode(() -> validate("key-configured-in-render", "prod")).doesNotThrowAnyException();
	}

	@Test
	void acceptsTheDevelopmentValueUnderRelaxedProfiles() {
		for (String profile : new String[] {"dev", "test", "local"}) {
			assertThatCode(() -> validate(InternalApiKeyValidator.DEVELOPMENT_API_KEY, profile))
					.doesNotThrowAnyException();
		}
	}

	private static void validate(String apiKey, String... activeProfiles) {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(activeProfiles);
		new InternalApiKeyValidator(new InternalApiProperties(apiKey), environment).validate();
	}
}
