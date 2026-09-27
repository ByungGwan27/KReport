/*
 * 리포트 디자이너.
 *
 * 화면의 단일 진실은 state.template(서버가 쓰는 것과 동일한 JSON)이고,
 * 캔버스와 속성 패널은 그 투영일 뿐이다. 편집은 항상 템플릿을 고친 뒤 다시 그린다.
 * DOM 을 직접 만지고 나중에 JSON 과 맞추려 들면 어긋난 상태를 저장하게 된다.
 */
(function () {
    'use strict';

    var BAND_LABELS = {
        REPORT_HEADER: '리포트 머리말',
        PAGE_HEADER: '페이지 머리말',
        GROUP_HEADER: '그룹 머리말',
        DETAIL: '본문',
        GROUP_FOOTER: '그룹 꼬리말',
        PAGE_FOOTER: '페이지 꼬리말',
        REPORT_FOOTER: '리포트 꼬리말'
    };

    var CHART_LABELS = {
        COLUMN: '세로 막대', BAR: '가로 막대',
        STACKED_COLUMN: '세로 누적 막대', STACKED_BAR: '가로 누적 막대',
        PERCENT_COLUMN: '세로 100% 누적', PERCENT_BAR: '가로 100% 누적',
        LINE: '꺾은선', AREA: '영역', PIE: '원 그래프', DONUT: '도넛'
    };

    var PAPER = {
        A3: [841.89, 1190.55], A4: [595.28, 841.89], A5: [419.53, 595.28],
        B4: [708.66, 1000.63], B5: [498.9, 708.66],
        LETTER: [612, 792], LEGAL: [612, 1008]
    };

    var state = {
        template: emptyTemplate(),
        selected: null,      // {bandIndex, elementIndex}
        selectedBand: -1,
        zoom: 1,
        grid: 4,
        fields: []
    };

    var canvas = document.getElementById('dzCanvas');
    var message = document.getElementById('dzMessage');
    var originalReportId = document.querySelector('.dz').dataset.reportId || '';

    // ================================================================ 모델

    function emptyTemplate() {
        return {
            schemaVersion: 1,
            reportId: '',
            name: '새 리포트',
            description: '',
            page: {
                paperSize: 'A4', orientation: 'PORTRAIT',
                marginTop: 36, marginRight: 28, marginBottom: 36, marginLeft: 28
            },
            dataSet: {sourceType: 'SQL', sql: '', rows: [], columns: [], maxRows: 50000},
            parameters: [],
            groups: [],
            bands: [
                newBand('PAGE_HEADER', 40),
                newBand('DETAIL', 18),
                newBand('PAGE_FOOTER', 20)
            ]
        };
    }

    function newBand(type, height) {
        return {type: type, groupName: null, height: height || 20, printWhen: null,
            backgroundColor: null, elements: []};
    }

    function defaultStyle() {
        return {
            fontFamily: 'default', fontSize: 9, bold: false, italic: false, underline: false,
            color: '#000000', backgroundColor: null, align: 'LEFT', valign: 'MIDDLE',
            borderTop: 0, borderRight: 0, borderBottom: 0, borderLeft: 0,
            borderColor: '#000000', paddingLeft: 2, paddingRight: 2, wrap: false
        };
    }

    function pageWidth() {
        var p = state.template.page;
        var size = PAPER[p.paperSize] || PAPER.A4;
        return p.orientation === 'LANDSCAPE' ? size[1] : size[0];
    }

    function contentWidth() {
        var p = state.template.page;
        return pageWidth() - p.marginLeft - p.marginRight;
    }

    /** 캔버스에 그릴 순서. 리포트를 실제로 출력했을 때의 세로 순서와 같게 둔다. */
    function bandOrder() {
        var order = ['REPORT_HEADER', 'PAGE_HEADER', 'GROUP_HEADER', 'DETAIL',
            'GROUP_FOOTER', 'PAGE_FOOTER', 'REPORT_FOOTER'];
        var indexed = state.template.bands.map(function (b, i) {
            return {band: b, index: i};
        });
        indexed.sort(function (a, b) {
            var d = order.indexOf(a.band.type) - order.indexOf(b.band.type);
            if (d !== 0) {
                return d;
            }
            // 같은 종류의 그룹 밴드는 그룹 정의 순서를 따른다. 꼬리말은 안쪽 그룹부터.
            var ga = groupOrder(a.band.groupName);
            var gb = groupOrder(b.band.groupName);
            return a.band.type === 'GROUP_FOOTER' ? gb - ga : ga - gb;
        });
        return indexed;
    }

    function groupOrder(name) {
        for (var i = 0; i < state.template.groups.length; i++) {
            if (state.template.groups[i].name === name) {
                return i;
            }
        }
        return 99;
    }

    function snap(value) {
        var g = state.grid;
        return g > 0 ? Math.round(value / g) * g : Math.round(value * 10) / 10;
    }

    function selectedElement() {
        if (!state.selected) {
            return null;
        }
        var band = state.template.bands[state.selected.bandIndex];
        return band ? band.elements[state.selected.elementIndex] : null;
    }

    // ================================================================ 캔버스

    function drawCanvas() {
        canvas.innerHTML = '';
        // 거터(96px) 만큼 폭을 더해야 축소/확대 시 가로 스크롤이 맞는다
        canvas.style.width = 'calc(' + pageWidth() + 'pt + 96px)';
        canvas.style.transform = 'scale(' + state.zoom + ')';

        var p = state.template.page;

        bandOrder().forEach(function (entry) {
            var band = entry.band;
            var bandEl = document.createElement('div');
            bandEl.className = 'dz-band' + (state.selectedBand === entry.index ? ' on' : '');
            bandEl.style.height = band.height + 'pt';
            bandEl.style.paddingLeft = p.marginLeft + 'pt';
            bandEl.style.paddingRight = p.marginRight + 'pt';
            bandEl.dataset.bandIndex = entry.index;
            if (band.backgroundColor) {
                bandEl.style.background = band.backgroundColor;
            }

            var tag = document.createElement('div');
            tag.className = 'dz-band-tag';
            tag.innerHTML = '<em>' + escapeHtml(BAND_LABELS[band.type]) + '</em><small>'
                + (band.groupName ? escapeHtml(band.groupName) + ' · ' : '')
                + band.height + 'pt</small>';
            bandEl.appendChild(tag);

            var inner = document.createElement('div');
            inner.style.cssText = 'position:relative;width:' + contentWidth() + 'pt;height:100%';
            band.elements.forEach(function (element, i) {
                inner.appendChild(drawElement(band, element, entry.index, i));
            });
            bandEl.appendChild(inner);

            var grip = document.createElement('div');
            grip.className = 'dz-band-resize';
            grip.dataset.bandIndex = entry.index;
            bandEl.appendChild(grip);

            canvas.appendChild(bandEl);
        });
    }

    function drawElement(band, element, bandIndex, elementIndex) {
        var el = document.createElement('div');
        var isSelected = state.selected
            && state.selected.bandIndex === bandIndex
            && state.selected.elementIndex === elementIndex;

        el.className = 'dz-el' + (isSelected ? ' sel' : '');
        el.dataset.bandIndex = bandIndex;
        el.dataset.elementIndex = elementIndex;

        var s = element.style || defaultStyle();
        var css = 'left:' + element.x + 'pt;top:' + element.y + 'pt;'
            + 'width:' + element.width + 'pt;height:' + element.height + 'pt;'
            + 'font-size:' + s.fontSize + 'pt;color:' + (s.color || '#000') + ';'
            + 'padding-left:' + (s.paddingLeft || 0) + 'pt;padding-right:' + (s.paddingRight || 0) + 'pt;'
            + 'justify-content:' + flexAlign(s.align) + ';align-items:' + flexValign(s.valign) + ';'
            + 'text-align:' + (s.align || 'LEFT').toLowerCase() + ';'
            + 'white-space:' + (s.wrap ? 'normal' : 'nowrap') + ';';

        if (s.bold) {
            css += 'font-weight:700;';
        }
        if (s.italic) {
            css += 'font-style:italic;';
        }
        if (s.underline) {
            css += 'text-decoration:underline;';
        }
        if (s.backgroundColor) {
            css += 'background:' + s.backgroundColor + ';';
        }
        ['Top', 'Right', 'Bottom', 'Left'].forEach(function (side) {
            var w = s['border' + side];
            if (w > 0) {
                css += 'border-' + side.toLowerCase() + ':' + w + 'pt solid '
                    + (s.borderColor || '#000') + ';';
            }
        });

        if (element.type === 'LINE') {
            var horizontal = element.height <= element.width;
            css += horizontal
                ? 'border-top:' + Math.max(0.5, element.height) + 'pt solid ' + (s.borderColor || '#000') + ';'
                : 'border-left:' + Math.max(0.5, element.width) + 'pt solid ' + (s.borderColor || '#000') + ';';
        }
        if (element.type === 'RECT' && !s.backgroundColor && !hasBorder(s)) {
            css += 'border:0.5pt dashed #9aa5b1;';
        }
        if (element.type === 'CHART') {
            // 실제 그림은 미리보기에서 확인한다. 편집 중에는 자리와 제목만 보이면 된다.
            css += 'border:0.6pt dashed #1b4f8a;background:#f4f8fd;align-items:flex-start;';
        }
        el.style.cssText = css;

        el.appendChild(labelSpan(element));

        if (isSelected) {
            var handle = document.createElement('div');
            handle.className = 'handle';
            el.appendChild(handle);
        }
        return el;
    }

    function labelSpan(element) {
        var span = document.createElement('span');
        if (element.type === 'LABEL') {
            span.textContent = element.text || '';
        } else if (element.type === 'TEXT') {
            span.textContent = element.expression || '';
            span.style.color = '#1b4f8a';
        } else if (element.type === 'QRCODE' || element.type === 'BARCODE') {
            span.textContent = (element.type === 'QRCODE' ? 'QR ' : '||| ') + (element.expression || '');
            span.style.color = '#7a5c00';
            span.style.fontSize = '7pt';
        } else if (element.type === 'IMAGE') {
            span.textContent = element.source || '(이미지)';
            span.style.color = '#6b7280';
            span.style.fontSize = '7pt';
        } else if (element.type === 'CHART') {
            var chart = element.chart || {};
            span.textContent = '▦ ' + (chart.title || CHART_LABELS[chart.type] || '차트');
            span.style.color = '#1b4f8a';
            span.style.fontSize = '7.5pt';
        }
        return span;
    }

    function hasBorder(s) {
        return s.borderTop > 0 || s.borderRight > 0 || s.borderBottom > 0 || s.borderLeft > 0;
    }

    function flexAlign(a) {
        return a === 'CENTER' ? 'center' : (a === 'RIGHT' ? 'flex-end' : 'flex-start');
    }

    function flexValign(v) {
        return v === 'TOP' ? 'flex-start' : (v === 'BOTTOM' ? 'flex-end' : 'center');
    }

    // ================================================================ 드래그 / 리사이즈

    var drag = null;

    canvas.addEventListener('mousedown', function (e) {
        var grip = e.target.closest('.dz-band-resize');
        if (grip) {
            var bandIndex = parseInt(grip.dataset.bandIndex, 10);
            drag = {
                mode: 'band', bandIndex: bandIndex,
                startY: e.clientY, startHeight: state.template.bands[bandIndex].height
            };
            e.preventDefault();
            return;
        }

        var target = e.target.closest('.dz-el');
        if (!target) {
            var bandEl = e.target.closest('.dz-band');
            state.selected = null;
            state.selectedBand = bandEl ? parseInt(bandEl.dataset.bandIndex, 10) : -1;
            renderAll();
            return;
        }

        var bi = parseInt(target.dataset.bandIndex, 10);
        var ei = parseInt(target.dataset.elementIndex, 10);
        state.selected = {bandIndex: bi, elementIndex: ei};
        state.selectedBand = bi;

        var element = state.template.bands[bi].elements[ei];
        drag = {
            mode: e.target.classList.contains('handle') ? 'resize' : 'move',
            element: element,
            startX: e.clientX, startY: e.clientY,
            originX: element.x, originY: element.y,
            originW: element.width, originH: element.height
        };
        renderAll();
        e.preventDefault();
    });

    document.addEventListener('mousemove', function (e) {
        if (!drag) {
            return;
        }
        var dx = (e.clientX - drag.startX) / state.zoom;
        var dy = (e.clientY - drag.startY) / state.zoom;

        if (drag.mode === 'band') {
            var band = state.template.bands[drag.bandIndex];
            band.height = Math.max(6, snap(drag.startHeight + dy));
            drawCanvas();
            renderBandList();
            return;
        }

        var element = drag.element;
        if (drag.mode === 'move') {
            element.x = Math.max(0, Math.min(contentWidth() - element.width, snap(drag.originX + dx)));
            element.y = Math.max(0, snap(drag.originY + dy));
        } else {
            element.width = Math.max(4, Math.min(contentWidth() - element.x, snap(drag.originW + dx)));
            element.height = Math.max(4, snap(drag.originH + dy));
        }
        drawCanvas();
        syncGeometryInputs(element);
    });

    document.addEventListener('mouseup', function () {
        if (drag && drag.mode !== 'band') {
            renderProps();
        }
        drag = null;
    });

    document.addEventListener('keydown', function (e) {
        if (/^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement.tagName)) {
            return;
        }
        var element = selectedElement();
        if (!element) {
            return;
        }
        if (e.key === 'Delete') {
            state.template.bands[state.selected.bandIndex].elements.splice(state.selected.elementIndex, 1);
            state.selected = null;
            renderAll();
            e.preventDefault();
            return;
        }
        var step = e.shiftKey ? 1 : state.grid || 1;
        var moved = true;
        if (e.key === 'ArrowLeft') {
            element.x = Math.max(0, element.x - step);
        } else if (e.key === 'ArrowRight') {
            element.x = Math.min(contentWidth() - element.width, element.x + step);
        } else if (e.key === 'ArrowUp') {
            element.y = Math.max(0, element.y - step);
        } else if (e.key === 'ArrowDown') {
            element.y = element.y + step;
        } else {
            moved = false;
        }
        if (moved) {
            drawCanvas();
            syncGeometryInputs(element);
            e.preventDefault();
        }
    });

    /** 드래그 중에는 속성 패널 전체를 다시 그리지 않고 좌표 칸만 갱신한다 */
    function syncGeometryInputs(element) {
        ['x', 'y', 'width', 'height'].forEach(function (key) {
            var input = document.querySelector('#props [data-prop="' + key + '"]');
            if (input) {
                input.value = element[key];
            }
        });
    }

    // ================================================================ 요소 추가

    function addElement(type) {
        var bandIndex = state.selectedBand >= 0 ? state.selectedBand : defaultBandIndex();
        if (bandIndex < 0) {
            showMessage('error', '먼저 밴드를 추가하세요.');
            return;
        }
        var band = state.template.bands[bandIndex];
        var element = {
            id: type.toLowerCase() + '_' + (band.elements.length + 1),
            type: type,
            x: 0, y: 0,
            width: type === 'QRCODE' ? 40
                : (type === 'LINE' ? contentWidth() : (type === 'CHART' ? 240 : 80)),
            height: type === 'QRCODE' ? 40
                : (type === 'LINE' ? 0.8
                    : (type === 'CHART' ? Math.min(140, band.height) : Math.min(16, band.height))),
            style: defaultStyle()
        };
        if (type === 'LABEL') {
            element.text = '라벨';
        } else if (type === 'TEXT' || type === 'QRCODE' || type === 'BARCODE') {
            element.expression = state.fields.length ? '{' + state.fields[0] + '}' : "''";
            element.nullText = '';
        } else if (type === 'IMAGE') {
            element.source = '';
        } else if (type === 'CHART') {
            element.chart = {
                type: 'COLUMN',
                categoryExpression: state.fields.length ? '{' + state.fields[0] + '}' : "''",
                series: [{
                    name: '계열 1',
                    expression: state.fields.length > 1 ? '{' + state.fields[1] + '}' : '1',
                    aggregation: 'SUM'
                }],
                sort: 'NONE', legend: 'AUTO', showValues: false,
                valueFormat: '#,##0', showGrid: true, maxCategories: 0,
                otherLabel: '기타', title: '', palette: [], valueScale: 'LINEAR'
            };
        }

        band.elements.push(element);
        state.selected = {bandIndex: bandIndex, elementIndex: band.elements.length - 1};
        renderAll();
    }

    function defaultBandIndex() {
        for (var i = 0; i < state.template.bands.length; i++) {
            if (state.template.bands[i].type === 'DETAIL') {
                return i;
            }
        }
        return state.template.bands.length ? 0 : -1;
    }

    // ================================================================ 속성 패널

    function renderProps() {
        var props = document.getElementById('props');
        var element = selectedElement();

        if (!element) {
            props.innerHTML = state.selectedBand >= 0
                ? bandPropsHtml(state.template.bands[state.selectedBand])
                : '<p class="dz-empty">요소를 선택하세요.</p>';
            bindProps();
            return;
        }

        var s = element.style || (element.style = defaultStyle());
        var html = '';

        html += row('종류', '<input type="text" value="' + element.type + '" disabled>');
        html += input('식별자', 'id', element.id, 'text');
        html += '<div class="section">위치와 크기 (pt)</div>';
        html += input('X', 'x', element.x, 'number');
        html += input('Y', 'y', element.y, 'number');
        html += input('너비', 'width', element.width, 'number');
        html += input('높이', 'height', element.height, 'number');

        if (element.type === 'LABEL') {
            html += '<div class="section">내용</div>';
            html += input('텍스트', 'text', element.text, 'text');
        }
        if (element.type === 'TEXT' || element.type === 'QRCODE' || element.type === 'BARCODE') {
            html += '<div class="section">내용</div>';
            html += area('표현식', 'expression', element.expression);
            if (element.type === 'TEXT') {
                html += input('포맷', 'format', element.format, 'text', '#,##0 또는 yyyy-MM-dd');
                html += input('빈값표시', 'nullText', element.nullText, 'text');
                html += check('반복값 생략', 'suppressRepeat', element.suppressRepeat);
            }
        }
        if (element.type === 'IMAGE') {
            html += input('경로', 'source', element.source, 'text', 'classpath:img/logo.png');
        }
        if (element.type === 'CHART') {
            html += chartPropsHtml(element.chart || {});
        }
        html += area('출력조건', 'printWhen', element.printWhen);

        if (element.type !== 'LINE' && element.type !== 'RECT') {
            html += '<div class="section">글꼴</div>';
            html += input('크기', 'style.fontSize', s.fontSize, 'number');
            html += check('굵게', 'style.bold', s.bold);
            html += check('기울임', 'style.italic', s.italic);
            html += check('밑줄', 'style.underline', s.underline);
            html += input('글자색', 'style.color', s.color, 'color');
            html += select('가로정렬', 'style.align', s.align,
                [['LEFT', '왼쪽'], ['CENTER', '가운데'], ['RIGHT', '오른쪽']]);
            html += select('세로정렬', 'style.valign', s.valign,
                [['TOP', '위'], ['MIDDLE', '가운데'], ['BOTTOM', '아래']]);
            html += check('줄바꿈', 'style.wrap', s.wrap);
        }

        html += '<div class="section">상자</div>';
        html += input('배경색', 'style.backgroundColor', s.backgroundColor, 'color');
        html += input('선색', 'style.borderColor', s.borderColor, 'color');
        html += input('선 위', 'style.borderTop', s.borderTop, 'number');
        html += input('선 아래', 'style.borderBottom', s.borderBottom, 'number');
        html += input('선 왼쪽', 'style.borderLeft', s.borderLeft, 'number');
        html += input('선 오른쪽', 'style.borderRight', s.borderRight, 'number');
        html += '<div class="full"><button class="btn" type="button" data-action="borderAll" '
            + 'style="width:100%">네 변 모두 0.6pt</button></div>';
        html += '<div class="full"><button class="btn danger" type="button" data-action="deleteElement" '
            + 'style="width:100%;margin-top:4px">요소 삭제</button></div>';

        props.innerHTML = html;
        bindProps();
    }

    /**
     * 차트 속성. 집계 범위는 따로 고르게 하지 않는다.
     * 차트가 놓인 밴드가 곧 범위라서, 따로 두면 정의와 화면이 어긋날 여지만 생긴다.
     */
    function chartPropsHtml(chart) {
        var html = '<div class="section">차트</div>';
        html += select('종류', 'chart.type', chart.type, [
            ['COLUMN', '세로 막대'], ['BAR', '가로 막대'],
            ['STACKED_COLUMN', '세로 누적 막대'], ['STACKED_BAR', '가로 누적 막대'],
            ['PERCENT_COLUMN', '세로 100% 누적'], ['PERCENT_BAR', '가로 100% 누적'],
            ['LINE', '꺾은선'], ['AREA', '영역'],
            ['PIE', '원 그래프'], ['DONUT', '도넛']
        ]);
        html += input('제목', 'chart.title', chart.title, 'text');
        html += area('항목(축)', 'chart.categoryExpression', chart.categoryExpression);
        html += select('정렬', 'chart.sort', chart.sort, [
            ['NONE', '데이터 순서'], ['VALUE_DESC', '값 큰 순'], ['VALUE_ASC', '값 작은 순'],
            ['CATEGORY_ASC', '항목 오름차순'], ['CATEGORY_DESC', '항목 내림차순']
        ]);
        html += input('항목 상한', 'chart.maxCategories', chart.maxCategories, 'number');
        html += input('묶음 이름', 'chart.otherLabel', chart.otherLabel, 'text');
        html += input('값 포맷', 'chart.valueFormat', chart.valueFormat, 'text', '#,##0');
        html += select('범례', 'chart.legend', chart.legend, [
            ['AUTO', '자동'], ['SHOW', '표시'], ['HIDE', '숨김']
        ]);
        html += check('값 표시', 'chart.showValues', chart.showValues);
        html += check('눈금선', 'chart.showGrid', chart.showGrid !== false);
        if (chart.type === 'LINE') {
            // 로그 축은 꺾은선에서만 뜻이 통한다. 다른 종류에서는 선택지를 아예 두지 않는다.
            html += select('값 축', 'chart.valueScale', chart.valueScale || 'LINEAR', [
                ['LINEAR', '선형'], ['LOG', '로그(10배씩)']
            ]);
        }

        html += '<div class="section">계열</div>';
        var series = chart.series || [];
        for (var i = 0; i < series.length; i++) {
            html += '<div class="full dz-series">'
                + '<div class="dz-series-head"><span>계열 ' + (i + 1) + '</span>'
                + '<button class="btn" type="button" data-series-remove="' + i + '">삭제</button></div>'
                + '<input type="text" data-series="' + i + '" data-series-prop="name" value="'
                + escapeHtml(series[i].name || '') + '" placeholder="범례 이름">'
                + '<input type="text" data-series="' + i + '" data-series-prop="expression" value="'
                + escapeHtml(series[i].expression || '') + '" placeholder="{금액}">'
                + '<select data-series="' + i + '" data-series-prop="aggregation">'
                + aggOptions(series[i].aggregation) + '</select></div>';
        }
        html += '<div class="full"><button class="btn" type="button" data-action="addSeries"'
            + ' style="width:100%">계열 추가</button></div>';
        return html;
    }

    function aggOptions(selected) {
        var options = [['SUM', '합계'], ['AVG', '평균'], ['COUNT', '건수'],
            ['MIN', '최솟값'], ['MAX', '최댓값']];
        var html = '';
        for (var i = 0; i < options.length; i++) {
            html += '<option value="' + options[i][0] + '"'
                + (options[i][0] === selected ? ' selected' : '') + '>' + options[i][1] + '</option>';
        }
        return html;
    }

    function bandPropsHtml(band) {
        var html = '<div class="section">밴드 설정</div>';
        html += row('종류', '<input type="text" value="' + BAND_LABELS[band.type] + '" disabled>');
        html += input('높이', 'band.height', band.height, 'number');
        html += input('배경색', 'band.backgroundColor', band.backgroundColor, 'color');
        html += area('출력조건', 'band.printWhen', band.printWhen);
        if (band.type === 'GROUP_HEADER' || band.type === 'GROUP_FOOTER') {
            var options = state.template.groups.map(function (g) {
                return [g.name, g.name];
            });
            html += select('대상 그룹', 'band.groupName', band.groupName,
                options.length ? options : [['', '(그룹 없음)']]);
        }
        return html;
    }

    function row(label, control) {
        return '<label><span>' + escapeHtml(label) + '</span>' + control + '</label>';
    }

    function input(label, prop, value, type, placeholder) {
        var v = value === null || value === undefined ? '' : value;
        if (type === 'color') {
            // 색은 비워 둘 수 있어야 하므로 색 선택기와 텍스트 입력을 함께 둔다
            return row(label,
                '<span style="display:flex;gap:3px">'
                + '<input type="color" data-prop="' + prop + '" data-color="1" value="'
                + (v || '#ffffff') + '" style="width:30px;padding:0">'
                + '<input type="text" data-prop="' + prop + '" value="' + escapeHtml(v)
                + '" placeholder="비움" style="flex:1"></span>');
        }
        return row(label, '<input type="' + type + '" data-prop="' + prop + '" value="'
            + escapeHtml(v) + '"' + (placeholder ? ' placeholder="' + placeholder + '"' : '') + '>');
    }

    function area(label, prop, value) {
        return row(label, '<textarea data-prop="' + prop + '" rows="2">'
            + escapeHtml(value == null ? '' : value) + '</textarea>');
    }

    function check(label, prop, value) {
        return row(label, '<input type="checkbox" data-prop="' + prop + '"'
            + (value ? ' checked' : '') + ' style="width:auto">');
    }

    function select(label, prop, value, options) {
        var html = '<select data-prop="' + prop + '">';
        options.forEach(function (o) {
            html += '<option value="' + escapeHtml(o[0]) + '"'
                + (o[0] === value ? ' selected' : '') + '>' + escapeHtml(o[1]) + '</option>';
        });
        return row(label, html + '</select>');
    }

    function bindProps() {
        var props = document.getElementById('props');

        props.querySelectorAll('[data-prop]').forEach(function (input) {
            input.addEventListener('input', function () {
                applyProp(this.dataset.prop, this);
            });
            input.addEventListener('change', function () {
                applyProp(this.dataset.prop, this);
            });
        });

        var borderAll = props.querySelector('[data-action="borderAll"]');
        if (borderAll) {
            borderAll.addEventListener('click', function () {
                var s = selectedElement().style;
                s.borderTop = s.borderRight = s.borderBottom = s.borderLeft = 0.6;
                renderAll();
            });
        }
        props.querySelectorAll('[data-series]').forEach(function (field) {
            field.addEventListener('change', function () {
                var chart = selectedElement().chart;
                var index = parseInt(this.dataset.series, 10);
                chart.series[index][this.dataset.seriesProp] = this.value;
                drawCanvas();
            });
        });

        props.querySelectorAll('[data-series-remove]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var chart = selectedElement().chart;
                chart.series.splice(parseInt(this.dataset.seriesRemove, 10), 1);
                renderAll();
            });
        });

        var addSeries = props.querySelector('[data-action="addSeries"]');
        if (addSeries) {
            addSeries.addEventListener('click', function () {
                var chart = selectedElement().chart;
                chart.series.push({
                    name: '계열 ' + (chart.series.length + 1),
                    expression: state.fields.length ? '{' + state.fields[0] + '}' : '1',
                    aggregation: 'SUM'
                });
                renderAll();
            });
        }

        var remove = props.querySelector('[data-action="deleteElement"]');
        if (remove) {
            remove.addEventListener('click', function () {
                state.template.bands[state.selected.bandIndex]
                    .elements.splice(state.selected.elementIndex, 1);
                state.selected = null;
                renderAll();
            });
        }
    }

    function applyProp(path, input) {
        var value;
        if (input.type === 'checkbox') {
            value = input.checked;
        } else if (input.type === 'number') {
            value = input.value === '' ? 0 : parseFloat(input.value);
            if (isNaN(value)) {
                return;
            }
        } else {
            value = input.value === '' ? null : input.value;
        }

        var target;
        if (path.indexOf('band.') === 0) {
            target = state.template.bands[state.selectedBand];
            path = path.substring(5);
        } else if (path.indexOf('chart.') === 0) {
            var owner = selectedElement();
            if (!owner || !owner.chart) {
                return;
            }
            target = owner.chart;
            path = path.substring(6);
        } else {
            target = selectedElement();
            if (!target) {
                return;
            }
            if (path.indexOf('style.') === 0) {
                target = target.style;
                path = path.substring(6);
            }
        }
        target[path] = value;

        // 색상은 선택기와 텍스트가 같은 속성을 공유하므로 서로 값을 맞춰 준다
        if (input.dataset.color !== '1') {
            var picker = document.querySelector('#props input[data-color="1"][data-prop="'
                + input.dataset.prop + '"]');
            if (picker && typeof value === 'string' && /^#[0-9a-fA-F]{6}$/.test(value)) {
                picker.value = value;
            }
        } else {
            var textInput = document.querySelector('#props input[type=text][data-prop="'
                + input.dataset.prop + '"]');
            if (textInput) {
                textInput.value = value;
            }
        }

        drawCanvas();
        if (path === 'height' || path === 'groupName') {
            renderBandList();
        }
        if (input.dataset.prop === 'chart.type') {
            renderProps();
        }
    }

    // ================================================================ 좌측 목록

    function renderBandList() {
        var list = document.getElementById('bandList');
        list.innerHTML = '';

        bandOrder().forEach(function (entry) {
            var band = entry.band;
            var item = document.createElement('div');
            item.className = 'dz-item' + (state.selectedBand === entry.index ? ' on' : '');
            item.innerHTML = '<span class="name">' + escapeHtml(BAND_LABELS[band.type])
                + (band.groupName ? ' · ' + escapeHtml(band.groupName) : '')
                + '</span><span class="sub">' + band.height + 'pt · '
                + band.elements.length + '개</span><button type="button" title="삭제">×</button>';

            item.addEventListener('click', function (e) {
                if (e.target.tagName === 'BUTTON') {
                    state.template.bands.splice(entry.index, 1);
                    state.selected = null;
                    state.selectedBand = -1;
                } else {
                    state.selectedBand = entry.index;
                    state.selected = null;
                }
                renderAll();
            });
            list.appendChild(item);
        });
    }

    function renderParamList() {
        var list = document.getElementById('paramList');
        list.innerHTML = '';

        state.template.parameters.forEach(function (p, i) {
            var item = document.createElement('div');
            item.className = 'dz-item';
            item.innerHTML = '<span class="name">' + escapeHtml(p.label || p.name)
                + '</span><span class="sub">' + p.name + ' · ' + p.dataType
                + '</span><button type="button" title="삭제">×</button>';

            item.addEventListener('click', function (e) {
                if (e.target.tagName === 'BUTTON') {
                    state.template.parameters.splice(i, 1);
                    renderParamList();
                    return;
                }
                editParameter(i);
            });
            list.appendChild(item);
        });
    }

    function editParameter(index) {
        var p = state.template.parameters[index];
        var name = prompt('파라미터명 (SQL 에서 :이름 으로 참조)', p.name);
        if (name === null) {
            return;
        }
        var label = prompt('화면에 표시할 이름', p.label || name);
        if (label === null) {
            return;
        }
        var type = prompt('자료형 (STRING / NUMBER / DATE / BOOLEAN)', p.dataType);
        if (type === null) {
            return;
        }
        var def = prompt('기본값', p.defaultValue || '');

        p.name = name.trim();
        p.label = label.trim();
        p.dataType = (type || 'STRING').trim().toUpperCase();
        p.defaultValue = def;
        renderParamList();
    }

    function renderGroupList() {
        var list = document.getElementById('groupList');
        list.innerHTML = '';

        state.template.groups.forEach(function (g, i) {
            var item = document.createElement('div');
            item.className = 'dz-item';
            item.innerHTML = '<span class="name">' + escapeHtml(g.name)
                + '</span><span class="sub">' + escapeHtml(g.expression || '')
                + (g.pageBreak ? ' · 쪽나눔' : '') + '</span><button type="button" title="삭제">×</button>';

            item.addEventListener('click', function (e) {
                if (e.target.tagName === 'BUTTON') {
                    state.template.groups.splice(i, 1);
                    renderAll();
                    return;
                }
                var expression = prompt('그룹 기준식 (예: {DEPT_CODE})', g.expression || '');
                if (expression === null) {
                    return;
                }
                g.expression = expression;
                g.pageBreak = confirm('그룹이 바뀔 때 쪽을 나눌까요?');
                renderAll();
            });
            list.appendChild(item);
        });
    }

    function renderFields() {
        var box = document.getElementById('fieldList');
        box.innerHTML = '';
        state.fields.forEach(function (name) {
            var chip = document.createElement('span');
            chip.textContent = name;
            chip.title = '클릭하면 선택한 밴드에 필드를 추가합니다';
            chip.addEventListener('click', function () {
                addFieldElement(name);
            });
            box.appendChild(chip);
        });
    }

    /** 필드 칩을 누르면 해당 컬럼을 출력하는 요소를 만들어 준다 */
    function addFieldElement(name) {
        addElement('TEXT');
        var element = selectedElement();
        element.expression = '{' + name + '}';
        element.id = name.toLowerCase();
        renderAll();
    }

    function renderPageForm() {
        var p = state.template.page;
        document.getElementById('paperSize').value = p.paperSize;
        document.getElementById('orientation').value = p.orientation;
        document.getElementById('marginTop').value = p.marginTop;
        document.getElementById('marginBottom').value = p.marginBottom;
        document.getElementById('marginLeft').value = p.marginLeft;
        document.getElementById('marginRight').value = p.marginRight;
        document.getElementById('sql').value = state.template.dataSet.sql || '';
        document.getElementById('reportName').value = state.template.name || '';
    }

    function renderAll() {
        drawCanvas();
        renderBandList();
        renderParamList();
        renderGroupList();
        renderProps();
    }

    // ================================================================ 서버 연동

    function showMessage(kind, text, details) {
        var html = '<div class="msg ' + kind + '">' + escapeHtml(text);
        if (details && details.length) {
            html += '<ul>';
            details.forEach(function (d) {
                html += '<li>' + escapeHtml(d) + '</li>';
            });
            html += '</ul>';
        }
        message.innerHTML = html + '</div>';
        if (kind === 'info') {
            setTimeout(function () {
                message.innerHTML = '';
            }, 2500);
        }
    }

    function collectTemplate() {
        var t = state.template;
        t.reportId = document.getElementById('reportId').value.trim();
        t.name = document.getElementById('reportName').value.trim();
        t.page.paperSize = document.getElementById('paperSize').value;
        t.page.orientation = document.getElementById('orientation').value;
        t.page.marginTop = parseFloat(document.getElementById('marginTop').value) || 0;
        t.page.marginBottom = parseFloat(document.getElementById('marginBottom').value) || 0;
        t.page.marginLeft = parseFloat(document.getElementById('marginLeft').value) || 0;
        t.page.marginRight = parseFloat(document.getElementById('marginRight').value) || 0;
        t.dataSet.sql = document.getElementById('sql').value;
        return t;
    }

    function defaultParameters() {
        var values = {};
        state.template.parameters.forEach(function (p) {
            values[p.name] = p.defaultValue == null ? '' : p.defaultValue;
        });
        return values;
    }


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

    function postJson(url, body) {
        return fetch(url, {
            method: 'POST',
            headers: csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify(body)
        }).then(function (res) {
            if (res.status === 401) {
                window.location.href = '/login';
                return {ok: false, status: 401, body: {}};
            }
            return res.json().catch(function () {
                return {};
            }).then(function (json) {
                return {ok: res.ok, status: res.status, body: json};
            });
        });
    }

    document.getElementById('btnValidate').addEventListener('click', function () {
        postJson('/api/reports/validate', collectTemplate()).then(function (r) {
            if (r.body.valid) {
                showMessage('info', '정의에 문제가 없습니다.');
            } else {
                showMessage('error', '정의에 오류가 있습니다.', r.body.errors);
            }
        });
    });

    document.getElementById('btnSave').addEventListener('click', function () {
        var template = collectTemplate();
        if (!template.reportId) {
            showMessage('error', '리포트 ID 를 입력하세요.');
            return;
        }
        var category = document.getElementById('category').value.trim();
        var url = '/api/reports/' + encodeURIComponent(template.reportId)
            + (category ? '?category=' + encodeURIComponent(category) : '');

        postJson(url, template).then(function (r) {
            if (!r.ok) {
                showMessage('error', r.body.message || '저장하지 못했습니다.', r.body.details);
                return;
            }
            showMessage('info', '저장했습니다.');
            if (!originalReportId) {
                history.replaceState(null, '', '/designer/' + encodeURIComponent(template.reportId));
                originalReportId = template.reportId;
            }
        });
    });

    document.getElementById('btnPreview').addEventListener('click', function () {
        postJson('/api/reports/preview', {
            template: collectTemplate(),
            parameters: defaultParameters()
        }).then(function (r) {
            if (!r.ok) {
                showMessage('error', r.body.message || '미리보기에 실패했습니다.', r.body.details);
                return;
            }
            var layer = document.getElementById('previewLayer');
            document.getElementById('previewCanvas').innerHTML =
                '<style>' + r.body.css + '</style>' + r.body.html;
            document.getElementById('previewStat').textContent =
                r.body.rowCount + '행 · ' + r.body.pageCount + '쪽 · ' + r.body.elapsedMillis + 'ms';
            layer.style.display = 'flex';
        });
    });

    document.getElementById('btnClosePreview').addEventListener('click', function () {
        document.getElementById('previewLayer').style.display = 'none';
    });

    document.getElementById('btnFields').addEventListener('click', function () {
        postJson('/api/reports/fields', {
            sql: document.getElementById('sql').value,
            parameters: defaultParameters()
        }).then(function (r) {
            if (!r.ok) {
                showMessage('error', r.body.message || '필드를 읽지 못했습니다.', r.body.details);
                return;
            }
            state.fields = r.body.columns || [];
            renderFields();
            showMessage('info', state.fields.length + '개 필드를 읽었습니다.');
        });
    });

    // ================================================================ 기타 조작

    document.querySelectorAll('[data-add]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            addElement(this.dataset.add);
        });
    });

    document.getElementById('btnAddBand').addEventListener('click', function () {
        var type = document.getElementById('newBandType').value;
        var band = newBand(type, type === 'DETAIL' ? 18 : 24);
        if (type === 'GROUP_HEADER' || type === 'GROUP_FOOTER') {
            if (!state.template.groups.length) {
                showMessage('error', '그룹을 먼저 추가하세요.');
                return;
            }
            band.groupName = state.template.groups[0].name;
        }
        state.template.bands.push(band);
        state.selectedBand = state.template.bands.length - 1;
        state.selected = null;
        renderAll();
    });

    document.getElementById('btnAddParam').addEventListener('click', function () {
        state.template.parameters.push({
            name: 'param' + (state.template.parameters.length + 1),
            label: '조건' + (state.template.parameters.length + 1),
            dataType: 'STRING', required: false, defaultValue: '', options: []
        });
        renderParamList();
        editParameter(state.template.parameters.length - 1);
    });

    document.getElementById('btnAddGroup').addEventListener('click', function () {
        var name = prompt('그룹명 (밴드에서 참조할 이름)', 'group' + (state.template.groups.length + 1));
        if (!name) {
            return;
        }
        var expression = prompt('그룹 기준식 (예: {DEPT_CODE})', '');
        state.template.groups.push({
            name: name.trim(), expression: expression || '', pageBreak: false,
            resetPageNumber: false, repeatHeaderOnNewPage: true
        });
        renderAll();
    });

    document.getElementById('dzZoom').addEventListener('change', function () {
        state.zoom = parseFloat(this.value);
        drawCanvas();
    });

    document.getElementById('dzGrid').addEventListener('change', function () {
        state.grid = parseInt(this.value, 10);
    });

    ['paperSize', 'orientation', 'marginTop', 'marginBottom', 'marginLeft', 'marginRight']
        .forEach(function (id) {
            document.getElementById(id).addEventListener('change', function () {
                collectTemplate();
                drawCanvas();
            });
        });

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    // ================================================================ 시작

    function boot() {
        if (!originalReportId) {
            renderPageForm();
            renderAll();
            return;
        }
        fetch('/api/reports/' + encodeURIComponent(originalReportId))
            .then(function (res) {
                return res.json();
            })
            .then(function (template) {
                state.template = template;
                state.template.bands.forEach(function (band) {
                    band.elements.forEach(function (e) {
                        e.style = Object.assign(defaultStyle(), e.style || {});
                    });
                });
                renderPageForm();
                renderAll();
            })
            .catch(function (e) {
                showMessage('error', '리포트를 불러오지 못했습니다: ' + e.message);
            });
    }

    boot();
})();
