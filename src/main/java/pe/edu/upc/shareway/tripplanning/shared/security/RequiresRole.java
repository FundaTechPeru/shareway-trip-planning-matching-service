package pe.edu.upc.shareway.tripplanning.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a handler method to the given token roles. Enforced when the method declares a
 * {@link AuthenticatedUser} parameter.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {

  UserRole[] value();
}
