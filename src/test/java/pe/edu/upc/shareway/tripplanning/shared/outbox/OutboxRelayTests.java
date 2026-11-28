package pe.edu.upc.shareway.tripplanning.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.upc.shareway.tripplanning.support.MutableClock;
import pe.edu.upc.shareway.tripplanning.support.StubBookingServer;
import pe.edu.upc.shareway.tripplanning.support.StubBookingServer.ReceivedRequest;

/** Delivery and error classification of the outbox relay against a simulated Booking server. */
@SpringBootTest(properties = {
		"app.booking.timeout=1s",
		"app.outbox.initial-backoff=5s",
		"app.outbox.max-backoff=5m"
})
@Import(OutboxRelayTests.ClockConfiguration.class)
class OutboxRelayTests {

	private static final StubBookingServer BOOKING = new StubBookingServer();
	private static final Instant START = Instant.parse("2026-11-27T10:00:00Z");

	@TestConfiguration
	static class ClockConfiguration {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock(START);
		}
	}

	@DynamicPropertySource
	static void bookingUrl(DynamicPropertyRegistry registry) {
		registry.add("app.booking.url", BOOKING::url);
	}

	@Autowired
	private OutboxRelay relay;

	@Autowired
	private OutboxService outboxService;

	@Autowired
	private OutboxEventRepository repository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void reset() {
		repository.deleteAll();
		BOOKING.reset();
		clock.set(START);
	}

	@Test
	void deliversPendingEventsInOrderWithTheInternalApiKey() {
		List<UUID> ids = enqueue("TripGroupOpened", "GroupCompleted", "ProposalAccepted");
		BOOKING.respond(200, 202, 204);

		relay.relayPending();

		List<ReceivedRequest> requests = BOOKING.requests();
		assertThat(requests).hasSize(3);
		assertThat(requests).extracting(request -> (String) JsonPath.read(request.body(), "$.eventId"))
				.containsExactly(ids.get(0).toString(), ids.get(1).toString(), ids.get(2).toString());
		ReceivedRequest first = requests.getFirst();
		assertThat(first.method()).isEqualTo("POST");
		assertThat(first.path()).isEqualTo("/api/v1/internal/events");
		assertThat(first.header("X-Internal-Api-Key")).isEqualTo("dev-only-shareway-internal-api-key");
		assertThat(first.header("Content-Type")).startsWith("application/json");
		assertThat((String) JsonPath.read(first.body(), "$.type")).isEqualTo("TripGroupOpened");
		assertThat((String) JsonPath.read(first.body(), "$.occurredAt")).isEqualTo(START.toString());
		assertThat((String) JsonPath.read(first.body(), "$.groupId")).isNotBlank();

		assertThat(repository.findAllInOrder()).allSatisfy(event -> {
			assertThat(event.getProcessedAt()).isEqualTo(START);
			assertThat(event.getFailedAt()).isNull();
			assertThat(event.getAttempts()).isEqualTo(1);
			assertThat(event.getLastError()).isNull();
		});

		relay.relayPending();
		assertThat(BOOKING.requests()).hasSize(3);
	}

	@ParameterizedTest
	@ValueSource(ints = {500, 502, 503, 401, 404, 405, 408, 429})
	void retryableStatusBacksOffAndStopsTheBatch(int status) {
		List<UUID> ids = enqueue("GroupCompleted", "ProposalAccepted");
		BOOKING.respond(status);

		relay.relayPending();

		assertThat(BOOKING.requests()).hasSize(1);
		OutboxEvent first = repository.findById(ids.get(0)).orElseThrow();
		assertThat(first.getAttempts()).isEqualTo(1);
		assertThat(first.getProcessedAt()).isNull();
		assertThat(first.getFailedAt()).isNull();
		assertThat(first.getNextAttemptAt()).isEqualTo(START.plusSeconds(5));
		assertThat(first.getLastError()).startsWith("HTTP " + status);
		OutboxEvent second = repository.findById(ids.get(1)).orElseThrow();
		assertThat(second.getAttempts()).isZero();
		assertThat(second.getProcessedAt()).isNull();

		// Still backing off: nothing is sent, and the later event does not overtake it.
		clock.advance(Duration.ofSeconds(4));
		relay.relayPending();
		assertThat(BOOKING.requests()).hasSize(1);

		// Once the wait is over both are delivered, in order.
		clock.advance(Duration.ofSeconds(1));
		BOOKING.respond(200, 200);
		relay.relayPending();
		assertThat(BOOKING.requests()).hasSize(3);
		assertThat(BOOKING.requests().subList(1, 3))
				.extracting(request -> (String) JsonPath.read(request.body(), "$.eventId"))
				.containsExactly(ids.get(0).toString(), ids.get(1).toString());
		assertThat(repository.findById(ids.get(0)).orElseThrow().getAttempts()).isEqualTo(2);
		assertThat(repository.findAllInOrder()).allSatisfy(event ->
				assertThat(event.getProcessedAt()).isNotNull());
	}

	@Test
	void consecutiveRetryableErrorsDoubleTheWait() {
		UUID id = enqueue("GroupCompleted").getFirst();

		BOOKING.respond(503);
		relay.relayPending();
		assertThat(repository.findById(id).orElseThrow().getNextAttemptAt()).isEqualTo(START.plusSeconds(5));

		clock.advance(Duration.ofSeconds(5));
		BOOKING.respond(503);
		relay.relayPending();
		OutboxEvent event = repository.findById(id).orElseThrow();
		assertThat(event.getAttempts()).isEqualTo(2);
		assertThat(event.getNextAttemptAt()).isEqualTo(START.plusSeconds(5 + 10));
	}

	@Test
	void timeoutIsRetried() {
		List<UUID> ids = enqueue("GroupCompleted", "ProposalAccepted");
		BOOKING.respondAfter(2500, 200);

		relay.relayPending();

		assertThat(BOOKING.requests()).hasSize(1);
		OutboxEvent first = repository.findById(ids.get(0)).orElseThrow();
		assertThat(first.getAttempts()).isEqualTo(1);
		assertThat(first.getProcessedAt()).isNull();
		assertThat(first.getFailedAt()).isNull();
		assertThat(first.getLastError()).startsWith("I/O error");
		assertThat(repository.findById(ids.get(1)).orElseThrow().getAttempts()).isZero();
	}

	@ParameterizedTest
	@ValueSource(ints = {400, 403, 409, 422})
	void otherClientErrorsAreFailedAndTheBatchContinues(int status) {
		List<UUID> ids = enqueue("GroupRejected", "TripGroupOpened");
		BOOKING.respond(status, 200);

		relay.relayPending();

		assertThat(BOOKING.requests()).hasSize(2);
		OutboxEvent failed = repository.findById(ids.get(0)).orElseThrow();
		assertThat(failed.getFailedAt()).isEqualTo(START);
		assertThat(failed.getProcessedAt()).isNull();
		assertThat(failed.getAttempts()).isEqualTo(1);
		assertThat(failed.getLastError()).startsWith("HTTP " + status);
		assertThat(repository.findById(ids.get(1)).orElseThrow().getProcessedAt()).isEqualTo(START);

		// A failed event is never sent again.
		relay.relayPending();
		assertThat(BOOKING.requests()).hasSize(2);
	}

	private List<UUID> enqueue(String... types) {
		return transactionTemplate.execute(status -> {
			List<UUID> ids = new ArrayList<>();
			for (String type : types) {
				ids.add(outboxService.enqueue(type, Map.of("groupId", UUID.randomUUID())).getId());
			}
			return ids;
		});
	}

	@Test
	void staleRetryCannotOverwriteSuccessfulDelivery() {
		UUID id = enqueue("TripGroupOpened").getFirst();
		OutboxEvent delivered = repository.findById(id).orElseThrow();
		OutboxEvent stale = repository.findById(id).orElseThrow();

		delivered.markProcessed(START);
		repository.saveAndFlush(delivered);
		stale.scheduleRetry(START, Duration.ofSeconds(5), "HTTP 503");

		assertThatThrownBy(() -> repository.saveAndFlush(stale))
				.isInstanceOf(OptimisticLockingFailureException.class);
		assertThat(repository.findById(id).orElseThrow().getProcessedAt()).isEqualTo(START);
	}

	@Test
	void eventIdAndOccurredAtAreFixedAtEnqueueTime() {
		clock.set(START.plus(123456, ChronoUnit.MICROS));
		UUID id = enqueue("TripGroupOpened").getFirst();
		OutboxEvent event = repository.findById(id).orElseThrow();
		assertThat(event.getOccurredAt()).isEqualTo(START.plus(123456, ChronoUnit.MICROS));
		assertThat((String) JsonPath.read(event.getPayload(), "$.occurredAt"))
				.isEqualTo(event.getOccurredAt().toString());
	}
}
