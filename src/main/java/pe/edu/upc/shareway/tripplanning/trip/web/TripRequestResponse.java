package pe.edu.upc.shareway.tripplanning.trip.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import pe.edu.upc.shareway.tripplanning.trip.domain.TripRequest;
import pe.edu.upc.shareway.tripplanning.trip.domain.TripRequestStatus;

public record TripRequestResponse(
    Long id,
    Long passengerId,
    String originLabel,
    String destinationLabel,
    String operationZone,
    LocalDate travelDate,
    LocalTime earliestDeparture,
    LocalTime latestDeparture,
    int seatsRequested,
    TripRequestStatus status,
    Instant createdAt
) {
  public static TripRequestResponse from(TripRequest tripRequest) {
    return new TripRequestResponse(
        tripRequest.getId(),
        tripRequest.getPassengerId(),
        tripRequest.getOriginLabel(),
        tripRequest.getDestinationLabel(),
        tripRequest.getOperationZone(),
        tripRequest.getTravelDate(),
        tripRequest.getEarliestDeparture(),
        tripRequest.getLatestDeparture(),
        tripRequest.getSeatsRequested(),
        tripRequest.getStatus(),
        tripRequest.getCreatedAt()
    );
  }
}
