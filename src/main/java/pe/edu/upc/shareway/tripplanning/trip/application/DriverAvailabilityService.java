package pe.edu.upc.shareway.tripplanning.trip.application;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.shareway.tripplanning.shared.api.ConflictException;
import pe.edu.upc.shareway.tripplanning.shared.outbox.OutboxService;
import pe.edu.upc.shareway.tripplanning.shared.security.AuthenticatedUser;
import pe.edu.upc.shareway.tripplanning.trip.domain.DriverAvailability;
import pe.edu.upc.shareway.tripplanning.trip.domain.DriverAvailabilityStatus;
import pe.edu.upc.shareway.tripplanning.trip.domain.MatchProposalStatus;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.DriverAvailabilityRepository;
import pe.edu.upc.shareway.tripplanning.trip.infrastructure.MatchProposalRepository;
import pe.edu.upc.shareway.tripplanning.trip.web.CreateDriverAvailabilityRequest;

/**
 * Driver availabilities, which Booking &amp; Pricing calls trip groups. Every state change queues its
 * integration events in the outbox within the same transaction.
 */
@Service
public class DriverAvailabilityService {

  private final DriverAvailabilityRepository driverAvailabilityRepository;
  private final MatchProposalRepository matchProposalRepository;
  private final OutboxService outboxService;

  public DriverAvailabilityService(
      DriverAvailabilityRepository driverAvailabilityRepository,
      MatchProposalRepository matchProposalRepository,
      OutboxService outboxService
  ) {
    this.driverAvailabilityRepository = driverAvailabilityRepository;
    this.matchProposalRepository = matchProposalRepository;
    this.outboxService = outboxService;
  }

  @Transactional
  public DriverAvailability createDriverAvailability(UUID driverId,
      CreateDriverAvailabilityRequest request) {
    DriverAvailability availability = driverAvailabilityRepository.save(new DriverAvailability(
        driverId,
        request.operationZone(),
        request.availableDate(),
        request.startTime(),
        request.endTime(),
        request.availableSeats()
    ));

    outboxService.enqueue(DriverGroupEvents.TRIP_GROUP_OPENED, Map.of(
        "groupId", availability.getId(),
        "capacity", availability.getAvailableSeats()
    ));
    return availability;
  }

  /** The driver sees their own availabilities; an admin sees all of them. */
  @Transactional(readOnly = true)
  public List<DriverAvailability> listDriverAvailabilities(AuthenticatedUser requester) {
    if (requester.isAdmin()) {
      return driverAvailabilityRepository.findAllByOrderByAvailableDateAscStartTimeAsc();
    }
    return driverAvailabilityRepository.findByDriverIdOrderByAvailableDateAscStartTimeAsc(
        requester.id());
  }

  /**
   * US-21: the driver accepts the group. Its PROPOSED proposals become ACCEPTED and GroupCompleted
   * and ProposalAccepted are queued, in that order.
   */
  @Transactional
  public DriverAvailability accept(UUID availabilityId, AuthenticatedUser driver) {
    UUID groupId = transitionFromOpen(availabilityId, driver, DriverAvailabilityStatus.ACCEPTED);
    matchProposalRepository.transitionStatusByDriverAvailability(
        groupId, MatchProposalStatus.PROPOSED, MatchProposalStatus.ACCEPTED);

    outboxService.enqueue(DriverGroupEvents.GROUP_COMPLETED, Map.of("groupId", groupId));
    outboxService.enqueue(DriverGroupEvents.PROPOSAL_ACCEPTED, Map.of(
        "groupId", groupId,
        "driverId", driver.id()
    ));
    return reload(groupId);
  }

  /** US-21: the driver rejects the group. Its PROPOSED proposals become REJECTED. */
  @Transactional
  public DriverAvailability reject(UUID availabilityId, AuthenticatedUser driver) {
    UUID groupId = transitionFromOpen(availabilityId, driver, DriverAvailabilityStatus.REJECTED);
    matchProposalRepository.transitionStatusByDriverAvailability(
        groupId, MatchProposalStatus.PROPOSED, MatchProposalStatus.REJECTED);

    outboxService.enqueue(DriverGroupEvents.GROUP_REJECTED, Map.of(
        "groupId", groupId,
        "driverId", driver.id()
    ));
    return reload(groupId);
  }

  /**
   * Moves an OPEN availability owned by {@code driver} to {@code target}. Availabilities of other
   * drivers are reported as not found so their existence is not disclosed.
   */
  private UUID transitionFromOpen(UUID availabilityId, AuthenticatedUser driver,
      DriverAvailabilityStatus target) {
    DriverAvailability availability = driverAvailabilityRepository.findById(availabilityId)
        .filter(found -> found.getDriverId().equals(driver.id()))
        .orElseThrow(() -> new EntityNotFoundException(
            "Driver availability not found: " + availabilityId));
    if (availability.getStatus() != DriverAvailabilityStatus.OPEN
        || driverAvailabilityRepository.transitionStatus(
            availabilityId, DriverAvailabilityStatus.OPEN, target) == 0) {
      throw new ConflictException("Driver availability " + availabilityId
          + " is no longer OPEN; it cannot be accepted or rejected again");
    }
    return availabilityId;
  }

  private DriverAvailability reload(UUID availabilityId) {
    return driverAvailabilityRepository.findById(availabilityId)
        .orElseThrow(() -> new EntityNotFoundException(
            "Driver availability not found: " + availabilityId));
  }
}
