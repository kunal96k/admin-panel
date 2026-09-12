package com.tts.sms.exception;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle client abort exceptions (browser closed connection)
     * Common with Google Analytics, page navigation, etc.
     */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException ex) {
        // Log at DEBUG level only - this is expected behavior
        log.debug("Client aborted connection: {}", ex.getMessage());
        // Don't return any response - connection is already closed
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        log.error("Validation error: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Validation Failed");
        response.put("message", "Invalid input data");
        response.put("path", extractPath(request));
        response.put("validationErrors", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public Object handleUnauthorized(UnauthorizedException ex, WebRequest request) {
        log.warn("[BLOCKED] Unauthorized access attempt: {}", ex.getMessage());
        return handleResponse(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                ex.getMessage(),
                request,
                ex
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public Object handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        log.error("Resource not found: {}", ex.getMessage());
        return handleResponse(
                HttpStatus.NOT_FOUND,
                "Resource Not Found",
                ex.getMessage(),
                request,
                ex
        );
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateResourceException(
            DuplicateResourceException ex, WebRequest request) {
        log.error("Duplicate resource error: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.CONFLICT.value());
        response.put("error", "Duplicate Resource");
        response.put("message", ex.getMessage());
        response.put("path", extractPath(request));

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        log.error("Illegal argument: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Bad Request");
        response.put("message", ex.getMessage());
        response.put("path", extractPath(request));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        log.error("Runtime exception occurred: {}", ex.getMessage(), ex);

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Runtime Error");
        response.put("message", ex.getMessage());
        response.put("path", extractPath(request));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(UnexpectedRollbackException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionRollback(
            UnexpectedRollbackException ex, WebRequest request) {
        log.error("Transaction rolled back unexpectedly", ex);

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", "Transaction Error");
        response.put("message", "Data validation failed. Please check your data format and required fields.");
        response.put("path", extractPath(request));

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSizeException(
            MaxUploadSizeExceededException ex, WebRequest request) {
        log.error("File size exceeded: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.put("error", "File Too Large");
        response.put("message", "File size exceeds maximum allowed limit.");
        response.put("path", extractPath(request));

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFound(
            NoResourceFoundException ex, WebRequest request) {
        String path = extractPath(request);

        // Ignore Chrome DevTools, Google Analytics, and other tracking scripts
        if (path.contains("com.chrome.devtools") ||
                path.contains("appspecific") ||
                path.contains("gtag") ||
                path.contains("googletagmanager") ||
                path.contains("google-analytics")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        log.debug("Resource not found: {}", ex.getResourcePath());

        return handleResponse(
                HttpStatus.NOT_FOUND,
                "Not Found",
                "The requested resource was not found",
                request,
                ex
        );
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMediaTypeNotAcceptable(
            org.springframework.web.HttpMediaTypeNotAcceptableException ex, WebRequest request) {
        String path = extractPath(request);

        // Ignore 406 errors from Google Analytics and tracking scripts
        if (path.contains("gtag") ||
                path.contains("googletagmanager") ||
                path.contains("google-analytics")) {
            log.debug("Ignoring 406 from analytics script: {}", path);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        log.error("Media type not acceptable: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.NOT_ACCEPTABLE.value());
        response.put("error", "Not Acceptable");
        response.put("message", "The requested media type is not supported");
        response.put("path", path);

        return ResponseEntity
                .status(HttpStatus.NOT_ACCEPTABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex, WebRequest request) {
        log.warn("[WARN] Method not supported: {} (Allowed methods: {})", ex.getMethod(), ex.getSupportedHttpMethods());

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.METHOD_NOT_ALLOWED.value());
        response.put("error", "Method Not Allowed");
        response.put("message", ex.getMessage());
        response.put("path", extractPath(request));

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(
            org.springframework.web.HttpMediaTypeNotSupportedException ex, WebRequest request) {
        log.warn("[WARN] Media type not supported: {}", ex.getContentType());

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        response.put("error", "Unsupported Media Type");
        response.put("message", ex.getMessage());
        response.put("path", extractPath(request));

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParams(
            org.springframework.web.bind.MissingServletRequestParameterException ex, WebRequest request) {
        log.warn("[WARN] Missing request parameter: {}", ex.getParameterName());

        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", "Bad Request");
        response.put("message", ex.getMessage());
        response.put("path", extractPath(request));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public Object handleGlobalException(
            Exception ex, WebRequest request) {

        // Check if it's a ClientAbortException wrapped in another exception
        if (isCausedByClientAbort(ex)) {
            log.debug("Client aborted connection (wrapped exception): {}", ex.getMessage());
            return null;
        }

        log.error("Unexpected error occurred", ex);

        return handleResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request,
                ex
        );
    }

    private Object handleResponse(HttpStatus status, String error, String message, WebRequest request, Exception ex) {
        String path = extractPath(request);
        boolean isHtml = false;
        String acceptHeader = request.getHeader("Accept");
        if (acceptHeader != null && acceptHeader.contains("text/html") && !path.startsWith("/api/")) {
            isHtml = true;
        }

        if (isHtml) {
            org.springframework.web.servlet.ModelAndView modelAndView = new org.springframework.web.servlet.ModelAndView();
            modelAndView.addObject("timestamp", LocalDateTime.now().toString());
            modelAndView.addObject("status", status.value());
            modelAndView.addObject("error", error);
            modelAndView.addObject("message", message);
            modelAndView.addObject("path", path);
            
            if (status == HttpStatus.NOT_FOUND) {
                modelAndView.setViewName("error/404");
            } else if (status == HttpStatus.FORBIDDEN) {
                modelAndView.setViewName("error/403");
            } else {
                modelAndView.setViewName("error/500");
            }
            return modelAndView;
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("timestamp", LocalDateTime.now().toString());
            response.put("status", status.value());
            response.put("error", error);
            response.put("message", message);
            response.put("path", path);
            return ResponseEntity
                    .status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
        }
    }

    /**
     * Check if exception is caused by client abort (browser closed connection)
     */
    private boolean isCausedByClientAbort(Throwable ex) {
        while (ex != null) {
            if (ex instanceof ClientAbortException
                    || ex.getClass().getName().contains("ClientAbortException")
                    || (ex.getMessage() != null && ex.getMessage().contains("CLOSED_RST_RX"))
                    || (ex.getMessage() != null && ex.getMessage().contains("Broken pipe"))) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }

    private String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}