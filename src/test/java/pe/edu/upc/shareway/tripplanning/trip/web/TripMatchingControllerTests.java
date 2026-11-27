package pe.edu.upc.shareway.tripplanning.trip.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.DriverAvailabilityRepository;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.MatchProposalRepository;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.TripRequestRepository;

@SpringBootTest
@AutoConfigureMockMvc
class TripMatchingControllerTests {

	private static final String PASSENGER_ID = "0b8c7d6e-5f4a-4b3c-9d2e-1f0a9b8c7d6e";
	private static final String OTHER_PASSENGER_ID = "1c9d8e7f-6a5b-4c4d-8e3f-2a1b0c9d8e7f";
	private static final String DRIVER_ID = "6f1d2a3b-4c5d-4e6f-8a9b-0c1d2e3f4a5b";
	private static final String ADMIN_ID = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

	// The web client still sends passengerId; it must be accepted and ignored.
	private static final String TRIP_REQUEST_BODY = """
			{
			  "passengerId": 25,
			  "originLabel": "UPC Monterrico",
			  "destinationLabel": "Chorrillos",
			  "operationZone": "Chorrillos",
			  "originLat": -12.104800,
			  "originLng": -76.963200,
			  "destinationLat": -12.172100,
			  "destinationLng": -77.016400,
			  "travelDate": "2026-10-04",
			  "earliestDeparture": "07:30:00",
			  "latestDeparture": "08:30:00",
			  "seatsRequested": 1
			}
			""";

	private static final String DRIVER_AVAILABILITY_BODY = """
			{
			  "driverId": 10,
			  "operationZone": "Chorrillos",
			  "availableDate": "2026-10-04",
			  "startTime": "07:00:00",
			  "endTime": "09:00:00",
			  "availableSeats": 3
			}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MatchProposalRepository matchProposalRepository;

	@Autowired
	private DriverAvailabilityRepository driverAvailabilityRepository;

	@Autowired
	private TripRequestRepository tripRequestRepository;

	@BeforeEach
	void cleanDatabase() {
		matchProposalRepository.deleteAll();
		driverAvailabilityRepository.deleteAll();
		tripRequestRepository.deleteAll();
	}

	@Test
	void createTripRequestTakesPassengerFromGatewayHeaders() throws Exception {
		mockMvc.perform(as(post("/api/v1/trip-requests"), PASSENGER_ID, "PASSENGER")
						.contentType(MediaType.APPLICATION_JSON)
						.content(TRIP_REQUEST_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.passengerId").value(PASSENGER_ID))
				.andExpect(jsonPath("$.status").value("PENDING_MATCH"));
	}

	@Test
	void createDriverAvailabilityTakesDriverFromGatewayHeaders() throws Exception {
		mockMvc.perform(as(post("/api/v1/driver-availabilities"), DRIVER_ID, "DRIVER")
						.contentType(MediaType.APPLICATION_JSON)
						.content(DRIVER_AVAILABILITY_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.driverId").value(DRIVER_ID));
	}

	@Test
	void requestWithoutUserIdHeaderIsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/trip-requests")
						.header("X-User-Role", "PASSENGER")
						.contentType(MediaType.APPLICATION_JSON)
						.content(TRIP_REQUEST_BODY))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Missing X-User-Id header"))
				.andExpect(jsonPath("$.path").value("/api/v1/trip-requests"))
				.andExpect(jsonPath("$.timestamp").exists());

		mockMvc.perform(get("/api/v1/trip-requests"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void requestWithMalformedUserIdHeaderIsUnauthorized() throws Exception {
		mockMvc.perform(as(get("/api/v1/trip-requests"), "25", "PASSENGER"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("X-User-Id header must be a UUID"));

		mockMvc.perform(as(get("/api/v1/trip-requests"), "1-2-3-4-5", "PASSENGER"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void createTripRequestRequiresPassengerRole() throws Exception {
		for (String role : new String[] {"DRIVER", "ADMIN", "UNKNOWN"}) {
			mockMvc.perform(as(post("/api/v1/trip-requests"), DRIVER_ID, role)
							.contentType(MediaType.APPLICATION_JSON)
							.content(TRIP_REQUEST_BODY))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.status").value(403))
					.andExpect(jsonPath("$.error").value("Forbidden"))
					.andExpect(jsonPath("$.path").value("/api/v1/trip-requests"));
		}

		mockMvc.perform(post("/api/v1/trip-requests")
						.header("X-User-Id", PASSENGER_ID)
						.contentType(MediaType.APPLICATION_JSON)
						.content(TRIP_REQUEST_BODY))
				.andExpect(status().isForbidden());
	}

	@Test
	void createDriverAvailabilityRequiresDriverRole() throws Exception {
		mockMvc.perform(as(post("/api/v1/driver-availabilities"), PASSENGER_ID, "PASSENGER")
						.contentType(MediaType.APPLICATION_JSON)
						.content(DRIVER_AVAILABILITY_BODY))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Forbidden"));
	}

	@Test
	void listTripRequestsReturnsOnlyTheRequesterTripsUnlessAdmin() throws Exception {
		String ownTripId = createTripRequest(PASSENGER_ID);
		String otherTripId = createTripRequest(OTHER_PASSENGER_ID);

		mockMvc.perform(as(get("/api/v1/trip-requests"), PASSENGER_ID, "PASSENGER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].id").value(ownTripId))
				.andExpect(jsonPath("$[0].passengerId").value(PASSENGER_ID));

		mockMvc.perform(as(get("/api/v1/trip-requests"), DRIVER_ID, "DRIVER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		mockMvc.perform(as(get("/api/v1/trip-requests"), ADMIN_ID, "ADMIN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", containsInAnyOrder(ownTripId, otherTripId)));
	}

	@Test
	void matchProposalsOfAnotherPassengerTripAreNotFound() throws Exception {
		mockMvc.perform(as(post("/api/v1/driver-availabilities"), DRIVER_ID, "DRIVER")
						.contentType(MediaType.APPLICATION_JSON)
						.content(DRIVER_AVAILABILITY_BODY))
				.andExpect(status().isCreated());
		String tripId = createTripRequest(PASSENGER_ID);
		String proposalsPath = "/api/v1/trip-requests/" + tripId + "/match-proposals";

		mockMvc.perform(as(post(proposalsPath), OTHER_PASSENGER_ID, "PASSENGER"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.error").value("Not found"))
				.andExpect(jsonPath("$.path").value(proposalsPath));
		mockMvc.perform(as(get(proposalsPath), OTHER_PASSENGER_ID, "PASSENGER"))
				.andExpect(status().isNotFound());
		mockMvc.perform(as(get(proposalsPath), DRIVER_ID, "DRIVER"))
				.andExpect(status().isNotFound());
		mockMvc.perform(post(proposalsPath))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(as(post(proposalsPath), PASSENGER_ID, "PASSENGER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].driverId").value(DRIVER_ID));
		mockMvc.perform(as(get(proposalsPath), PASSENGER_ID, "PASSENGER"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
		mockMvc.perform(as(get(proposalsPath), ADMIN_ID, "ADMIN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)));
	}

	@Test
	void matchProposalsOfUnknownTripAreNotFound() throws Exception {
		mockMvc.perform(as(get("/api/v1/trip-requests/" + UUID.randomUUID() + "/match-proposals"),
						ADMIN_ID, "ADMIN"))
				.andExpect(status().isNotFound());
	}

	private String createTripRequest(String passengerId) throws Exception {
		String response = mockMvc.perform(as(post("/api/v1/trip-requests"), passengerId, "PASSENGER")
						.contentType(MediaType.APPLICATION_JSON)
						.content(TRIP_REQUEST_BODY))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,
			String userId, String role) {
		return request.header("X-User-Id", userId).header("X-User-Role", role);
	}
}
