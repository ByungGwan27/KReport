package kr.co.kreport.export;

import java.util.Locale;

/** 출력 형식 */
public enum ExportFormat {

    HTML("text/html; charset=UTF-8", "html"),
    PDF("application/pdf", "pdf"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    CSV("text/csv; charset=UTF-8", "csv");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }

    public static ExportFormat of(String name) {
        if (name == null || name.isBlank()) {
            return HTML;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 출력 형식입니다: " + name);
        }
    }

    /** 브라우저에 그대로 띄울 수 있는 형식인지 */
    public boolean isInline() {
        return this == HTML || this == PDF;
    }
}
