package kr.co.kreport.export;

import kr.co.kreport.config.KReportProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 리포트 IMAGE 요소의 원본을 읽는다. 허용된 위치에서만 읽는다.
 *
 * <p>여기를 열어 두면 리포트 정의를 쓸 수 있는 사람이 서버를 대신 움직일 수 있다.
 * 주소를 자유롭게 넣게 두면 서버가 내부망으로 요청을 보내고(SSRF), 파일 경로를 넣게 두면
 * 서버의 파일을 이미지로 읽어 낸다. 기본값은 classpath 안쪽뿐이고, 그 밖은
 * 설정에 적어 둔 접두어와 맞을 때만 연다.</p>
 */
@Slf4j
@Component
public class ReportImageLoader {

    /** 외부 이미지를 기다리는 시간. 리포트 출력이 원격 응답에 묶이지 않게 한다. */
    private static final int TIMEOUT_MILLIS = 3_000;

    /** 내려받을 이미지 크기 상한 */
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private final KReportProperties.Image config;

    public ReportImageLoader(KReportProperties properties) {
        this.config = properties.getImage();
    }

    public BufferedImage load(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String trimmed = source.trim();
        try {
            if (trimmed.startsWith("classpath:")) {
                return config.isAllowClasspath() ? readClasspath(trimmed.substring(10)) : reject(trimmed);
            }
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return matchesPrefix(trimmed, config.getAllowedUrlPrefixes())
                        ? readUrl(trimmed) : reject(trimmed);
            }
            return matchesPrefix(trimmed, config.getAllowedFilePrefixes())
                    ? readFile(trimmed) : reject(trimmed);

        } catch (Exception e) {
            log.warn("이미지를 읽지 못했습니다 ({}): {}", trimmed, e.getMessage());
            return null;
        }
    }

    private BufferedImage reject(String source) {
        log.warn("허용되지 않은 이미지 위치라 건너뜁니다: {}", source);
        return null;
    }

    /** 접두어 목록 중 하나로 시작하는지. 목록이 비어 있으면 아무것도 허용하지 않는다. */
    private boolean matchesPrefix(String source, java.util.List<String> prefixes) {
        if (prefixes == null || prefixes.isEmpty()) {
            return false;
        }
        for (String prefix : prefixes) {
            if (prefix != null && !prefix.isBlank() && source.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private BufferedImage readClasspath(String path) throws Exception {
        // 상위 경로 표기를 막아 classpath 밖으로 빠져나가지 못하게 한다
        if (path.contains("..")) {
            return reject("classpath:" + path);
        }
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return ImageIO.read(in);
        }
    }

    private BufferedImage readUrl(String source) throws Exception {
        URL url = URI.create(source).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        // 리다이렉트를 따라가면 허용 접두어 검사를 우회당한다
        connection.setInstanceFollowRedirects(false);
        try (InputStream in = connection.getInputStream()) {
            return ImageIO.read(limited(in));
        } finally {
            connection.disconnect();
        }
    }

    private BufferedImage readFile(String source) throws Exception {
        Path path = Path.of(source).normalize();
        if (!Files.isReadable(path) || Files.isDirectory(path)) {
            return null;
        }
        if (Files.size(path) > MAX_BYTES) {
            log.warn("이미지가 너무 큽니다: {}", source);
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            return ImageIO.read(in);
        }
    }

    /** 응답이 끝없이 이어질 때를 대비해 읽을 양을 제한한다 */
    private InputStream limited(InputStream in) {
        return new java.io.FilterInputStream(in) {
            private int read;

            @Override
            public int read() throws java.io.IOException {
                int b = super.read();
                if (b >= 0 && ++read > MAX_BYTES) {
                    throw new java.io.IOException("이미지가 너무 큽니다.");
                }
                return b;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
                int count = super.read(buffer, offset, length);
                if (count > 0 && (read += count) > MAX_BYTES) {
                    throw new java.io.IOException("이미지가 너무 큽니다.");
                }
                return count;
            }
        };
    }
}
