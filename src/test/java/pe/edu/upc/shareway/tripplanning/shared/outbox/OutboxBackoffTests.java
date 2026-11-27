package pe.edu.upc.shareway.tripplanning.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxBackoffTests {

	private final OutboxRelay relay = new OutboxRelay(null, null,
			new OutboxProperties(false, Duration.ofSeconds(5), 50, Duration.ofSeconds(5), Duration.ofMinutes(5)),
			Clock.systemUTC());

	@Test
	void backoffDoublesFromTheInitialWait() {
		assertThat(relay.backoff(1)).isEqualTo(Duration.ofSeconds(5));
		assertThat(relay.backoff(2)).isEqualTo(Duration.ofSeconds(10));
		assertThat(relay.backoff(3)).isEqualTo(Duration.ofSeconds(20));
		assertThat(relay.backoff(6)).isEqualTo(Duration.ofSeconds(160));
	}

	@Test
	void backoffIsCappedAtFiveMinutes() {
		assertThat(relay.backoff(7)).isEqualTo(Duration.ofMinutes(5));
		assertThat(relay.backoff(1_000)).isEqualTo(Duration.ofMinutes(5));
	}
}
