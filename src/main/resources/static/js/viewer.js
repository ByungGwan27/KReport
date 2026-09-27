/* 리포트 뷰어. 조회 조건을 모아 서버에 렌더를 요청하고 결과 HTML을 붙인다. */
(function () {
    'use strict';

    var form = document.getElementById('paramForm');
    var reportId = form.dataset.reportId;
    var canvas = document.getElementById('canvas');
    var message = document.getElementById('message');
    var pager = document.getElementById('pager');
    var pageInfo = document.getElementById('pageInfo');
    var stat = document.getElementById('stat');

    var styleTag = document.createElement('style');
    document.head.appendChild(styleTag);

    var wrap = document.getElementById('canvasWrap');
    var pageCount = 0;
    var currentPage = 1;
    /** 버튼으로 쪽을 옮기는 중인지. 그동안은 스크롤 추적을 쉰다. */
    var syncingScroll = false;


    /**
     * CSRF 토큰. 서버가 쿠키로 내려 준 값을 헤더로 되돌려 보낸다.
     * 교차 출처에서는 이 쿠키를 읽을 수 없으므로 남의 사이트가 대신 요청을 보낼 수 없다.
     */
    function csrfHeaders(base) {
        var headers = base || {};
        var match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        if (match) {
            headers['X-XSRF-TOKEN'] = decodeURIComponent(match[1]);
        }
        return headers;
    }

    function collectParameters() {
        var params = {};
        var fields = form.querySelectorAll('input[name], select[name]');
        for (var i = 0; i < fields.length; i++) {
            params[fields[i].name] = fields[i].value;
        }
        return params;
    }

    function showError(text, details) {
        var html = '<div class="msg error"><strong>' + escapeHtml(text) + '</strong>';
        if (details && details.length) {
            html += '<ul>';
            for (var i = 0; i < details.length; i++) {
                html += '<li>' + escapeHtml(details[i]) + '</li>';
            }
            html += '</ul>';
        }
        message.innerHTML = html + '</div>';
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function render(event) {
        if (event) {
            event.preventDefault();
        }
        message.innerHTML = '<div class="msg info">조회 중입니다...</div>';
        canvas.innerHTML = '';

        fetch('/api/reports/' + encodeURIComponent(reportId) + '/render', {
            method: 'POST',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify(collectParameters())
        }).then(function (res) {
            return res.json().catch(function () {
                return {};
            }).then(function (body) {
                return {ok: res.ok, status: res.status, body: body};
            });
        }).then(function (result) {
            if (result.status === 401) {
                window.location.href = '/login';
                return;
            }
            if (!result.ok) {
                showError(result.body.message || '조회에 실패했습니다.', result.body.details);
                pager.style.display = 'none';
                return;
            }
            message.innerHTML = '';
            styleTag.textContent = result.body.css;
            canvas.innerHTML = result.body.html;

            pageCount = result.body.pageCount;
            currentPage = 1;
            pager.style.display = pageCount > 0 ? 'flex' : 'none';
            stat.textContent = result.body.rowCount + '행 · ' + pageCount + '쪽 · '
                + result.body.elapsedMillis + 'ms';
            updatePageInfo();
        }).catch(function (e) {
            showError('서버와 통신하지 못했습니다: ' + e.message);
        });
    }

    function updatePageInfo() {
        pageInfo.textContent = currentPage + ' / ' + pageCount;
    }

    /**
     * 쪽 이동.
     *
     * scrollIntoView 로 옮기면 부드러운 스크롤이 진행되는 동안 scroll 이벤트가 계속 터지고,
     * 그 이벤트가 현재 쪽 표시를 원래 쪽으로 되돌려 버린다. 이동할 위치를 직접 지정하고,
     * 그동안 위치 추적을 잠시 멈춘다.
     */
    function goPage(no) {
        if (no < 1 || no > pageCount) {
            return;
        }
        var target = canvas.querySelector('.kr-page[data-page="' + no + '"]');
        if (!target) {
            return;
        }
        currentPage = no;
        updatePageInfo();

        syncingScroll = true;
        wrap.scrollTop = target.offsetTop - canvas.offsetTop;
        // 스크롤 이벤트가 한 박자 늦게 오므로 다음 프레임까지 추적을 막는다
        window.setTimeout(function () {
            syncingScroll = false;
        }, 80);
    }

    function exportFile(format) {
        var params = collectParameters();
        var query = ['format=' + encodeURIComponent(format)];
        for (var key in params) {
            if (Object.prototype.hasOwnProperty.call(params, key)) {
                query.push(encodeURIComponent(key) + '=' + encodeURIComponent(params[key]));
            }
        }
        window.open('/api/reports/' + encodeURIComponent(reportId) + '/export?' + query.join('&'),
            format === 'PDF' ? '_blank' : '_self');
    }

    form.addEventListener('submit', render);

    document.getElementById('prevPage').addEventListener('click', function () {
        goPage(currentPage - 1);
    });
    document.getElementById('nextPage').addEventListener('click', function () {
        goPage(currentPage + 1);
    });
    document.getElementById('zoom').addEventListener('change', function () {
        canvas.style.transform = 'scale(' + this.value + ')';
    });
    document.getElementById('btnPrint').addEventListener('click', function () {
        window.print();
    });

    var exportButtons = document.querySelectorAll('[data-export]');
    for (var i = 0; i < exportButtons.length; i++) {
        exportButtons[i].addEventListener('click', function () {
            exportFile(this.dataset.export);
        });
    }

    // 스크롤 위치로 현재 쪽 표시를 따라가게 한다
    wrap.addEventListener('scroll', function () {
        if (syncingScroll) {
            return;
        }
        var pages = canvas.querySelectorAll('.kr-page');
        var wrapTop = this.getBoundingClientRect().top;
        for (var i = 0; i < pages.length; i++) {
            if (pages[i].getBoundingClientRect().bottom > wrapTop + 40) {
                var no = parseInt(pages[i].dataset.page, 10);
                if (no !== currentPage) {
                    currentPage = no;
                    updatePageInfo();
                }
                return;
            }
        }
    });

    render();
})();
