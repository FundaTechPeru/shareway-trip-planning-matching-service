package pe.edu.upc.shareway.tripplanning.trip.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.upc.shareway.tripplanning.trip.domain.TripRequest;

public interface TripRequestRepository extends JpaRepository<TripRequest, Long> {

  List<TripRequest> findAllByOrderByCreatedAtDesc();
}
