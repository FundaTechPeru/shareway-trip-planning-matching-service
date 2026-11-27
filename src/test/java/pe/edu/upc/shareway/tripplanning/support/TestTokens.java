package pe.edu.upc.shareway.tripplanning.support;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Builds HS256 access tokens shaped like the ones Driver Management issues, signed with the
 * secret configured in {@code src/test/resources/application.properties}.
 */
public final class TestTokens {

  public static final String SECRET = "dev-only-shareway-driver-management-secret-change-me";

  private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
  private static final SecretKey OTHER_KEY = Keys.hmacShaKeyFor(
      "another-secret-that-is-long-enough-for-hs256".getBytes(StandardCharsets.UTF_8));

  private TestTokens() {
  }

  /** {@code Authorization} header value for a valid token. */
  public static String bearer(String userId, String role) {
    return "Bearer " + token(userId, role, KEY, Duration.ofMinutes(60));
  }

  public static String bearer(UUID userId, String role) {
    return bearer(userId.toString(), role);
  }

  public static String expired(String userId, String role) {
    return "Bearer " + token(userId, role, KEY, Duration.ofMinutes(-5));
  }

  public static String signedWithAnotherSecret(String userId, String role) {
    return "Bearer " + token(userId, role, OTHER_KEY, Duration.ofMinutes(60));
  }

  public static String withoutRole(String userId) {
    return "Bearer " + token(userId, null, KEY, Duration.ofMinutes(60));
  }

  private static String token(String userId, String role, SecretKey key, Duration validFor) {
    Instant now = Instant.now();
    JwtBuilder builder = Jwts.builder()
        .subject(userId)
        .claim("userId", userId)
        .claim("email", "user@shareway.test")
        .issuedAt(Date.from(now.minus(Duration.ofMinutes(10))))
        .expiration(Date.from(now.plus(validFor)))
        .signWith(key);
    if (role != null) {
      builder.claim("role", role);
    }
    return builder.compact();
  }
}
