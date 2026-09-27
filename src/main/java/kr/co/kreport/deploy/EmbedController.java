package kr.co.kreport.deploy;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.kreport.access.CurrentViewer;
import kr.co.kreport.access.ReportAccessService;
import kr.co.kreport.license.LicenseService;
import kr.co.kreport.service.ReportDefinitionService;
import kr.co.kreport.service.ReportRunService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 고객사 화면 안에 리포트를 끼워 넣는 창구.
 *
 * <p>고객사가 JSP 든 다른 무엇으로 만든 화면에 {@code <div>} 하나와 스크립트 한 줄을 넣으면,
 * 그 자리에 리포트가 그려진다. 리포트를 그리는 일 자체는 여전히 이 서버가 한다 —
 * 데이터 조회와 쪽 나눔은 JDBC 와 레이아웃 엔진이 있어야 하므로 브라우저로 옮길 수 없다.
 * 고객사 화면에 들어가는 것은 <b>가져다 보여 주는 코드</b>이고, 리포트 정의와 SQL 은
 * 서버에 남는다. 그래서 화면 소스를 아무리 들여다봐도 조회 조건 말고는 나오지 않는다.</p>
 *
 * <p>여기서도 라이선스와 열람 권한을 본다. 임베드 경로만 열어 두고 검사를 빼면
 * 앞문을 잠그고 뒷문을 열어 두는 셈이 된다.</p>
 */
@Slf4j
@RestController
public class EmbedController {

    private final ReportRunService runService;
    private final ReportDefinitionService definitionService;
    private final ReportAccessService accessService;
    private final CurrentViewer currentViewer;
    private final LicenseService licenses;

    public EmbedController(ReportRunService runService,
                           ReportDefinitionService definitionService,
                           ReportAccessService accessService,
                           CurrentViewer currentViewer,
                           LicenseService licenses) {
        this.runService = runService;
        this.definitionService = definitionService;
        this.accessService = accessService;
        this.currentViewer = currentViewer;
        this.licenses = licenses;
    }

    /**
     * 끼워 넣을 자리를 채우는 스크립트.
     *
     * <p>고객사 화면에는 이 한 줄만 들어간다. 조회 조건은 {@code data-} 속성으로 넘긴다.</p>
     */
    @GetMapping(value = "/embed/kreport.js", produces = "application/javascript;charset=UTF-8")
    public String loader() {
        return LOADER_JS;
    }

    /**
     * 리포트 본문(HTML 조각과 CSS).
     *
     * <p>{@code /api/reports/{id}/render} 와 같은 일을 하지만 GET 이라 스크립트에서 부르기
     * 쉽고, 조회 조건을 질의 문자열로 받는다.</p>
     */
    @GetMapping(value = "/embed/{reportId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReportRunService.ViewerContent render(@PathVariable String reportId,
                                                 @RequestParam Map<String, String> query,
                                                 HttpServletRequest request) {
        licenses.requireValid();
        accessService.requireView(definitionService.get(reportId), currentViewer.resolve(request));

        Map<String, Object> parameters = new LinkedHashMap<>(query);
        return runService.renderForViewer(reportId, parameters, caller(request));
    }

    /**
     * 고객사 화면에 붙여 넣을 코드 조각을 만들어 준다.
     *
     * <p>손으로 적게 하면 오타로 끼워 넣기가 안 되는 문의가 들어온다. 화면에서 복사해
     * 붙이게 한다.</p>
     */
    @GetMapping(value = "/embed/{reportId}/snippet", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> snippet(@PathVariable String reportId, HttpServletRequest request) {
        definitionService.get(reportId);
        String base = baseUrl(request);

        Map<String, String> out = new LinkedHashMap<>();
        out.put("html", """
                <div class="kreport" data-report="%s" data-params="fiscalYear=2026"></div>
                <script src="%s/embed/kreport.js"></script>"""
                .formatted(reportId, base));
        out.put("jsp", """
                <%%-- KReport %s --%%>
                <div class="kreport"
                     data-report="%s"
                     data-params="fiscalYear=<%%= request.getParameter("year") %%>"></div>
                <script src="%s/embed/kreport.js"></script>"""
                .formatted(reportId, reportId, base));
        out.put("iframe", """
                <iframe src="%s/viewer/%s" style="width:100%%;height:800px;border:0"></iframe>"""
                .formatted(base, reportId));
        return out;
    }

    private ReportRunService.Caller caller(HttpServletRequest request) {
        return new ReportRunService.Caller(currentViewer.resolve(request), clientIp(request));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }

    /** 스니펫에 박아 줄 주소. 역방향 프록시 뒤에 있으면 그쪽 주소를 따른다. */
    private String baseUrl(HttpServletRequest request) {
        String proto = header(request, "X-Forwarded-Proto", request.getScheme());
        String host = header(request, "X-Forwarded-Host",
                request.getServerName() + portSuffix(request));
        return proto + "://" + host + request.getContextPath();
    }

    private String header(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value.split(",")[0].trim();
    }

    private String portSuffix(HttpServletRequest request) {
        int port = request.getServerPort();
        boolean standard = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        return standard ? "" : ":" + port;
    }

    /**
     * 끼워 넣기 스크립트.
     *
     * <p>파일로 두지 않고 문자열로 들고 있는 이유는, 이 스크립트가 <b>런타임 주소를 스스로
     * 알아내야</b> 하기 때문이다. 자기 {@code src} 에서 주소를 뽑으므로 고객사가 어떤
     * 경로에 올리든 그대로 동작한다.</p>
     */
    private static final String LOADER_JS = """
            /* KReport 임베드 로더 */
            (function () {
              var me = document.currentScript;
              var base = me.src.replace(/\\/embed\\/kreport\\.js.*$/, '');

              function draw(box) {
                var id = box.getAttribute('data-report');
                if (!id || box.dataset.krDone) return;
                box.dataset.krDone = '1';
                box.textContent = '리포트를 불러오는 중...';

                var qs = box.getAttribute('data-params') || '';
                fetch(base + '/embed/' + encodeURIComponent(id) + (qs ? '?' + qs : ''),
                      {credentials: 'include'})
                  .then(function (r) {
                    if (!r.ok) return r.json().then(function (e) { throw new Error(e.message || r.status); });
                    return r.json();
                  })
                  .then(function (data) {
                    /* 리포트 CSS 가 고객사 화면 전체를 물들이지 않도록 한 번만, 범위를 좁혀 넣는다 */
                    if (data.css && !document.getElementById('kreport-css')) {
                      var style = document.createElement('style');
                      style.id = 'kreport-css';
                      style.textContent = data.css;
                      document.head.appendChild(style);
                    }
                    box.innerHTML = data.html;
                  })
                  .catch(function (e) {
                    box.textContent = '리포트를 불러오지 못했습니다: ' + e.message;
                  });
              }

              function scan() {
                document.querySelectorAll('.kreport[data-report]').forEach(draw);
              }
              if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', scan);
              } else {
                scan();
              }
              window.KReport = {refresh: scan};
            })();
            """;
}
