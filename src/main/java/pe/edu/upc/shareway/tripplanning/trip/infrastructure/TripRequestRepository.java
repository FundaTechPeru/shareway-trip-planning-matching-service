package pe.edu.upc.shareway.tripplanning.trip.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upc.shareway.tripplanning.trip.domain.TripRequest;

public interface TripRequestRepository extends JpaRepository<TripRequest, UUID> {

  List<TripRequest> findAllByOrderByCreatedAtDesc();

  List<TripRequest> findByPassengerIdOrderByCreatedAtDesc(UUID passengerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select trip from TripRequest trip where trip.id = :id")
  Optional<TripRequest> findForMatching(@Param("id") UUID id);
}
