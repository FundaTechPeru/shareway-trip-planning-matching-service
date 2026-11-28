package pe.edu.upc.shareway.tripplanning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import pe.edu.upc.shareway.tripplanning.shared.outbox.OutboxEventRepository;
import pe.edu.upc.shareway.tripplanning.shared.security.AuthenticatedUser;
import pe.edu.upc.shareway.tripplanning.shared.security.UserRole;
import pe.edu.upc.shareway.tripplanning.trip.application.DriverAvailabilityService;
import pe.edu.upc.shareway.tripplanning.trip.application.TripMatchingService;
import pe.edu.upc.shareway.tripplanning.trip.domain.MatchProposal;
import pe.edu.upc.shareway.tripplanning.trip.domain.TripRequest;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.DriverAvailabilityRepository;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.MatchProposalRepository;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.TripRequestRepository;
import pe.edu.upc.shareway.tripplanning.trip.web.CreateDriverAvailabilityRequest;
import pe.edu.upc.shareway.tripplanning.trip.web.CreateTripRequest;

@SpringBootTest
@AutoConfigureMockMvc
class SharewayTripPlanningMatchingServiceApplicationTests {

	private static final UUID DRIVER_ID = UUID.fromString("6f1d2a3b-4c5d-4e6f-8a9b-0c1d2e3f4a5b");
	private static final UUID PASSENGER_ID = UUID.fromString("0b8c7d6e-5f4a-4b3c-9d2e-1f0a9b8c7d6e");
	private static final AuthenticatedUser PASSENGER = new AuthenticatedUser(PASSENGER_ID, UserRole.PASSENGER);

	@Autowired
	private TripMatchingService tripMatchingService;

	@Autowired
	private DriverAvailabilityService driverAvailabilityService;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private MatchProposalRepository matchProposalRepository;

	@Autowired
	private DriverAvailabilityRepository driverAvailabilityRepository;

	@Autowired
	private TripRequestRepository tripRequestRepository;

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void cleanDatabase() {
		matchProposalRepository.deleteAll();
		driverAvailabilityRepository.deleteAll();
		tripRequestRepository.deleteAll();
		outboxEventRepository.deleteAll();
	}

	@Test
	void contextLoads() {
	}

	@Test
	void generateMatchProposalsIsIdempotentForTheSameTripRequest() {
		driverAvailabilityService.createDriverAvailability(DRIVER_ID, new CreateDriverAvailabilityRequest(
				"Chorrillos",
				LocalDate.of(2026, 10, 4),
				LocalTime.of(7, 0),
				LocalTime.of(9, 0),
				3
		));
		TripRequest tripRequest = tripMatchingService.createTripRequest(PASSENGER_ID, new CreateTripRequest(
				"UPC Monterrico",
				"Chorrillos",
				"Chorrillos",
				BigDecimal.valueOf(-12.104800),
				BigDecimal.valueOf(-76.963200),
				BigDecimal.valueOf(-12.172100),
				BigDecimal.valueOf(-77.016400),
				LocalDate.of(2026, 10, 4),
				LocalTime.of(7, 30),
				LocalTime.of(8, 30),
				1
		));

		List<MatchProposal> firstRun = tripMatchingService.generateMatchProposals(tripRequest.getId(), PASSENGER);
		List<MatchProposal> secondRun = tripMatchingService.generateMatchProposals(tripRequest.getId(), PASSENGER);

		assertThat(firstRun).hasSize(1);
		assertThat(secondRun).hasSize(1);
		assertThat(matchProposalRepository.findByTripRequestIdOrderByCreatedAtDesc(tripRequest.getId()))
				.hasSize(1);
	}

	@Test
	void generateMatchProposalsReturnsOneProposalPerDriver() {
		CreateDriverAvailabilityRequest availability = new CreateDriverAvailabilityRequest(
				"Chorrillos",
				LocalDate.of(2026, 10, 4),
				LocalTime.of(7, 0),
				LocalTime.of(9, 0),
				3
		);
		driverAvailabilityService.createDriverAvailability(DRIVER_ID, availability);
		driverAvailabilityService.createDriverAvailability(DRIVER_ID, availability);
		TripRequest tripRequest = tripMatchingService.createTripRequest(PASSENGER_ID, new CreateTripRequest(
				"UPC Monterrico",
				"Chorrillos",
				"Chorrillos",
				BigDecimal.valueOf(-12.104800),
				BigDecimal.valueOf(-76.963200),
				BigDecimal.valueOf(-12.172100),
				BigDecimal.valueOf(-77.016400),
				LocalDate.of(2026, 10, 4),
				LocalTime.of(7, 30),
				LocalTime.of(8, 30),
				1
		));

		List<MatchProposal> proposals = tripMatchingService.generateMatchProposals(tripRequest.getId(), PASSENGER);

		assertThat(proposals)
				.hasSize(1)
				.extracting(MatchProposal::getDriverId)
				.containsExactly(DRIVER_ID);
	}

	@Test
	void openApiDocsDescribeMainTp1Endpoints() throws Exception {
		String openApiJson = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(openApiJson)
				.contains("Register driver availability")
				.contains("Create trip request")
				.contains("Generate match proposals")
				.contains("Accept trip group (US-21)")
				.contains("Reject trip group (US-21)")
				.contains("bearerAuth")
				.contains("groupId")
				.doesNotContain("X-User-Id");
	}
}
