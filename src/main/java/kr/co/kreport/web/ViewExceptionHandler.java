package kr.co.kreport.web;

import jakarta.servlet.http.HttpServletResponse;
import kr.co.kreport.support.ErrorCode;
import kr.co.kreport.support.KReportException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

/**
 * 화면 요청에서 난 오류를 오류 페이지로 보낸다.
 *
 * <p>{@code sendError} 로 서블릿 오류 처리에 넘기면 상태 코드에 맞는 문구가 담긴
 * {@code error.html} 이 그려진다. 여기서 뷰 이름을 직접 돌려주지 않는 이유는, 그렇게 하면
 * 응답 상태가 200 으로 나가 브라우저와 중간 장비가 정상 응답으로 여기기 때문이다.
 * 열람이 거부된 요청이 200 으로 기록되면 감사에서도 구분되지 않는다.</p>
 */
@Slf4j
@ControllerAdvice(annotations = Controller.class)
public class ViewExceptionHandler {

    @ExceptionHandler(KReportException.class)
    public void handle(KReportException e, HttpServletResponse response) throws IOException {
        ErrorCode code = e.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("[{}] {}", code.getCode(), e.getMessage(), e);
        } else {
            log.warn("[{}] {}", code.getCode(), e.getMessage());
        }
        response.sendError(code.getStatus().value());
    }
}
