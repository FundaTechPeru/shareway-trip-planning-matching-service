package pe.edu.upc.shareway.tripplanning.shared.security;

import java.util.UUID;

/**
 * User identity forwarded by the API Gateway after it validates the JWT.
 *
 * @param id   value of the {@code X-User-Id} header (JWT subject)
 * @param role value of the {@code X-User-Role} header, or {@code null} when absent or unknown
 */
public record GatewayUser(UUID id, UserRole role) {

  public static final String USER_ID_HEADER = "X-User-Id";
  public static final String USER_ROLE_HEADER = "X-User-Role";

  public boolean isAdmin() {
    return role == UserRole.ADMIN;
  }

  public boolean canAccessResourceOwnedBy(UUID ownerId) {
    return isAdmin() || id.equals(ownerId);
  }
}
