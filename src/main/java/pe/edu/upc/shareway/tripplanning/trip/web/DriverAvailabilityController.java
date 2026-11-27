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
import pe.edu.upc.shareway.tripplanning.trip.application.DriverAvailabilityService;

/**
 * Driver availabilities, called trip groups by Booking &amp; Pricing (the availability id is the
 * groupId). Every endpoint requires the bearer token forwarded by the API Gateway.
 */
@RestController
@RequestMapping("/api/v1/driver-availabilities")
@Tag(name = "Driver Availability", description = "Driver availabilities (trip groups) and US-21 accept or reject")
public class DriverAvailabilityController {

  private final DriverAvailabilityService driverAvailabilityService;

  public DriverAvailabilityController(DriverAvailabilityService driverAvailabilityService) {
    this.driverAvailabilityService = driverAvailabilityService;
  }

  @PostMapping
  @RequiresRole(UserRole.DRIVER)
  @Operation(
      summary = "Register driver availability",
      description = "Registers the operating zone, date, time window and seats available for a driver. "
          + "Requires the DRIVER role; the driver is the token subject. "
          + "A driverId in the body is ignored. Queues the TripGroupOpened event for Booking."
  )
  @ApiResponse(responseCode = "201", description = "Driver availability registered with status OPEN")
  @ApiResponse(responseCode = "400", description = "Invalid request body",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(responseCode = "403", description = "The token role is not DRIVER",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ResponseEntity<DriverAvailabilityResponse> createDriverAvailability(
      @Parameter(hidden = true) AuthenticatedUser user,
      @Valid @RequestBody CreateDriverAvailabilityRequest request
  ) {
    DriverAvailabilityResponse response = DriverAvailabilityResponse.from(
        driverAvailabilityService.createDriverAvailability(user.id(), request)
    );
    return ResponseEntity.created(URI.create("/api/v1/driver-availabilities/" + response.id()))
        .body(response);
  }

  @GetMapping
  @RequiresRole({UserRole.DRIVER, UserRole.ADMIN})
  @Operation(
      summary = "List driver availabilities",
      description = "A DRIVER gets their own availabilities; an ADMIN gets all of them. "
          + "Ordered by date and start time."
  )
  @ApiResponse(responseCode = "200", description = "Driver availabilities visible to the user")
  @ApiResponse(responseCode = "403", description = "The token role is not DRIVER or ADMIN",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ResponseEntity<List<DriverAvailabilityResponse>> listDriverAvailabilities(
      @Parameter(hidden = true) AuthenticatedUser user
  ) {
    return ResponseEntity.ok(driverAvailabilityService.listDriverAvailabilities(user).stream()
        .map(DriverAvailabilityResponse::from)
        .toList());
  }

  @PostMapping("/{driverAvailabilityId}/accept")
  @RequiresRole(UserRole.DRIVER)
  @Operation(
      summary = "Accept trip group (US-21)",
      description = "The owning DRIVER accepts an OPEN availability: it becomes ACCEPTED, its PROPOSED "
          + "proposals become ACCEPTED, and GroupCompleted and ProposalAccepted are queued for Booking, "
          + "in that order."
  )
  @ApiResponse(responseCode = "200", description = "Availability accepted")
  @ApiResponse(responseCode = "403", description = "The token role is not DRIVER",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(responseCode = "404", description = "Availability not found or owned by another driver",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(responseCode = "409", description = "The availability is no longer OPEN",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ResponseEntity<DriverAvailabilityResponse> accept(
      @Parameter(hidden = true) AuthenticatedUser user,
      @PathVariable UUID driverAvailabilityId
  ) {
    return ResponseEntity.ok(DriverAvailabilityResponse.from(
        driverAvailabilityService.accept(driverAvailabilityId, user)));
  }

  @PostMapping("/{driverAvailabilityId}/reject")
  @RequiresRole(UserRole.DRIVER)
  @Operation(
      summary = "Reject trip group (US-21)",
      description = "The owning DRIVER rejects an OPEN availability: it becomes REJECTED, its PROPOSED "
          + "proposals become REJECTED, and GroupRejected is queued for Booking."
  )
  @ApiResponse(responseCode = "200", description = "Availability rejected")
  @ApiResponse(responseCode = "403", description = "The token role is not DRIVER",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(responseCode = "404", description = "Availability not found or owned by another driver",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(responseCode = "409", description = "The availability is no longer OPEN",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  ResponseEntity<DriverAvailabilityResponse> reject(
      @Parameter(hidden = true) AuthenticatedUser user,
      @PathVariable UUID driverAvailabilityId
  ) {
    return ResponseEntity.ok(DriverAvailabilityResponse.from(
        driverAvailabilityService.reject(driverAvailabilityId, user)));
  }
}
