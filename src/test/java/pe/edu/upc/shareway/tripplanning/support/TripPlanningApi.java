package pe.edu.upc.shareway.tripplanning.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upc.shareway.tripplanning.shared.outbox.OutboxEventRepository;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.DriverAvailabilityRepository;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.MatchProposalRepository;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.TripRequestRepository;

/** Shared request bodies and calls for the MockMvc tests. */
@TestComponent
public class TripPlanningApi {

	/** Includes the legacy passengerId, which the web client still sends and must be ignored. */
	public static final String TRIP_REQUEST_BODY = """
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

	/** Includes the legacy driverId, which must be ignored. */
	public static final String DRIVER_AVAILABILITY_BODY = """
			{
			  "driverId": 10,
			  "operationZone": "Chorrillos",
			  "availableDate": "2026-10-04",
			  "startTime": "07:00:00",
			  "endTime": "09:00:00",
			  "availableSeats": 3
			}
			""";

	private final MockMvc mockMvc;
	private final MatchProposalRepository matchProposalRepository;
	private final DriverAvailabilityRepository driverAvailabilityRepository;
	private final TripRequestRepository tripRequestRepository;
	private final OutboxEventRepository outboxEventRepository;

	public TripPlanningApi(MockMvc mockMvc, MatchProposalRepository matchProposalRepository,
			DriverAvailabilityRepository driverAvailabilityRepository,
			TripRequestRepository tripRequestRepository, OutboxEventRepository outboxEventRepository) {
		this.mockMvc = mockMvc;
		this.matchProposalRepository = matchProposalRepository;
		this.driverAvailabilityRepository = driverAvailabilityRepository;
		this.tripRequestRepository = tripRequestRepository;
		this.outboxEventRepository = outboxEventRepository;
	}

	public void cleanDatabase() {
		matchProposalRepository.deleteAll();
		driverAvailabilityRepository.deleteAll();
		tripRequestRepository.deleteAll();
		outboxEventRepository.deleteAll();
	}

	/** Creates a trip request as the given passenger and returns its id. */
	public String createTripRequest(String passengerId) throws Exception {
		return createAndReturnId("/api/v1/trip-requests", TestTokens.bearer(passengerId, "PASSENGER"),
				TRIP_REQUEST_BODY);
	}

	/** Creates a driver availability (trip group) as the given driver and returns its id. */
	public String createDriverAvailability(String driverId) throws Exception {
		return createAndReturnId("/api/v1/driver-availabilities", TestTokens.bearer(driverId, "DRIVER"),
				DRIVER_AVAILABILITY_BODY);
	}

	private String createAndReturnId(String path, String authorization, String body) throws Exception {
		String response = mockMvc.perform(post(path)
						.header(HttpHeaders.AUTHORIZATION, authorization)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString();
		return JsonPath.read(response, "$.id");
	}
}
