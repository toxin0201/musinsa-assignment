package com.musinsa.point.common;

import jakarta.persistence.LockTimeoutException;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 예외를 응답으로 바꾸는 유일한 지점.
 * 스프링은 선언 순서가 아니라 예외 타입이 얼마나 가까운지로 처리기를 고르므로,
 * 프레임워크 예외는 저마다 전용 처리기를 둬야 마지막 안전망(Exception)으로 흘러가지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 같은 주문을 두 번 쓰는 일을 막는 유일 제약. 이름은 {@code schema.sql} 이 정한다. */
    private static final String USE_ORDER_CONSTRAINT = "uk_point_transaction_use_order";

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    /** 도메인이 스스로 정한 거절. 코드와 상태를 예외가 들고 온다. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handle(ApiException exception) {
        return respond(exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException exception) {
        return respond(ErrorCode.INVALID_REQUEST, firstFieldMessage(exception));
    }

    /** 경로 변수에 붙인 제약(@Size 등)이 깨졌을 때. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handle(ConstraintViolationException exception) {
        return respond(ErrorCode.INVALID_REQUEST, exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElseGet(ErrorCode.INVALID_REQUEST::getDefaultMessage));
    }

    /** 깨진 JSON, long 에 담기지 않는 숫자, 타입이 맞지 않는 필드가 모두 여기로 온다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handle(HttpMessageNotReadableException exception) {
        return respond(ErrorCode.INVALID_REQUEST, "요청 본문을 읽을 수 없습니다.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentTypeMismatchException exception) {
        return respond(ErrorCode.INVALID_REQUEST, "'%s' 값의 형식이 올바르지 않습니다.".formatted(exception.getName()));
    }

    /**
     * 같은 회원의 다른 요청이 계정 행을 쥐고 있어 대기 시간을 넘긴 경우.
     * 리포지토리를 거치면 PessimisticLockingFailureException, EntityManager 를 직접 쓰면 LockTimeoutException 이 올라온다.
     */
    @ExceptionHandler({PessimisticLockingFailureException.class, LockTimeoutException.class})
    public ResponseEntity<ErrorResponse> handleLockTimeout(Exception exception) {
        return respond(ErrorCode.UPDATE_CONFLICT, ErrorCode.UPDATE_CONFLICT.getDefaultMessage());
    }

    /**
     * 무결성 위반 중 주문 중복이라고 말할 수 있는 것은 (계정, 주문번호) 유일 제약뿐이다.
     * 나머지는 우리가 예상하지 못한 상태이므로 원인을 로그에 남기고 500 으로 답한다 —
     * 전부 409 로 뭉개면 고칠 곳이 있는 오류가 "정상적인 중복 요청" 처럼 보여 묻힌다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handle(DataIntegrityViolationException exception) {
        if (violatesUseOrderConstraint(exception)) {
            return respond(ErrorCode.DUPLICATE_ORDER, ErrorCode.DUPLICATE_ORDER.getDefaultMessage());
        }
        log.error("무결성 제약을 어겨 요청을 처리하지 못했습니다.", exception);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception exception) {
        return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getDefaultMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handle(HttpRequestMethodNotSupportedException exception) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handle(HttpMediaTypeNotSupportedException exception) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getDefaultMessage());
    }

    /** 마지막 안전망. 내부 사정은 응답에 담지 않되, 원인은 사슬째로 로그에 남긴다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handle(Exception exception) {
        log.error("요청을 처리하지 못했습니다.", exception);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    /**
     * 제약 이름은 드라이버마다 대소문자도, 사슬 안의 깊이도 다르게 실려 온다.
     * 그래서 맨 위 메시지만 보지 않고 원인을 끝까지 따라가며 대소문자 없이 찾는다.
     */
    private static boolean violatesUseOrderConstraint(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(USE_ORDER_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, message, clock.instant()));
    }

    private static String firstFieldMessage(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElseGet(ErrorCode.INVALID_REQUEST::getDefaultMessage);
    }
}
