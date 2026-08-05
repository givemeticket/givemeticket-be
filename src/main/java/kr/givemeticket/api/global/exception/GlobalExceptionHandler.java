package kr.givemeticket.api.global.exception;

import java.util.stream.Collectors;
import kr.givemeticket.api.global.log.dto.ErrorLog;
import kr.givemeticket.api.payment.domain.PaymentException;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.marker.Markers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 결제 게이트웨이 실패는 우리 잘못이 아니라 외부 의존성 문제라 따로 분류한다.
     */
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePaymentException(PaymentException e) {
        int status = e.getStatus().value();
        logError(ErrorLog.externalError(status, e, e.getCode()), e);
        return ResponseEntity.status(e.getStatus())
                .body(ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        HttpStatus status = e.getStatus();

        if (status.is5xxServerError()) {
            // STOCK_NOT_INITIALIZED 처럼 5xx 로 선언된 비즈니스 예외는 실제로 서버 결함이다.
            logError(ErrorLog.serverError(status.value(), e, e.getCode()), e);
        } else if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            logWarn(ErrorLog.clientError(status.value(), e, e.getCode()));
        } else {
            logInfo(ErrorLog.clientError(status.value(), e, e.getCode()));
        }

        return ResponseEntity.status(status)
                .body(ErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        logInfo(ErrorLog.clientError(HttpStatus.BAD_REQUEST.value(), e, "INVALID_REQUEST"));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_REQUEST", message));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        logInfo(ErrorLog.clientError(HttpStatus.NOT_FOUND.value(), e, "NOT_FOUND"));

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "요청한 경로를 찾을 수 없습니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        logInfo(ErrorLog.clientError(HttpStatus.METHOD_NOT_ALLOWED.value(), e, "METHOD_NOT_ALLOWED"));

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorResponse.of("METHOD_NOT_ALLOWED", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        logError(ErrorLog.serverError(HttpStatus.INTERNAL_SERVER_ERROR.value(), e, "INTERNAL_ERROR"), e);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "예상치 못한 오류가 발생했습니다."));
    }

    private void logInfo(ErrorLog errorLog) {
        log.info(Markers.appendEntries(errorLog.fields()), errorLog.summary());
    }

    private void logWarn(ErrorLog errorLog) {
        log.warn(Markers.appendEntries(errorLog.fields()), errorLog.summary());
    }

    /**
     * 서버/외부 오류는 요약 필드에 더해 예외 자체를 넘겨 전체 스택트레이스를 남긴다.
     */
    private void logError(ErrorLog errorLog, Throwable e) {
        log.error(Markers.appendEntries(errorLog.fields()), errorLog.summary(), e);
    }
}
