package kr.co.kreport.web;

import kr.co.kreport.support.ErrorCode;
import kr.co.kreport.support.KReportException;
import kr.co.kreport.web.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API 오류 응답을 한 형태로 맞춘다.
 *
 * <p>가름의 기준은 예외의 타입 하나다. {@link KReportException} 은 이 시스템이 스스로
 * 알아차린 오류라 그 문구가 화면에 그대로 나가도 되고, 그 밖의 예외는 내부 사정이 섞여 있을
 * 수 있으므로 일반 문구로 덮고 상세는 로그에만 남긴다. 예외를 하나 더 만들 때 처리기를
 * 함께 늘리지 않아도 되는 이유이기도 하다.</p>
 *
 * <p>대상을 {@link RestController} 로 좁혀 둔 것은 화면 요청까지 JSON 오류를 받지 않게
 * 하기 위해서다. 브라우저 주소창으로 들어온 사람에게 JSON 을 내려 주면 무슨 일이
 * 일어났는지 알 길이 없다. 화면 쪽은 {@code ViewExceptionHandler} 가 오류 페이지로 보낸다.</p>
 */
@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    /** 이 시스템이 스스로 알아차린 오류. 문구와 항목별 안내를 그대로 내보낸다. */
    @ExceptionHandler(KReportException.class)
    public ResponseEntity<ErrorResponse> handleKnown(KReportException e) {
        ErrorCode code = e.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("[{}] {}", code.getCode(), e.getMessage(), e);
        } else {
            log.warn("[{}] {}", code.getCode(), e.getMessage());
        }
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, e.getMessage(), e.getDetails()));
    }

    /** 두 사람이 같은 정의를 동시에 저장한 경우 */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConflict(OptimisticLockingFailureException e) {
        return respond(ErrorCode.TEMPLATE_CONFLICT);
    }

    /** 본문이 JSON 으로 읽히지 않는 경우. 파서 메시지에는 내부 구조가 드러나므로 싣지 않는다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("요청 본문을 읽지 못했습니다: {}", e.getMessage());
        return respond(ErrorCode.TEMPLATE_UNREADABLE);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.PARAMETER_INVALID.getStatus())
                .body(ErrorResponse.of(ErrorCode.PARAMETER_INVALID, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOthers(Exception e) {
        log.error("예기치 못한 오류", e);
        return respond(ErrorCode.INTERNAL);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }
}
