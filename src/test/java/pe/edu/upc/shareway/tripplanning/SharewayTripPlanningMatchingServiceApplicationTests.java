package pe.edu.upc.shareway.tripplanning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

	@Autowired
	private TripMatchingService tripMatchingService;

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
	}

	@Test
	void contextLoads() {
	}

	@Test
	void generateMatchProposalsIsIdempotentForTheSameTripRequest() {
		tripMatchingService.createDriverAvailability(new CreateDriverAvailabilityRequest(
				10L,
				"Chorrillos",
				LocalDate.of(2026, 10, 4),
				LocalTime.of(7, 0),
				LocalTime.of(9, 0),
				3
		));
		TripRequest tripRequest = tripMatchingService.createTripRequest(new CreateTripRequest(
				25L,
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

		List<MatchProposal> firstRun = tripMatchingService.generateMatchProposals(tripRequest.getId());
		List<MatchProposal> secondRun = tripMatchingService.generateMatchProposals(tripRequest.getId());

		assertThat(firstRun).hasSize(1);
		assertThat(secondRun).hasSize(1);
		assertThat(matchProposalRepository.findByTripRequestIdOrderByCreatedAtDesc(tripRequest.getId()))
				.hasSize(1);
	}

	@Test
	void generateMatchProposalsReturnsOneProposalPerDriver() {
		CreateDriverAvailabilityRequest availability = new CreateDriverAvailabilityRequest(
				10L,
				"Chorrillos",
				LocalDate.of(2026, 10, 4),
				LocalTime.of(7, 0),
				LocalTime.of(9, 0),
				3
		);
		tripMatchingService.createDriverAvailability(availability);
		tripMatchingService.createDriverAvailability(availability);
		TripRequest tripRequest = tripMatchingService.createTripRequest(new CreateTripRequest(
				25L,
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

		List<MatchProposal> proposals = tripMatchingService.generateMatchProposals(tripRequest.getId());

		assertThat(proposals)
				.hasSize(1)
				.extracting(MatchProposal::getDriverId)
				.containsExactly(10L);
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
				.contains("Generate match proposals");
	}
}
