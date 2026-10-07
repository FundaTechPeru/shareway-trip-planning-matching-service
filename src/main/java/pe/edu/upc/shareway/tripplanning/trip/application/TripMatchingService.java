package pe.edu.upc.shareway.tripplanning.trip.application;

import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.shareway.tripplanning.trip.domain.DriverAvailability;
import pe.edu.upc.shareway.tripplanning.trip.domain.MatchProposal;
import pe.edu.upc.shareway.tripplanning.trip.domain.TripRequest;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.DriverAvailabilityRepository;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.MatchProposalRepository;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.TripRequestRepository;
import pe.edu.upc.shareway.tripplanning.trip.web.CreateDriverAvailabilityRequest;
import pe.edu.upc.shareway.tripplanning.trip.web.CreateTripRequest;

@Service
public class TripMatchingService {

  private static final BigDecimal BASE_FARE = BigDecimal.valueOf(4.50);
  private static final BigDecimal PRICE_PER_SEAT = BigDecimal.valueOf(3.20);

  private final TripRequestRepository tripRequestRepository;
  private final DriverAvailabilityRepository driverAvailabilityRepository;
  private final MatchProposalRepository matchProposalRepository;

  public TripMatchingService(
      TripRequestRepository tripRequestRepository,
      DriverAvailabilityRepository driverAvailabilityRepository,
      MatchProposalRepository matchProposalRepository
  ) {
    this.tripRequestRepository = tripRequestRepository;
    this.driverAvailabilityRepository = driverAvailabilityRepository;
    this.matchProposalRepository = matchProposalRepository;
  }

  @Transactional
  public TripRequest createTripRequest(CreateTripRequest request) {
    TripRequest tripRequest = new TripRequest(
        request.passengerId(),
        request.originLabel(),
        request.destinationLabel(),
        request.operationZone(),
        request.originLat(),
        request.originLng(),
        request.destinationLat(),
        request.destinationLng(),
        request.travelDate(),
        request.earliestDeparture(),
        request.latestDeparture(),
        request.seatsRequested()
    );

    return tripRequestRepository.save(tripRequest);
  }

  @Transactional
  public DriverAvailability createDriverAvailability(CreateDriverAvailabilityRequest request) {
    DriverAvailability availability = new DriverAvailability(
        request.driverId(),
        request.operationZone(),
        request.availableDate(),
        request.startTime(),
        request.endTime(),
        request.availableSeats()
    );

    return driverAvailabilityRepository.save(availability);
  }

  @Transactional(readOnly = true)
  public List<TripRequest> listTripRequests() {
    return tripRequestRepository.findAllByOrderByCreatedAtDesc();
  }

  @Transactional
  public List<MatchProposal> generateMatchProposals(Long tripRequestId) {
    List<MatchProposal> existingProposals =
        matchProposalRepository.findByTripRequestIdOrderByCreatedAtDesc(tripRequestId);
    if (!existingProposals.isEmpty()) {
      return deduplicateByDriverId(existingProposals);
    }

    TripRequest tripRequest = tripRequestRepository.findById(tripRequestId)
        .orElseThrow(() -> new EntityNotFoundException("Trip request not found: " + tripRequestId));

    List<DriverAvailability> candidates = driverAvailabilityRepository.findCandidates(
        tripRequest.getOperationZone(),
        tripRequest.getTravelDate(),
        tripRequest.getEarliestDeparture(),
        tripRequest.getLatestDeparture(),
        tripRequest.getSeatsRequested()
    );

    Set<Long> proposedDriverIds = new HashSet<>();
    List<MatchProposal> proposals = candidates.stream()
        .filter(candidate -> proposedDriverIds.add(candidate.getDriverId()))
        .limit(5)
        .map(candidate -> new MatchProposal(
            tripRequest,
            candidate.getDriverId(),
            estimateFare(tripRequest),
            estimateDurationMinutes(tripRequest)
        ))
        .toList();

    return matchProposalRepository.saveAll(proposals);
  }

  @Transactional(readOnly = true)
  public List<MatchProposal> listMatchProposals(Long tripRequestId) {
    return deduplicateByDriverId(
        matchProposalRepository.findByTripRequestIdOrderByCreatedAtDesc(tripRequestId)
    );
  }

  private BigDecimal estimateFare(TripRequest tripRequest) {
    return BASE_FARE
        .add(PRICE_PER_SEAT.multiply(BigDecimal.valueOf(tripRequest.getSeatsRequested())))
        .setScale(2, RoundingMode.HALF_UP);
  }

  private int estimateDurationMinutes(TripRequest tripRequest) {
    int windowMinutes = tripRequest.getLatestDeparture().toSecondOfDay()
        - tripRequest.getEarliestDeparture().toSecondOfDay();
    return Math.max(15, windowMinutes / 60);
  }

  private List<MatchProposal> deduplicateByDriverId(List<MatchProposal> proposals) {
    LinkedHashMap<Long, MatchProposal> proposalsByDriver = new LinkedHashMap<>();
    for (MatchProposal proposal : proposals) {
      proposalsByDriver.putIfAbsent(proposal.getDriverId(), proposal);
    }
    return new ArrayList<>(proposalsByDriver.values());
  }
}
