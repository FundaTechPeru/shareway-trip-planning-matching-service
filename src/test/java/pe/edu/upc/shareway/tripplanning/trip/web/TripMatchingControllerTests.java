package pe.edu.upc.shareway.tripplanning.trip.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import pe.edu.upc.shareway.tripplanning.support.TestTokens;
import pe.edu.upc.shareway.tripplanning.support.TripPlanningApi;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TripPlanningApi.class)
class TripMatchingControllerTests {

	private static final String PASSENGER_ID = "0b8c7d6e-5f4a-4b3c-9d2e-1f0a9b8c7d6e";
	private static final String OTHER_PASSENGER_ID = "1c9d8e7f-6a5b-4c4d-8e3f-2a1b0c9d8e7f";
	private static final String DRIVER_ID = "6f1d2a3b-4c5d-4e6f-8a9b-0c1d2e3f4a5b";
	private static final String ADMIN_ID = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TripPlanningApi api;

	@BeforeEach
	void cleanDatabase() {
		api.cleanDatabase();
	}

	@Test
	void createTripRequestTakesPassengerFromTheToken() throws Exception {
		// The web client still sends passengerId; it must be accepted and ignored.
		mockMvc.perform(post("/api/v1/trip-requests")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(PASSENGER_ID, "PASSENGER"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(TripPlanningApi.TRIP_REQUEST_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.passengerId").value(PASSENGER_ID))
				.andExpect(jsonPath("$.status").value("PENDING_MATCH"));
	}

	@Test
	void requestWithoutTokenIsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/trip-requests")
						.contentType(MediaType.APPLICATION_JSON)
						.content(TripPlanningApi.TRIP_REQUEST_BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Missing bearer token"))
				.andExpect(jsonPath("$.path").value("/api/v1/trip-requests"))
				.andExpect(jsonPath("$.timestamp").exists());

		mockMvc.perform(get("/api/v1/trip-requests"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/trip-requests").header(HttpHeaders.AUTHORIZATION, "Basic abc"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void gatewayIdentityHeadersAreNoLongerTrusted() throws Exception {
		mockMvc.perform(post("/api/v1/trip-requests")
						.header("X-User-Id", PASSENGER_ID)
						.header("X-User-Role", "PASSENGER")
						.contentType(MediaType.APPLICATION_JSON)
						.content(TripPlanningApi.TRIP_REQUEST_BODY))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void invalidTokensAreUnauthorized() throws Exception {
		String[] invalid = {
				"Bearer not-a-jwt",
				TestTokens.expired(PASSENGER_ID, "PASSENGER"),
				TestTokens.signedWithAnotherSecret(PASSENGER_ID, "PASSENGER"),
				TestTokens.bearer("25", "PASSENGER"),
				TestTokens.bearer("1-2-3-4-5", "PASSENGER")
		};
		for (String authorization : invalid) {
			mockMvc.perform(get("/api/v1/trip-requests").header(HttpHeaders.AUTHORIZATION, authorization))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.message").value("Invalid or expired token"));
		}
	}

	@Test
	void createTripRequestRequiresPassengerRole() throws Exception {
		String[] notPassenger = {
				TestTokens.bearer(DRIVER_ID, "DRIVER"),
				TestTokens.bearer(ADMIN_ID, "ADMIN"),
				TestTokens.bearer(PASSENGER_ID, "UNKNOWN"),
				TestTokens.withoutRole(PASSENGER_ID)
		};
		for (String authorization : notPassenger) {
			mockMvc.perform(post("/api/v1/trip-requests")
							.header(HttpHeaders.AUTHORIZATION, authorization)
							.contentType(MediaType.APPLICATION_JSON)
							.content(TripPlanningApi.TRIP_REQUEST_BODY))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.status").value(403))
					.andExpect(jsonPath("$.error").value("Forbidden"))
					.andExpect(jsonPath("$.path").value("/api/v1/trip-requests"));
		}
	}

	@Test
	void listTripRequestsReturnsOnlyTheRequesterTripsUnlessAdmin() throws Exception {
		String ownTripId = api.createTripRequest(PASSENGER_ID);
		String otherTripId = api.createTripRequest(OTHER_PASSENGER_ID);

		mockMvc.perform(get("/api/v1/trip-requests")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(PASSENGER_ID, "PASSENGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(ownTripId))
				.andExpect(jsonPath("$[0].passengerId").value(PASSENGER_ID));

		mockMvc.perform(get("/api/v1/trip-requests")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(DRIVER_ID, "DRIVER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		mockMvc.perform(get("/api/v1/trip-requests")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(ADMIN_ID, "ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", containsInAnyOrder(ownTripId, otherTripId)));
	}

	@Test
	void matchProposalsOfAnotherPassengerTripAreNotFound() throws Exception {
		api.createDriverAvailability(DRIVER_ID);
		String tripId = api.createTripRequest(PASSENGER_ID);
		String proposalsPath = "/api/v1/trip-requests/" + tripId + "/match-proposals";

		mockMvc.perform(post(proposalsPath)
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(OTHER_PASSENGER_ID, "PASSENGER")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not found"))
				.andExpect(jsonPath("$.path").value(proposalsPath));
		mockMvc.perform(get(proposalsPath)
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(OTHER_PASSENGER_ID, "PASSENGER")))
				.andExpect(status().isNotFound());
		mockMvc.perform(get(proposalsPath)
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(DRIVER_ID, "DRIVER")))
				.andExpect(status().isNotFound());
		mockMvc.perform(post(proposalsPath))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post(proposalsPath)
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(PASSENGER_ID, "PASSENGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].driverId").value(DRIVER_ID));
		mockMvc.perform(get(proposalsPath)
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(ADMIN_ID, "ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void matchProposalsCarryTheirTripGroup() throws Exception {
		String groupId = api.createDriverAvailability(DRIVER_ID);
		String tripId = api.createTripRequest(PASSENGER_ID);

		mockMvc.perform(post("/api/v1/trip-requests/" + tripId + "/match-proposals")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(PASSENGER_ID, "PASSENGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].groupId").value(groupId))
				.andExpect(jsonPath("$[0].availableDate").value("2026-10-04"))
				.andExpect(jsonPath("$[0].startTime").value("07:00:00"))
				.andExpect(jsonPath("$[0].endTime").value("09:00:00"))
				.andExpect(jsonPath("$[0].status").value("PROPOSED"));

		// Listing reads the proposals back from the database, with the group loaded.
		mockMvc.perform(get("/api/v1/trip-requests/" + tripId + "/match-proposals")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(PASSENGER_ID, "PASSENGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].groupId").value(groupId))
				.andExpect(jsonPath("$[0].startTime").value("07:00:00"));
	}

	@Test
	void matchingIgnoresGroupsThatAreNoLongerOpen() throws Exception {
		String rejectedGroupId = api.createDriverAvailability(DRIVER_ID);
		mockMvc.perform(post("/api/v1/driver-availabilities/" + rejectedGroupId + "/reject")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(DRIVER_ID, "DRIVER")))
				.andExpect(status().isOk());
		String tripId = api.createTripRequest(PASSENGER_ID);

		mockMvc.perform(post("/api/v1/trip-requests/" + tripId + "/match-proposals")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(PASSENGER_ID, "PASSENGER")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void matchProposalsOfUnknownTripAreNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/trip-requests/" + UUID.randomUUID() + "/match-proposals")
						.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(ADMIN_ID, "ADMIN")))
				.andExpect(status().isNotFound());
	}
}
