package kr.co.kreport.export;

import jakarta.annotation.PostConstruct;
import kr.co.kreport.config.KReportProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PDF 출력에 쓸 한글 폰트를 공급한다.
 *
 * <p>PDF 표준 14 폰트에는 한글 글리프가 없다. 임베딩할 TTF 가 없으면 한글이 전부 깨지므로
 * classpath 에 넣어 둔 폰트를 먼저 찾고, 없으면 OS 기본 폰트 경로를 차례로 뒤진다.
 * 그래도 못 찾으면 라틴 폰트로 떨어뜨리되 기동 시 경고를 남겨 배포 단계에서 잡히게 한다.</p>
 */
@Slf4j
@Component
public class PdfFontProvider {

    /** classpath 우선 탐색 경로 */
    private static final List<String> CLASSPATH_CANDIDATES = List.of(
            "fonts/NanumGothic.ttf",
            "fonts/malgun.ttf"
    );

    /** OS 기본 폰트 탐색 경로 */
    private static final List<String> SYSTEM_CANDIDATES = List.of(
            "C:/Windows/Fonts/malgun.ttf",
            "C:/Windows/Fonts/gulim.ttc",
            "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
            "/usr/share/fonts/nanum/NanumGothic.ttf",
            "/System/Library/Fonts/AppleSDGothicNeo.ttc"
    );

    private static final List<String> BOLD_SYSTEM_CANDIDATES = List.of(
            "C:/Windows/Fonts/malgunbd.ttf",
            "/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf"
    );

    private final String configuredRegular;
    private final String configuredBold;

    private byte[] regularBytes;
    private byte[] boldBytes;

    public PdfFontProvider(KReportProperties properties) {
        this.configuredRegular = properties.getFont().getRegular();
        this.configuredBold = properties.getFont().getBold();
    }

    @PostConstruct
    void loadFontBytes() {
        regularBytes = read(configuredRegular, CLASSPATH_CANDIDATES, SYSTEM_CANDIDATES);
        boldBytes = read(configuredBold, List.of("fonts/NanumGothicBold.ttf"), BOLD_SYSTEM_CANDIDATES);

        if (regularBytes == null) {
            log.warn("PDF 출력용 한글 폰트를 찾지 못했습니다. 한글이 깨져 출력됩니다. "
                    + "src/main/resources/fonts/NanumGothic.ttf 를 두거나 kreport.font.regular 를 지정하세요.");
        }
        if (boldBytes == null) {
            boldBytes = regularBytes;
        }
    }

    /**
     * 문서마다 폰트 객체를 새로 만든다. PDFont 는 소속 문서에 묶여 있어 재사용할 수 없다.
     */
    public Fonts fontsFor(PDDocument document) throws IOException {
        PDFont regular = load(document, regularBytes, Standard14Fonts.FontName.HELVETICA);
        PDFont bold = load(document, boldBytes, Standard14Fonts.FontName.HELVETICA_BOLD);
        return new Fonts(regular, bold, regularBytes != null);
    }

    private PDFont load(PDDocument document, byte[] bytes, Standard14Fonts.FontName fallback)
            throws IOException {
        if (bytes == null) {
            return new PDType1Font(fallback);
        }
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            // 사용한 글자만 서브셋으로 임베딩한다. 전체 임베딩은 파일이 수 MB 씩 불어난다.
            return PDType0Font.load(document, in, true);
        } catch (IOException e) {
            log.warn("폰트 임베딩 실패, 기본 폰트로 대체합니다: {}", e.getMessage());
            return new PDType1Font(fallback);
        }
    }

    private byte[] read(String configured, List<String> classpathCandidates, List<String> systemCandidates) {
        if (configured != null && !configured.isBlank()) {
            byte[] bytes = readPath(configured);
            if (bytes != null) {
                log.info("PDF 폰트 로드: {}", configured);
                return bytes;
            }
            log.warn("설정된 폰트 경로를 읽을 수 없습니다: {}", configured);
        }
        for (String candidate : classpathCandidates) {
            ClassPathResource resource = new ClassPathResource(candidate);
            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    log.info("PDF 폰트 로드: classpath:{}", candidate);
                    return in.readAllBytes();
                } catch (IOException ignored) {
                    // 다음 후보로
                }
            }
        }
        for (String candidate : systemCandidates) {
            byte[] bytes = readPath(candidate);
            if (bytes != null) {
                log.info("PDF 폰트 로드: {}", candidate);
                return bytes;
            }
        }
        return null;
    }

    private byte[] readPath(String path) {
        try {
            Path p = Path.of(path);
            // ttc(폰트 컬렉션)는 PDType0Font.load 가 받지 못하므로 제외한다
            if (!Files.isReadable(p) || path.toLowerCase().endsWith(".ttc")) {
                return null;
            }
            return Files.readAllBytes(p);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param koreanCapable 한글 글리프를 가진 폰트가 실렸는지 여부
     */
    public record Fonts(PDFont regular, PDFont bold, boolean koreanCapable) {

        public PDFont pick(boolean boldStyle) {
            return boldStyle ? bold : regular;
        }
    }
}
