package dev.orderflow.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Все ошибки API — в одном формате (RFC 7807 ProblemDetail плюс свой код),
 * чтобы клиенту не приходилось разбирать тексты.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException e) {
		return problem(HttpStatus.NOT_FOUND, e.code(), e.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException e) {
		return problem(HttpStatus.CONFLICT, e.code(), e.getMessage());
	}

	@ExceptionHandler(InsufficientStockException.class)
	ProblemDetail handleInsufficientStock(InsufficientStockException e) {
		ProblemDetail detail = problem(HttpStatus.CONFLICT, "insufficient_stock", e.getMessage());
		detail.setProperty("sku", e.sku());
		detail.setProperty("available", e.available());
		detail.setProperty("requested", e.requested());
		return detail;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidation(MethodArgumentNotValidException e) {
		List<String> errors = e.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.toList();
		ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "validation_failed",
				"тело запроса не прошло валидацию");
		detail.setProperty("errors", errors);
		return detail;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
		return problem(HttpStatus.BAD_REQUEST, "malformed_json", "тело запроса не разобралось как JSON");
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
		return problem(HttpStatus.BAD_REQUEST, "bad_request", e.getMessage());
	}

	static ProblemDetail problem(HttpStatus status, String code, String message) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
		detail.setProperty("code", code);
		return detail;
	}

}
