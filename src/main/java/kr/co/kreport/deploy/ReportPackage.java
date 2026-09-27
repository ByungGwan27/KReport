package kr.co.kreport.deploy;

import java.time.LocalDateTime;

/**
 * 배포용으로 구운 리포트 한 건의 속살.
 *
 * <p>서명 대상이라 한 글자도 고칠 수 없다. 고치면 {@link ReportPackager} 가 거부한다.</p>
 *
 * @param formatVersion 포맷 판. 나중에 구조가 바뀌어도 옛 패키지를 읽을 수 있게 남긴다.
 * @param reportId      리포트 식별자
 * @param name          화면에 보일 이름
 * @param templateJson  리포트 정의 원본
 * @param builtAt       구운 시각
 * @param builtBy       구운 사람
 * @param licenseId     이 패키지를 구운 라이선스. 누구에게 납품한 것인지 남긴다.
 */
public record ReportPackage(
        int formatVersion,
        String reportId,
        String name,
        String templateJson,
        LocalDateTime builtAt,
        String builtBy,
        String licenseId) {

    public static final int FORMAT_VERSION = 1;

    /** 파일 맨 앞에 붙는 표시. 엉뚱한 파일을 올렸을 때 바로 알아채려고 둔다. */
    public static final String MAGIC = "KRPT1";

    public static final String EXTENSION = ".krpt";
}
