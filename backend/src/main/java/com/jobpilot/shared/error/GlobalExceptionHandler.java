package com.jobpilot.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Único manejador global de errores. Todas las respuestas de error son {@link ProblemDetail}
 * ({@code application/problem+json}), con {@code instance} = ruta de la petición y sin trazas ni
 * mensajes internos. Las excepciones estándar de Spring MVC (405, 415, 404 de ruta…) las resuelve
 * {@link ResponseEntityExceptionHandler}. Nunca se registra el cuerpo de la petición: puede contener
 * datos personales (CVs).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---- Excepciones de dominio ----

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ProblemTypes.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ProblemTypes.of(ex.getTypeSlug()), ex.getTitle(), ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<Object> handleInvalidRequest(InvalidRequestException ex, NativeWebRequest request) {
        return validationProblem(List.of(new FieldError(ex.getField(), ex.getMessage())), new HttpHeaders(),
                request);
    }

    @ExceptionHandler(InvalidCvFileException.class)
    ProblemDetail handleInvalidCvFile(InvalidCvFileException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ProblemTypes.INVALID_CV_FILE, "Fichero no válido",
                ex.getMessage(), request);
    }

    @ExceptionHandler(CvWithoutTextException.class)
    ProblemDetail handleCvWithoutText(CvWithoutTextException ex, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ProblemTypes.CV_WITHOUT_TEXT, "El PDF no tiene texto",
                ex.getMessage(), request);
    }

    /**
     * Otra petición ha modificado la fila entre la lectura y la escritura (bloqueo optimista con {@code @Version}).
     * El servicio ya compara la {@code version} que envía el cliente; esto cubre la carrera entre dos peticiones.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Conflicto de versión en {} {}", request.getMethod(), request.getRequestURI());
        return handleConflict(ConflictException.versionConflict(), request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest request) {
        log.info("Límite de peticiones superado en {} {}", request.getMethod(), request.getRequestURI());
        ProblemDetail body = problem(HttpStatus.TOO_MANY_REQUESTS, ProblemTypes.RATE_LIMIT_EXCEEDED,
                "Demasiadas peticiones", ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
                .body(body);
    }

    // ---- Autenticación (401, siempre con WWW-Authenticate: Bearer) ----

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex,
            HttpServletRequest request) {
        return unauthorized(problem(HttpStatus.UNAUTHORIZED, ProblemTypes.INVALID_CREDENTIALS,
                "Credenciales incorrectas", ex.getMessage(), request));
    }

    /** Lo reenvía aquí {@code ProblemDetailAuthenticationEntryPoint} desde los filtros de Spring Security. */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        log.debug("Petición no autenticada en {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage());
        return unauthorized(problem(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED, "No autenticado",
                "Falta el token de acceso, no es válido o ha caducado.", request));
    }

    // ---- Errores del LLM: el mensaje de la excepción puede traer detalles del proveedor, solo va al log ----

    @ExceptionHandler(LlmTimeoutException.class)
    ProblemDetail handleLlmTimeout(LlmTimeoutException ex, HttpServletRequest request) {
        log.warn("Timeout del LLM en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return problem(HttpStatus.GATEWAY_TIMEOUT, ProblemTypes.LLM_TIMEOUT, "La IA no ha respondido a tiempo",
                "La IA ha tardado demasiado en responder. Inténtalo de nuevo en unos minutos.", request);
    }

    @ExceptionHandler(LlmInvalidResponseException.class)
    ProblemDetail handleLlmInvalidResponse(LlmInvalidResponseException ex, HttpServletRequest request) {
        log.warn("Respuesta no válida del LLM en {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, ProblemTypes.LLM_INVALID_RESPONSE, "Respuesta de la IA no válida",
                "La IA ha devuelto una respuesta que no se ha podido procesar. Inténtalo de nuevo.", request);
    }

    @ExceptionHandler(LlmOverloadedException.class)
    ResponseEntity<ProblemDetail> handleLlmOverloaded(LlmOverloadedException ex, HttpServletRequest request) {
        log.warn("LLM saturado en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        ProblemDetail body = problem(HttpStatus.SERVICE_UNAVAILABLE, ProblemTypes.LLM_OVERLOADED,
                "La IA está saturada",
                "Hay demasiadas peticiones a la IA en este momento. Inténtalo de nuevo en "
                        + ex.getRetryAfterSeconds() + " segundos.",
                request);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.getRetryAfterSeconds()))
                .body(body);
    }

    // ---- Cualquier otra excepción: 500 genérico, la traza completa solo en el log ----

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ProblemTypes.INTERNAL_ERROR, "Error interno",
                "Se ha producido un error inesperado. Inténtalo de nuevo más tarde.", request);
    }

    // ---- Personalización de las excepciones estándar de Spring MVC ----

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return validationProblem(errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> {
                    if (result instanceof ParameterErrors parameterErrors) {
                        return parameterErrors.getFieldErrors().stream()
                                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()));
                    }
                    String name = result.getMethodParameter().getParameterName();
                    return result.getResolvableErrors().stream()
                            .map(MessageSourceResolvable::getDefaultMessage)
                            .map(message -> new FieldError(name, message));
                })
                .toList();
        return validationProblem(errors, headers, request);
    }

    /** El CV supera {@code spring.servlet.multipart.max-file-size} (5 MB): 400 en {@code file}, como en la HU-01. */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationProblem(List.of(new FieldError("file", InvalidCvFileException.DETAIL)), new HttpHeaders(),
                request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no es un JSON válido o no tiene el formato esperado.");
        body.setType(ProblemTypes.MALFORMED_REQUEST);
        body.setTitle("Petición mal formada");
        return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Punto común de todas las respuestas de {@link ResponseEntityExceptionHandler}: fija {@code instance}. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        if (body instanceof ProblemDetail problemDetail && problemDetail.getInstance() == null) {
            problemDetail.setInstance(requestPath(request));
        }
        return super.createResponseEntity(body, headers, status, request);
    }

    // ---- Utilidades ----

    private ResponseEntity<Object> validationProblem(List<FieldError> errors, HttpHeaders headers, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La petición contiene campos no válidos.");
        body.setType(ProblemTypes.VALIDATION_ERROR);
        body.setTitle("Datos de entrada no válidos");
        body.setProperty("errors", errors.stream()
                .sorted(Comparator.comparing(FieldError::field).thenComparing(FieldError::message))
                .toList());
        return createResponseEntity(body, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static ResponseEntity<ProblemDetail> unauthorized(ProblemDetail body) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").body(body);
    }

    private static ProblemDetail problem(HttpStatus status, URI type, String title, String detail,
            HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(type);
        body.setTitle(title);
        body.setInstance(URI.create(request.getRequestURI()));
        return body;
    }

    private static URI requestPath(WebRequest request) {
        if (request instanceof NativeWebRequest nativeRequest) {
            HttpServletRequest servletRequest = nativeRequest.getNativeRequest(HttpServletRequest.class);
            if (servletRequest != null) {
                return URI.create(servletRequest.getRequestURI());
            }
        }
        return null;
    }
}
