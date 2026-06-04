package com.remote.core.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException exception,
                                                                    HttpServletRequest request) {
        log.warn(
                "Business error: code={}, message={}, path={}",
                exception.getCode(),
                exception.getMessage(),
                request.getRequestURI()
        );

        return buildResponse(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception,
                                                                  HttpServletRequest request) {
        log.warn(
                "Bad request: message={}, path={}",
                exception.getMessage(),
                request.getRequestURI()
        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException exception,
                                                               HttpServletRequest request) {
        log.warn(
                "Conflict: message={}, path={}",
                exception.getMessage(),
                request.getRequestURI()
        );

        return buildResponse(
                HttpStatus.CONFLICT,
                "CONFLICT",
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception,
                                                                 HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());

        log.warn(
                "Response status error: status={}, message={}, path={}",
                status.value(),
                exception.getReason(),
                request.getRequestURI()
        );

        return buildResponse(
                status,
                status.name(),
                exception.getReason(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception,
                                                             HttpServletRequest request) {
        log.error(
                "Unexpected server error: path={}",
                request.getRequestURI(),
                exception
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "Внутренняя ошибка сервера",
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatus status,
                                                           String code,
                                                           String message,
                                                           HttpServletRequest request) {
        ApiErrorResponse response = new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI()
        );

        return ResponseEntity.status(status).body(response);
    }
}