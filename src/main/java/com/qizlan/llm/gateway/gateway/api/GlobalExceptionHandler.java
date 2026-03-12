package com.qizlan.llm.gateway.gateway.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import com.qizlan.llm.gateway.gateway.security.ApiKeyAccessDeniedException;
import com.qizlan.llm.gateway.gateway.security.GuardrailViolationException;
import com.qizlan.llm.gateway.gateway.provider.UpstreamProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(true, 400, "Invalid request parameters"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(true, 400, ex.getMessage()));
    }

    @ExceptionHandler(UpstreamProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleUpstream(UpstreamProviderException ex) {
        return ResponseEntity.status(ex.getGatewayStatus())
                .body(new ApiErrorResponse(true, ex.getGatewayStatus(), ex.getMessage()));
    }

    @ExceptionHandler(ApiKeyAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(ApiKeyAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(true, 403, ex.getMessage()));
    }

    @ExceptionHandler(GuardrailViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleGuardrail(GuardrailViolationException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiErrorResponse(true, 403, ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiErrorResponse(true, ex.getStatusCode().value(), ex.getReason() == null ? ex.getMessage() : ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled gateway exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(true, 500, "Internal Server Error"));
    }
}
