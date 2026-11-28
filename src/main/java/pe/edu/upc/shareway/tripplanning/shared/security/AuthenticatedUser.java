package pe.edu.upc.shareway.tripplanning.shared.security;

import java.util.UUID;

/**
 * User described by a valid access token.
 *
 * @param id   the {@code sub} claim (user UUID)
 * @param role the {@code role} claim, or {@code null} when it is absent or unknown
 */
public record AuthenticatedUser(UUID id, UserRole role) {

  public boolean isAdmin() {
    return role == UserRole.ADMIN;
  }

  public boolean canAccessResourceOwnedBy(UUID ownerId) {
    return isAdmin() || id.equals(ownerId);
  }
}
