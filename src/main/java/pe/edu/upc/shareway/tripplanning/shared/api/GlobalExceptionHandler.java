package pe.edu.upc.shareway.tripplanning.shared.api;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException exception,
      HttpServletRequest request
  ) {
    String message = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .collect(Collectors.joining("; "));

    return ResponseEntity.badRequest()
        .body(ApiError.of(400, "Validation failed", message, request.getRequestURI()));
  }

  @ExceptionHandler(EntityNotFoundException.class)
  ResponseEntity<ApiError> handleNotFound(
      EntityNotFoundException exception,
      HttpServletRequest request
  ) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiError.of(404, "Not found", exception.getMessage(), request.getRequestURI()));
  }
}
