package pe.edu.upc.shareway.tripplanning.shared.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BookingEventsClientTests {

	@ParameterizedTest
	@CsvSource({
			"200, DELIVERED", "201, DELIVERED", "202, DELIVERED", "204, DELIVERED",
			"500, RETRY", "502, RETRY", "503, RETRY", "504, RETRY",
			"401, RETRY", "408, RETRY", "429, RETRY",
			"301, RETRY", "302, RETRY",
			"400, REJECTED", "403, REJECTED", "404, REJECTED", "405, REJECTED", "409, REJECTED",
			"413, REJECTED", "415, REJECTED", "422, REJECTED"
	})
	void classifiesHttpStatuses(int status, DeliveryOutcome expected) {
		assertThat(DeliveryOutcome.ofStatus(status)).isEqualTo(expected);
	}

	@Test
	void connectionRefusedIsRetried() throws IOException {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		BookingEventsClient client = new BookingEventsClient(
				new BookingProperties("http://localhost:" + closedPort + "/", Duration.ofSeconds(2)),
				new InternalApiProperties("dev-only-shareway-internal-api-key"));

		DeliveryResult result = client.send("{}");

		assertThat(result.outcome()).isEqualTo(DeliveryOutcome.RETRY);
		assertThat(result.detail()).startsWith("I/O error");
	}
}
