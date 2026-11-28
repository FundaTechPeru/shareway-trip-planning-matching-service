package pe.edu.upc.shareway.tripplanning.trip.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.upc.shareway.tripplanning.shared.api.ApiError;
import pe.edu.upc.shareway.tripplanning.shared.security.AuthenticatedUser;
import pe.edu.upc.shareway.tripplanning.shared.security.RequiresRole;
import pe.edu.upc.shareway.tripplanning.shared.security.UserRole;
import pe.edu.upc.shareway.tripplanning.trip.application.TripMatchingService;

/**
 * Every endpoint requires the bearer token forwarded by the API Gateway. The security requirement
 * and the 401 response are added to the OpenAPI document by
 * {@link pe.edu.upc.shareway.tripplanning.shared.config.OpenApiConfig}.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Trip Planning & Matching", description = "Trip requests and match proposals")
public class TripMatchingController {

  private final TripMatchingService tripMatchingService;

  public TripMatchingController(TripMatchingService tripMatchingService) {
    this.tripMatchingService = tripMatchingService;
  }

  @PostMapping("/trip-requests")
  @RequiresRole(UserRole.PASSENGER)
  @Operation(
      summary = "Create trip request",
      description = "Registers a passenger trip request to search compatible driver availability. "
          + "Requires the PASSENGER role; the passenger is the token subject. "
          + "A passengerId in the body is ignored."
  )
  @ApiResponse(responseCode = "201", description = "Trip request created")
  @ApiResponse(responseCode = "400", description = "Invalid request body",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(responseCode = "403", description = "The token role is not PASSENGER",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ResponseEntity<TripRequestResponse> createTripRequest(
      @Parameter(hidden = true) AuthenticatedUser user,
      @Valid @RequestBody CreateTripRequest request
  ) {
    TripRequestResponse response = TripRequestResponse.from(
        tripMatchingService.createTripRequest(user.id(), request)
    );
    return ResponseEntity.created(URI.create("/api/v1/trip-requests/" + response.id()))
        .body(response);
  }

  @GetMapping("/trip-requests")
  @Operation(
      summary = "List trip requests",
      description = "Returns the trip requests of the token subject, ordered by creation date. "
          + "ADMIN users get every trip request."
  )
  @ApiResponse(responseCode = "200", description = "Trip requests visible to the user")
  ResponseEntity<List<TripRequestResponse>> listTripRequests(
      @Parameter(hidden = true) AuthenticatedUser user
  ) {
    List<TripRequestResponse> responses = tripMatchingService.listTripRequests(user).stream()
        .map(TripRequestResponse::from)
        .toList();
    return ResponseEntity.ok(responses);
  }

  @PostMapping("/trip-requests/{tripRequestId}/match-proposals")
  @Operation(
      summary = "Generate match proposals",
      description = "Creates idempotent match proposals for a trip request using OPEN driver "
          + "availabilities (trip groups). Each proposal carries the groupId it came from. "
          + "Only the passenger who owns the trip request or an ADMIN can call it."
  )
  @ApiResponse(responseCode = "200", description = "Match proposals for the trip request")
  @ApiResponse(responseCode = "404", description = "Trip request not found or owned by another user",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ResponseEntity<List<MatchProposalResponse>> generateMatchProposals(
      @Parameter(hidden = true) AuthenticatedUser user,
      @PathVariable UUID tripRequestId
  ) {
    List<MatchProposalResponse> responses =
        tripMatchingService.generateMatchProposals(tripRequestId, user)
            .stream()
            .map(MatchProposalResponse::from)
            .toList();
    return ResponseEntity.ok(responses);
  }

  @GetMapping("/trip-requests/{tripRequestId}/match-proposals")
  @Operation(
      summary = "List match proposals",
      description = "Returns generated proposals for a trip request. "
          + "Only the passenger who owns the trip request or an ADMIN can call it."
  )
  @ApiResponse(responseCode = "200", description = "Match proposals for the trip request")
  @ApiResponse(responseCode = "404", description = "Trip request not found or owned by another user",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ResponseEntity<List<MatchProposalResponse>> listMatchProposals(
      @Parameter(hidden = true) AuthenticatedUser user,
      @PathVariable UUID tripRequestId
  ) {
    List<MatchProposalResponse> responses =
        tripMatchingService.listMatchProposals(tripRequestId, user)
            .stream()
            .map(MatchProposalResponse::from)
            .toList();
    return ResponseEntity.ok(responses);
  }
}
