package pe.edu.upc.shareway.tripplanning.trip.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.upc.shareway.tripplanning.trip.application.TripMatchingService;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Trip Planning & Matching", description = "Trip requests, driver availability and match proposals")
public class TripMatchingController {

  private final TripMatchingService tripMatchingService;

  public TripMatchingController(TripMatchingService tripMatchingService) {
    this.tripMatchingService = tripMatchingService;
  }

  @PostMapping("/trip-requests")
  @Operation(
      summary = "Create trip request",
      description = "Registers a passenger trip request to search compatible driver availability."
  )
  ResponseEntity<TripRequestResponse> createTripRequest(
      @Valid @RequestBody CreateTripRequest request
  ) {
    TripRequestResponse response = TripRequestResponse.from(
        tripMatchingService.createTripRequest(request)
    );
    return ResponseEntity.created(URI.create("/api/v1/trip-requests/" + response.id()))
        .body(response);
  }

  @GetMapping("/trip-requests")
  @Operation(
      summary = "List trip requests",
      description = "Returns the trip requests registered in the service, ordered by creation date."
  )
  ResponseEntity<List<TripRequestResponse>> listTripRequests() {
    List<TripRequestResponse> responses = tripMatchingService.listTripRequests().stream()
        .map(TripRequestResponse::from)
        .toList();
    return ResponseEntity.ok(responses);
  }

  @PostMapping("/driver-availabilities")
  @Operation(
      summary = "Register driver availability",
      description = "Registers the operating zone, date, time window and seats available for a driver."
  )
  ResponseEntity<DriverAvailabilityResponse> createDriverAvailability(
      @Valid @RequestBody CreateDriverAvailabilityRequest request
  ) {
    DriverAvailabilityResponse response = DriverAvailabilityResponse.from(
        tripMatchingService.createDriverAvailability(request)
    );
    return ResponseEntity.created(URI.create("/api/v1/driver-availabilities/" + response.id()))
        .body(response);
  }

  @PostMapping("/trip-requests/{tripRequestId}/match-proposals")
  @Operation(
      summary = "Generate match proposals",
      description = "Creates idempotent match proposals for a trip request using compatible driver availability."
  )
  ResponseEntity<List<MatchProposalResponse>> generateMatchProposals(
      @PathVariable Long tripRequestId
  ) {
    List<MatchProposalResponse> responses = tripMatchingService.generateMatchProposals(tripRequestId)
        .stream()
        .map(MatchProposalResponse::from)
        .toList();
    return ResponseEntity.ok(responses);
  }

  @GetMapping("/trip-requests/{tripRequestId}/match-proposals")
  @Operation(
      summary = "List match proposals",
      description = "Returns generated proposals for a trip request."
  )
  ResponseEntity<List<MatchProposalResponse>> listMatchProposals(
      @PathVariable Long tripRequestId
  ) {
    List<MatchProposalResponse> responses = tripMatchingService.listMatchProposals(tripRequestId)
        .stream()
        .map(MatchProposalResponse::from)
        .toList();
    return ResponseEntity.ok(responses);
  }
}
