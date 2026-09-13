package com.musinsa.point.common;

import jakarta.persistence.LockTimeoutException;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
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
     * 사용 서비스가 먼저 걸러 내므로 여기까지 오는 일은 드물다.
     * 남은 가능성은 같은 주문이 동시에 들어와 (계정, 주문번호) 유일 제약에 걸린 경우다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handle(DataIntegrityViolationException exception) {
        return respond(ErrorCode.DUPLICATE_ORDER, ErrorCode.DUPLICATE_ORDER.getDefaultMessage());
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

    /** 마지막 안전망. 내부 사정은 응답에 담지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handle(Exception exception) {
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage());
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
