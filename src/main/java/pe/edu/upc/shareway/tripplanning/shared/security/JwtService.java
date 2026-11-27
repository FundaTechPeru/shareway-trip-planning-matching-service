package pe.edu.upc.shareway.tripplanning.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Validates the HS256 access tokens issued by Driver Management with the shared JWT_SECRET. The user
 * is the {@code sub} claim (a UUID) and the role is the {@code role} claim.
 */
@Service
public class JwtService {

  static final String ROLE_CLAIM = "role";

  private final SecretKey signingKey;

  public JwtService(JwtProperties properties) {
    String secret = properties.secret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "app.jwt.secret is not set. Provide the JWT_SECRET environment variable "
              + "(or run with the dev or local profile for the development default).");
    }
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Returns the user of a valid token, or empty when the token is malformed, expired, not signed
   * with HS256 and JWT_SECRET, or its subject is not a UUID. An unknown or missing role yields a
   * user without role, which is then rejected by endpoints that require one.
   */
  public Optional<AuthenticatedUser> authenticate(String token) {
    try {
      Claims claims = Jwts.parser()
          .verifyWith(signingKey)
          .build()
          .parseSignedClaims(token)
          .getPayload();
      String subject = claims.getSubject();
      if (subject == null || subject.length() != 36) {
        return Optional.empty();
      }
      UUID userId = UUID.fromString(subject);
      Object role = claims.get(ROLE_CLAIM);
      UserRole userRole = role instanceof String value ? UserRole.parse(value).orElse(null) : null;
      return Optional.of(new AuthenticatedUser(userId, userRole));
    } catch (JwtException | IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
