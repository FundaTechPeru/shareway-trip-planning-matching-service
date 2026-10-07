package pe.edu.upc.shareway.tripplanning.trip.infrastructure;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upc.shareway.tripplanning.trip.domain.DriverAvailability;

public interface DriverAvailabilityRepository extends JpaRepository<DriverAvailability, Long> {

  @Query("""
      select availability
      from DriverAvailability availability
      where lower(availability.operationZone) = lower(:operationZone)
        and availability.availableDate = :travelDate
        and availability.startTime <= :latestDeparture
        and availability.endTime >= :earliestDeparture
        and availability.availableSeats >= :seatsRequested
      order by availability.availableSeats asc, availability.startTime asc
      """)
  List<DriverAvailability> findCandidates(
      @Param("operationZone") String operationZone,
      @Param("travelDate") LocalDate travelDate,
      @Param("earliestDeparture") LocalTime earliestDeparture,
      @Param("latestDeparture") LocalTime latestDeparture,
      @Param("seatsRequested") int seatsRequested
  );
}
