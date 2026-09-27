/**
 * 열람 권한 관리 화면.
 *
 * 인라인 스크립트로 두지 않은 이유는 CSP 때문이다. script-src 를 'self' 로 잠가 두었으므로
 * 인라인 <script> 는 실행되지 않는다. 화면 하나 붙이자고 CSP 를 풀면, 리포트 본문에 섞여
 * 들어온 스크립트까지 함께 살아난다. 리포트 아이디는 data 속성으로 받는다.
 */
const REPORT_ID = document.getElementById('accessRoot').dataset.reportId;
const BASE = '/api/reports/' + encodeURIComponent(REPORT_ID) + '/access';

const TYPE_LABEL = {DEPARTMENT: '부서', USER: '사용자', ROLE: '권한'};

function csrf() {
    const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return m ? decodeURIComponent(m[1]) : '';
}

async function call(method, url, body) {
    const res = await fetch(url, {
        method,
        headers: {'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf()},
        body: body ? JSON.stringify(body) : undefined
    });
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) {
        // 서버가 왜 막았는지 그대로 보여 준다. 일반 문구로 덮으면 사유를 알 수 없다.
        throw new Error((data && data.message) || '요청을 처리하지 못했습니다.');
    }
    return data;
}

function escapeHtml(s) {
    return (s || '').replace(/[&<>"]/g, c =>
        ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'}[c]));
}

function render(settings) {
    document.getElementById('mode').value = settings.mode;
    const hint = document.getElementById('modeHint');
    if (settings.mode === 'RESTRICTED' && settings.rules.length === 0) {
        hint.textContent = '규칙이 없어 조회 권한자는 아무도 볼 수 없습니다.';
        hint.style.color = 'var(--danger, #b91c1c)';
    } else {
        hint.textContent = '';
    }

    const body = settings.rules.map(r => `
        <tr>
          <td class="center"><span class="tag">${TYPE_LABEL[r.grantType] || r.grantType}</span></td>
          <td>${escapeHtml(r.grantValue)}</td>
          <td style="color:var(--muted)">${escapeHtml(r.note) || '-'}</td>
          <td class="center">${escapeHtml(r.createdBy) || '-'}</td>
          <td class="center">${(r.createdAt || '').replace('T', ' ').slice(0, 16) || '-'}</td>
          <td class="center"><button class="btn" data-id="${r.id}">삭제</button></td>
        </tr>`).join('');

    document.getElementById('rules').innerHTML = body
        || '<tr><td colspan="6" class="empty">등록된 규칙이 없습니다.</td></tr>';

    document.querySelectorAll('#rules button[data-id]').forEach(b =>
        b.addEventListener('click', () => remove(b.dataset.id)));
}

async function load() {
    try {
        render(await call('GET', BASE));
    } catch (e) {
        alert(e.message);
    }
}

async function remove(id) {
    try {
        await call('DELETE', BASE + '/' + id);
        await load();
    } catch (e) {
        alert(e.message);
    }
}

document.getElementById('add').addEventListener('click', async () => {
    const value = document.getElementById('grantValue').value.trim();
    if (!value) {
        alert('대상 값을 입력하세요.');
        return;
    }
    try {
        await call('POST', BASE, {
            grantType: document.getElementById('grantType').value,
            grantValue: value,
            note: document.getElementById('note').value.trim() || null
        });
        document.getElementById('grantValue').value = '';
        document.getElementById('note').value = '';
        await load();
    } catch (e) {
        alert(e.message);
    }
});

document.getElementById('saveMode').addEventListener('click', async () => {
    try {
        render(await call('PUT', BASE, {mode: document.getElementById('mode').value}));
    } catch (e) {
        alert(e.message);
    }
});

load();
