package kr.co.kreport.deploy;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.kreport.config.KReportProperties;
import kr.co.kreport.license.SignedEnvelope;
import kr.co.kreport.support.ErrorCode;
import kr.co.kreport.support.KReportException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

/**
 * 리포트 정의를 배포용 {@code .krpt} 로 굽고, 다시 읽는다.
 *
 * <h2>무엇을 보장하고 무엇을 보장하지 않는가</h2>
 * <p>보장하는 것은 <b>변조 탐지</b>다. 파일을 한 바이트라도 고치면 서명이 깨져 실행이
 * 거부된다. 압축까지 거치므로 텍스트 편집기로 열어도 SQL 이나 좌표가 보이지 않는다.</p>
 *
 * <p>보장하지 않는 것은 <b>내용의 비밀</b>이다. 푸는 코드가 고객사 서버 안에 있는 이상,
 * 작정하고 뜯으면 정의를 꺼낼 수 있다. 여기에 암호화를 얹어도 열쇠를 같은 서버에 두어야
 * 하므로 사정은 달라지지 않는다. 그 점을 감추지 않고 적어 둔다.</p>
 *
 * <p>구울 때는 개인키가 필요하다. 개인키는 <b>공급사 빌드 환경에만</b> 두고 고객사 서버에는
 * 넣지 않는다. 고객사에 개인키가 있으면 그쪽에서 패키지를 새로 구울 수 있다.</p>
 */
@Slf4j
@Component
public class ReportPackager {

    private final KReportProperties.LicenseConfig config;
    private final ObjectMapper mapper;

    public ReportPackager(KReportProperties properties, ObjectMapper templateObjectMapper) {
        this.config = properties.getLicense();
        this.mapper = templateObjectMapper;
    }

    /** 이 서버에서 패키지를 구울 수 있는지. 개인키가 없으면 읽기 전용 런타임이다. */
    public boolean canBuild() {
        return config.getPrivateKey() != null && !config.getPrivateKey().isBlank();
    }

    /**
     * 정의를 서명된 패키지 한 줄로 굽는다.
     *
     * @return {@code KRPT1.<base64 압축본문>.<base64 서명>}
     */
    public String build(ReportPackage content) {
        if (!canBuild()) {
            throw new KReportException(ErrorCode.PACKAGE_NO_KEY,
                    "패키지를 구울 개인키가 없습니다. 공급사 빌드 환경에서만 만들 수 있습니다.");
        }
        byte[] json = toJson(content);
        byte[] squeezed = deflate(json);
        PrivateKey key = SignedEnvelope.privateKey(config.getPrivateKey());

        log.info("리포트 패키지 생성: {} ({} -> {} bytes)",
                content.reportId(), json.length, squeezed.length);
        return ReportPackage.MAGIC + "." + SignedEnvelope.seal(squeezed, key);
    }

    /**
     * 패키지를 열어 정의를 꺼낸다. 서명이 맞지 않으면 예외.
     *
     * <p>이 메서드가 통과했다는 것은 <b>공급사가 서명한 그대로</b>라는 뜻이다.</p>
     */
    public ReportPackage open(String packaged) {
        if (packaged == null || packaged.isBlank()) {
            throw new KReportException(ErrorCode.PACKAGE_INVALID, "패키지가 비어 있습니다.");
        }
        String compact = packaged.replaceAll("\\s", "");
        String prefix = ReportPackage.MAGIC + ".";
        if (!compact.startsWith(prefix)) {
            throw new KReportException(ErrorCode.PACKAGE_INVALID,
                    "KReport 패키지 파일이 아닙니다. 확장자 " + ReportPackage.EXTENSION + " 파일을 올리세요.");
        }
        byte[] squeezed = SignedEnvelope.open(compact.substring(prefix.length()),
                SignedEnvelope.publicKey(config.getPublicKey()));

        ReportPackage content = fromJson(inflate(squeezed));
        if (content.formatVersion() > ReportPackage.FORMAT_VERSION) {
            throw new KReportException(ErrorCode.PACKAGE_INVALID,
                    "이 런타임보다 새로운 판의 패키지입니다 (판 " + content.formatVersion()
                            + "). KReport 런타임을 올리세요.");
        }
        return content;
    }

    // ---------------------------------------------------------------- 내부

    private byte[] toJson(ReportPackage content) {
        try {
            return mapper.writeValueAsBytes(content);
        } catch (IOException e) {
            throw new KReportException(ErrorCode.PACKAGE_INVALID, "패키지를 만들지 못했습니다.");
        }
    }

    private ReportPackage fromJson(byte[] json) {
        try {
            return mapper.readValue(json, ReportPackage.class);
        } catch (IOException e) {
            throw new KReportException(ErrorCode.PACKAGE_INVALID, "패키지 내용을 해석하지 못했습니다.");
        }
    }

    /**
     * 압축. 리포트 정의는 같은 낱말이 반복되는 JSON 이라 크게 줄고,
     * 그 덕에 편집기로 열어도 알아볼 수 없게 된다.
     */
    private byte[] deflate(byte[] raw) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try (DeflaterOutputStream stream = new DeflaterOutputStream(out, deflater)) {
            stream.write(raw);
        } catch (IOException e) {
            throw new KReportException(ErrorCode.PACKAGE_INVALID, "패키지를 압축하지 못했습니다.");
        } finally {
            deflater.end();
        }
        return out.toByteArray();
    }

    private byte[] inflate(byte[] squeezed) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Inflater inflater = new Inflater();
        try (InflaterOutputStream stream = new InflaterOutputStream(out, inflater)) {
            stream.write(squeezed);
        } catch (IOException e) {
            // 서명이 맞는데 풀리지 않는 경우는 사실상 없다. 형식이 다른 파일일 때 여기로 온다.
            throw new KReportException(ErrorCode.PACKAGE_INVALID, "패키지를 풀지 못했습니다.");
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }

    static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static boolean looksLikePackage(byte[] head) {
        byte[] magic = ReportPackage.MAGIC.getBytes(StandardCharsets.UTF_8);
        return head.length >= magic.length
                && Arrays.equals(Arrays.copyOf(head, magic.length), magic);
    }
}
