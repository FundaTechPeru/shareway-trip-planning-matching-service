package pe.edu.upc.shareway.tripplanning.trip.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upc.shareway.tripplanning.shared.outbox.OutboxEvent;
import pe.edu.upc.shareway.tripplanning.shared.outbox.OutboxEventRepository;
import pe.edu.upc.shareway.tripplanning.support.TestTokens;
import pe.edu.upc.shareway.tripplanning.support.TripPlanningApi;
import pe.edu.upc.shareway.tripplanning.trip.domain.MatchProposal;
import pe.edu.upc.shareway.tripplanning.trip.domain.MatchProposalStatus;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.MatchProposalRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TripPlanningApi.class)
class DriverAvailabilityControllerTests {

	private static final String DRIVER_ID = "6f1d2a3b-4c5d-4e6f-8a9b-0c1d2e3f4a5b";
	private static final String OTHER_DRIVER_ID = "7a2e3b4c-5d6e-4f7a-9b0c-1d2e3f4a5b6c";
	private static final String PASSENGER_ID = "0b8c7d6e-5f4a-4b3c-9d2e-1f0a9b8c7d6e";
	private static final String ADMIN_ID = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TripPlanningApi api;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private MatchProposalRepository matchProposalRepository;

	@BeforeEach
	void cleanDatabase() {
		api.cleanDatabase();
	}

	@Test
	void createDriverAvailabilityTakesDriverFromTheTokenAndOpensATripGroup() throws Exception {
		String response = mockMvc.perform(post("/api/v1/driver-availabilities")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(DRIVER_ID, "DRIVER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(TripPlanningApi.DRIVER_AVAILABILITY_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.driverId").value(DRIVER_ID))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andReturn().getResponse().getContentAsString();
		String groupId = JsonPath.read(response, "$.id");

		List<OutboxEvent> events = outboxEventRepository.findAllInOrder();
		assertThat(events).extracting(OutboxEvent::getType).containsExactly("TripGroupOpened");
		OutboxEvent event = events.getFirst();
		assertThat(event.getProcessedAt()).isNull();
		assertThat(event.getAttempts()).isZero();
		String payload = event.getPayload();
		assertThat((String) JsonPath.read(payload, "$.eventId")).isEqualTo(event.getId().toString());
		assertThat((String) JsonPath.read(payload, "$.occurredAt")).isEqualTo(event.getOccurredAt().toString());
		assertThat((String) JsonPath.read(payload, "$.type")).isEqualTo("TripGroupOpened");
		assertThat((String) JsonPath.read(payload, "$.groupId")).isEqualTo(groupId);
		assertThat((Integer) JsonPath.read(payload, "$.capacity")).isEqualTo(3);
	}

	@Test
	void createDriverAvailabilityRequiresDriverRole() throws Exception {
		mockMvc.perform(post("/api/v1/driver-availabilities")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(PASSENGER_ID, "PASSENGER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(TripPlanningApi.DRIVER_AVAILABILITY_BODY))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Forbidden"));
		mockMvc.perform(post("/api/v1/driver-availabilities")
						.contentType(MediaType.APPLICATION_JSON)
						.content(TripPlanningApi.DRIVER_AVAILABILITY_BODY))
				.andExpect(status().isUnauthorized());

		assertThat(outboxEventRepository.count()).isZero();
	}

	@Test
	void listDriverAvailabilitiesReturnsOwnForDriverAndAllForAdmin() throws Exception {
		String ownId = api.createDriverAvailability(DRIVER_ID);
		String otherId = api.createDriverAvailability(OTHER_DRIVER_ID);

		mockMvc.perform(get("/api/v1/driver-availabilities")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(DRIVER_ID, "DRIVER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(ownId))
				.andExpect(jsonPath("$[0].status").value("OPEN"));

		mockMvc.perform(get("/api/v1/driver-availabilities")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(ADMIN_ID, "ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", containsInAnyOrder(ownId, otherId)));

		mockMvc.perform(get("/api/v1/driver-availabilities")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(PASSENGER_ID, "PASSENGER")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/driver-availabilities"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void acceptCompletesTheGroupAndQueuesEventsInOrder() throws Exception {
		String groupId = api.createDriverAvailability(DRIVER_ID);
		String tripId = api.createTripRequest(PASSENGER_ID);
		generateProposals(tripId);

		mockMvc.perform(post("/api/v1/driver-availabilities/" + groupId + "/accept")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(DRIVER_ID, "DRIVER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(groupId))
				.andExpect(jsonPath("$.status").value("ACCEPTED"));

		assertThat(proposalsOf(groupId)).extracting(MatchProposal::getStatus)
				.containsExactly(MatchProposalStatus.ACCEPTED);
		mockMvc.perform(get("/api/v1/trip-requests/" + tripId + "/match-proposals")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(PASSENGER_ID, "PASSENGER")))
				.andExpect(jsonPath("$[0].status").value("ACCEPTED"));

		List<OutboxEvent> events = outboxEventRepository.findAllInOrder();
		assertThat(events).extracting(OutboxEvent::getType)
				.containsExactly("TripGroupOpened", "GroupCompleted", "ProposalAccepted");
		assertThat((String) JsonPath.read(events.get(1).getPayload(), "$.groupId")).isEqualTo(groupId);
		assertThat((String) JsonPath.read(events.get(2).getPayload(), "$.groupId")).isEqualTo(groupId);
		assertThat((String) JsonPath.read(events.get(2).getPayload(), "$.driverId")).isEqualTo(DRIVER_ID);
	}

	@Test
	void rejectClosesTheGroupAndQueuesGroupRejected() throws Exception {
		String groupId = api.createDriverAvailability(DRIVER_ID);
		generateProposals(api.createTripRequest(PASSENGER_ID));

		mockMvc.perform(post("/api/v1/driver-availabilities/" + groupId + "/reject")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(DRIVER_ID, "DRIVER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REJECTED"));

		assertThat(proposalsOf(groupId)).extracting(MatchProposal::getStatus)
				.containsExactly(MatchProposalStatus.REJECTED);
		List<OutboxEvent> events = outboxEventRepository.findAllInOrder();
		assertThat(events).extracting(OutboxEvent::getType)
				.containsExactly("TripGroupOpened", "GroupRejected");
		assertThat((String) JsonPath.read(events.get(1).getPayload(), "$.groupId")).isEqualTo(groupId);
		assertThat((String) JsonPath.read(events.get(1).getPayload(), "$.driverId")).isEqualTo(DRIVER_ID);
	}

	@Test
	void acceptOrRejectAGroupOfAnotherDriverIsNotFound() throws Exception {
		String groupId = api.createDriverAvailability(DRIVER_ID);

		for (String action : new String[] {"accept", "reject"}) {
			mockMvc.perform(post("/api/v1/driver-availabilities/" + groupId + "/" + action)
							.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(OTHER_DRIVER_ID, "DRIVER")))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.status").value(404))
					.andExpect(jsonPath("$.error").value("Not found"));
			mockMvc.perform(post("/api/v1/driver-availabilities/" + UUID.randomUUID() + "/" + action)
							.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(DRIVER_ID, "DRIVER")))
					.andExpect(status().isNotFound());
			mockMvc.perform(post("/api/v1/driver-availabilities/" + groupId + "/" + action)
							.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(ADMIN_ID, "ADMIN")))
					.andExpect(status().isForbidden());
			mockMvc.perform(post("/api/v1/driver-availabilities/" + groupId + "/" + action))
					.andExpect(status().isUnauthorized());
		}

		assertThat(outboxEventRepository.findAllInOrder()).extracting(OutboxEvent::getType)
				.containsExactly("TripGroupOpened");
	}

	@Test
	void acceptOrRejectAGroupThatIsNoLongerOpenIsConflict() throws Exception {
		String acceptedId = api.createDriverAvailability(DRIVER_ID);
		String rejectedId = api.createDriverAvailability(DRIVER_ID);
		postAction(acceptedId, "accept");
		postAction(rejectedId, "reject");
		long eventsBefore = outboxEventRepository.count();

		String[][] repeated = {
				{acceptedId, "accept"}, {acceptedId, "reject"},
				{rejectedId, "reject"}, {rejectedId, "accept"}
		};
		for (String[] call : repeated) {
			String path = "/api/v1/driver-availabilities/" + call[0] + "/" + call[1];
			mockMvc.perform(post(path)
							.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(DRIVER_ID, "DRIVER")))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.status").value(409))
					.andExpect(jsonPath("$.error").value("Conflict"))
					.andExpect(jsonPath("$.path").value(path));
		}

		assertThat(outboxEventRepository.count()).isEqualTo(eventsBefore);
	}

	private void generateProposals(String tripId) throws Exception {
		mockMvc.perform(post("/api/v1/trip-requests/" + tripId + "/match-proposals")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(PASSENGER_ID, "PASSENGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
	}

	private void postAction(String groupId, String action) throws Exception {
		mockMvc.perform(post("/api/v1/driver-availabilities/" + groupId + "/" + action)
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(DRIVER_ID, "DRIVER")))
				.andExpect(status().isOk());
	}

	private List<MatchProposal> proposalsOf(String groupId) {
		return matchProposalRepository.findByDriverAvailabilityId(UUID.fromString(groupId));
	}
}
