package pe.edu.upc.shareway.tripplanning.trip.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upc.shareway.tripplanning.trip.domain.DriverAvailability;
import pe.edu.upc.shareway.tripplanning.trip.domain.DriverAvailabilityStatus;

public interface DriverAvailabilityRepository extends JpaRepository<DriverAvailability, UUID> {

  /** Locks compatible groups until the proposals are saved, so closure cannot miss a new one. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      select availability
      from DriverAvailability availability
      where availability.status = pe.edu.upc.shareway.tripplanning.trip.domain.DriverAvailabilityStatus.OPEN
        and lower(availability.operationZone) = lower(:operationZone)
        and availability.availableDate = :travelDate
        and availability.startTime <= :latestDeparture
        and availability.endTime >= :earliestDeparture
        and availability.availableSeats >= :seatsRequested
      order by availability.availableSeats asc, availability.startTime asc, availability.id asc
      """)
  List<DriverAvailability> findCandidates(
      @Param("operationZone") String operationZone,
      @Param("travelDate") LocalDate travelDate,
      @Param("earliestDeparture") LocalTime earliestDeparture,
      @Param("latestDeparture") LocalTime latestDeparture,
      @Param("seatsRequested") int seatsRequested
  );

  List<DriverAvailability> findByDriverIdOrderByAvailableDateAscStartTimeAsc(UUID driverId);

  List<DriverAvailability> findAllByOrderByAvailableDateAscStartTimeAsc();

  /**
   * Moves the availability from {@code from} to {@code to} only while it is still in {@code from},
   * so two concurrent requests cannot both succeed. Returns the number of updated rows (0 or 1).
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      update DriverAvailability availability
      set availability.status = :to
      where availability.id = :id
        and availability.status = :from
      """)
  int transitionStatus(
      @Param("id") UUID id,
      @Param("from") DriverAvailabilityStatus from,
      @Param("to") DriverAvailabilityStatus to
  );
}
