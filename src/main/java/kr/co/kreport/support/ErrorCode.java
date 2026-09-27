package kr.co.kreport.support;

import org.springframework.http.HttpStatus;

/**
 * 오류 코드.
 *
 * <p>기관 시스템과 연계하면 "무슨 오류인지"를 문구가 아니라 코드로 주고받게 된다. 문구는
 * 다듬느라 바뀌지만 코드는 계약이라 바뀌지 않는다. 화면에 띄울 기본 문구와 HTTP 상태를
 * 코드마다 한곳에 묶어 두어, 새 오류를 만들 때 셋이 따로 놀지 않게 한다.</p>
 *
 * <p>접두어로 어느 층에서 난 오류인지 구분한다. {@code RPT} 리포트 정의,
 * {@code PRM} 조회 조건, {@code DAT} 데이터 조회, {@code EXP} 표현식, {@code OUT} 출력.</p>
 */
public enum ErrorCode {

    // --- 리포트 정의
    REPORT_NOT_FOUND("RPT-404", HttpStatus.NOT_FOUND, "리포트를 찾을 수 없습니다."),
    TEMPLATE_INVALID("RPT-400", HttpStatus.BAD_REQUEST, "리포트 정의에 오류가 있습니다."),
    TEMPLATE_CONFLICT("RPT-409", HttpStatus.CONFLICT,
            "다른 사용자가 먼저 저장했습니다. 화면을 새로 고친 뒤 다시 저장하세요."),
    TEMPLATE_UNREADABLE("RPT-422", HttpStatus.BAD_REQUEST, "리포트 정의를 해석할 수 없습니다."),

    // --- 조회 조건
    PARAMETER_INVALID("PRM-400", HttpStatus.BAD_REQUEST, "조회 조건을 확인하세요."),

    // --- 데이터 조회
    DATASET_FAILED("DAT-500", HttpStatus.BAD_REQUEST,
            "데이터셋을 조회하지 못했습니다. 리포트 정의를 확인하세요."),
    DATASET_FORBIDDEN("DAT-403", HttpStatus.BAD_REQUEST, "허용되지 않은 조회입니다."),

    // --- 표현식
    DATASOURCE_UNKNOWN("DSR-400", HttpStatus.BAD_REQUEST, "등록되지 않은 데이터소스입니다."),
    DATASOURCE_FORBIDDEN("DSR-403", HttpStatus.FORBIDDEN, "이 데이터소스를 사용할 권한이 없습니다."),

    EXPRESSION_INVALID("EXP-400", HttpStatus.BAD_REQUEST, "표현식이 올바르지 않습니다."),

    // --- 출력
    REPORT_FORBIDDEN("RPT-403", HttpStatus.FORBIDDEN, "이 리포트를 열람할 권한이 없습니다."),
    ACCESS_RULE_NOT_FOUND("ACL-404", HttpStatus.NOT_FOUND, "열람 규칙을 찾을 수 없습니다."),
    ACCESS_RULE_DUPLICATE("ACL-409", HttpStatus.CONFLICT, "이미 등록된 열람 규칙입니다."),
    ACCESS_RULE_INVALID("ACL-400", HttpStatus.BAD_REQUEST, "열람 규칙이 올바르지 않습니다."),

    LICENSE_MISSING("LIC-401", HttpStatus.SERVICE_UNAVAILABLE, "라이선스가 없습니다."),
    LICENSE_INVALID("LIC-400", HttpStatus.SERVICE_UNAVAILABLE, "라이선스가 올바르지 않습니다."),
    LICENSE_EXPIRED("LIC-410", HttpStatus.SERVICE_UNAVAILABLE, "라이선스가 만료되었습니다."),
    LICENSE_HOST_MISMATCH("LIC-403", HttpStatus.SERVICE_UNAVAILABLE, "이 서버에서 쓸 수 없는 라이선스입니다."),
    LICENSE_LIMIT("LIC-409", HttpStatus.FORBIDDEN, "라이선스 허용 범위를 넘었습니다."),

    PACKAGE_INVALID("PKG-400", HttpStatus.BAD_REQUEST, "리포트 패키지가 올바르지 않습니다."),
    PACKAGE_NO_KEY("PKG-501", HttpStatus.NOT_IMPLEMENTED, "이 서버에서는 패키지를 만들 수 없습니다."),

    EXPORT_UNSUPPORTED("OUT-400", HttpStatus.BAD_REQUEST, "지원하지 않는 출력 형식입니다."),
    EXPORT_FAILED("OUT-500", HttpStatus.INTERNAL_SERVER_ERROR, "리포트를 내보내지 못했습니다."),

    // --- 그 밖
    INTERNAL("SYS-500", HttpStatus.INTERNAL_SERVER_ERROR,
            "리포트 처리 중 오류가 발생했습니다. 관리자에게 문의하세요.");

    private final String code;
    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(String code, HttpStatus status, String defaultMessage) {
        this.code = code;
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** 화면에 그대로 띄울 수 있는 문구. 내부 사정을 담지 않는다. */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
