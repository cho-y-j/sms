// ===== State =====
const state = {
    token: localStorage.getItem('admin_token'),
    currentPage: 'dashboard',
    users: { page: 1, data: [], total: 0 },
    payments: { page: 1, data: [], total: 0 },
};

const API = '/api/admin';
const PAGE_SIZE = 20;

// ===== API Helper =====
async function api(path, options = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (state.token) headers['Authorization'] = `Bearer ${state.token}`;
    const res = await fetch(`${API}${path}`, { ...options, headers });
    if (res.status === 401) { logout(); throw new Error('Unauthorized'); }
    if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err.error || `HTTP ${res.status}`);
    }
    return res.json();
}

// ===== Toast =====
function showToast(message, type = 'success') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}

// ===== Auth =====
async function login() {
    const username = document.getElementById('loginUsername').value;
    const password = document.getElementById('loginPassword').value;
    const errorEl = document.getElementById('loginError');
    errorEl.style.display = 'none';
    try {
        const data = await fetch(`${API}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        }).then(r => r.json());
        if (data.accessToken) {
            state.token = data.accessToken;
            localStorage.setItem('admin_token', data.accessToken);
            showApp();
        } else {
            errorEl.textContent = data.error || '로그인 실패';
            errorEl.style.display = 'block';
        }
    } catch (e) {
        errorEl.textContent = '서버 연결 실패';
        errorEl.style.display = 'block';
    }
}

function logout() {
    state.token = null;
    localStorage.removeItem('admin_token');
    document.getElementById('loginPage').style.display = 'flex';
    document.getElementById('appPage').style.display = 'none';
}

// ===== Navigation =====
function showApp() {
    document.getElementById('loginPage').style.display = 'none';
    document.getElementById('appPage').style.display = 'flex';
    navigate('dashboard');
}

function navigate(page) {
    state.currentPage = page;
    document.querySelectorAll('.nav-item').forEach(el => el.classList.toggle('active', el.dataset.page === page));
    const content = document.getElementById('contentArea');
    content.innerHTML = '<div class="loading">로딩 중...</div>';

    switch (page) {
        case 'dashboard': loadDashboard(); break;
        case 'users': state.users.page = 1; loadUsers(); break;
        case 'payments': state.payments.page = 1; loadPayments(); break;
        case 'usage': loadUsage(); break;
        case 'config': loadConfig(); break;
        case 'ai': loadAI(); break;
    }
}

// ===== Format Helpers =====
function formatNumber(n) { return (n || 0).toLocaleString('ko-KR'); }
function formatCurrency(n) { return (n || 0).toLocaleString('ko-KR') + '원'; }
function formatDate(d) {
    if (!d) return '-';
    try { return new Date(d).toLocaleString('ko-KR'); } catch { return d; }
}
function formatDateShort(d) {
    if (!d) return '-';
    try { return new Date(d).toLocaleDateString('ko-KR'); } catch { return d; }
}
function statusBadge(status) { return `<span class="badge badge-${status}">${status === 'active' ? '활성' : status === 'suspended' ? '정지' : status}</span>`; }
function tierBadge(tier) { return `<span class="badge badge-${tier}">${tier === 'free' ? '무료' : tier === 'paid' ? '유료' : tier === 'premium' ? '프리미엄' : tier}</span>`; }
function paymentStatusBadge(s) {
    const labels = { completed: '완료', pending: '대기', failed: '실패' };
    return `<span class="badge badge-${s}">${labels[s] || s}</span>`;
}

// ===== Dashboard =====
async function loadDashboard() {
    try {
        const data = await api('/dashboard');
        document.getElementById('contentArea').innerHTML = `
            <div class="page-header"><h1>대시보드</h1><p>서비스 현황 요약</p></div>
            <div class="stats-grid">
                <div class="stat-card blue"><div class="stat-icon">👥</div><div class="stat-value">${formatNumber(data.totalUsers)}</div><div class="stat-label">전체 회원</div></div>
                <div class="stat-card green"><div class="stat-icon">✅</div><div class="stat-value">${formatNumber(data.activeUsers)}</div><div class="stat-label">활성 회원</div></div>
                <div class="stat-card orange"><div class="stat-icon">💬</div><div class="stat-value">${formatNumber(data.todayMessages)}</div><div class="stat-label">오늘 발송 건수</div></div>
                <div class="stat-card purple"><div class="stat-icon">💰</div><div class="stat-value">${formatCurrency(data.monthlyRevenue)}</div><div class="stat-label">이번 달 매출</div></div>
            </div>
            <div class="card">
                <div class="card-title">최근 결제 내역</div>
                <div class="table-wrapper">
                    <table>
                        <thead><tr><th>결제 ID</th><th>금액</th><th>유형</th><th>상태</th><th>일시</th></tr></thead>
                        <tbody>
                            ${(data.recentPayments || []).map(p => `
                                <tr class="no-hover">
                                    <td>${p.id ? p.id.substring(0,8) + '...' : '-'}</td>
                                    <td>${formatCurrency(p.amount)}</td>
                                    <td>${p.type || '-'}</td>
                                    <td>${paymentStatusBadge(p.status)}</td>
                                    <td>${formatDate(p.createdAt)}</td>
                                </tr>
                            `).join('') || '<tr class="no-hover"><td colspan="5" style="text-align:center;color:#888;">결제 내역이 없습니다</td></tr>'}
                        </tbody>
                    </table>
                </div>
            </div>`;
    } catch (e) { showError(e.message); }
}

// ===== Users =====
async function loadUsers() {
    try {
        const search = state.users.search || '';
        const data = await api(`/users?page=${state.users.page}&limit=${PAGE_SIZE}`);
        state.users.data = data.data || [];
        state.users.total = data.total || 0;
        const totalPages = Math.ceil(state.users.total / PAGE_SIZE) || 1;

        let filtered = state.users.data;
        if (search) {
            const s = search.toLowerCase();
            filtered = filtered.filter(u =>
                (u.displayName || '').toLowerCase().includes(s) ||
                (u.phoneNumber || '').includes(s)
            );
        }

        document.getElementById('contentArea').innerHTML = `
            <div class="page-header"><h1>회원 관리</h1><p>전체 ${formatNumber(state.users.total)}명</p></div>
            <div class="card">
                <div class="form-row">
                    <input class="input" type="text" placeholder="이름 또는 전화번호 검색" id="userSearch" value="${search}" onkeyup="onUserSearch(event)">
                    <button class="btn btn-secondary" onclick="state.users.search=document.getElementById('userSearch').value;loadUsers();">검색</button>
                </div>
                <div class="table-wrapper">
                    <table>
                        <thead><tr><th>이름</th><th>전화번호</th><th>이메일</th><th>등급</th><th>상태</th><th>가입일</th></tr></thead>
                        <tbody>
                            ${filtered.map(u => `
                                <tr onclick="showUserDetail('${u.id}')">
                                    <td>${u.displayName || '-'}</td>
                                    <td>${u.phoneNumber || '-'}</td>
                                    <td>${u.email || '-'}</td>
                                    <td>${tierBadge(u.tier)}</td>
                                    <td>${statusBadge(u.status)}</td>
                                    <td>${formatDateShort(u.createdAt)}</td>
                                </tr>
                            `).join('') || '<tr class="no-hover"><td colspan="6" style="text-align:center;color:#888;">회원이 없습니다</td></tr>'}
                        </tbody>
                    </table>
                </div>
                <div class="pagination">
                    <button onclick="state.users.page--;loadUsers();" ${state.users.page <= 1 ? 'disabled' : ''}>&laquo; 이전</button>
                    <span>${state.users.page} / ${totalPages}</span>
                    <button onclick="state.users.page++;loadUsers();" ${state.users.page >= totalPages ? 'disabled' : ''}>다음 &raquo;</button>
                </div>
            </div>`;
    } catch (e) { showError(e.message); }
}

function onUserSearch(e) { if (e.key === 'Enter') { state.users.search = e.target.value; loadUsers(); } }

async function showUserDetail(userId) {
    try {
        const u = await api(`/users/${userId}`);
        const modal = document.getElementById('modalOverlay');
        document.getElementById('modalContent').innerHTML = `
            <div class="modal-header"><h2>회원 상세 정보</h2><button class="modal-close" onclick="closeModal()">&times;</button></div>
            <div class="modal-body">
                <div class="detail-row"><div class="detail-label">ID</div><div class="detail-value">${u.id}</div></div>
                <div class="detail-row"><div class="detail-label">이름</div><div class="detail-value">${u.displayName || '-'}</div></div>
                <div class="detail-row"><div class="detail-label">전화번호</div><div class="detail-value">${u.phoneNumber || '-'}</div></div>
                <div class="detail-row"><div class="detail-label">이메일</div><div class="detail-value">${u.email || '-'}</div></div>
                <div class="detail-row"><div class="detail-label">등급</div><div class="detail-value">${tierBadge(u.tier)}</div></div>
                <div class="detail-row"><div class="detail-label">상태</div><div class="detail-value">${statusBadge(u.status)}</div></div>
                <div class="detail-row"><div class="detail-label">크레딧 잔액</div><div class="detail-value">${formatCurrency(u.creditBalance)}</div></div>
                <div class="detail-row"><div class="detail-label">오늘 사용량</div><div class="detail-value">${formatNumber(u.todayUsage)}건</div></div>
                <div class="detail-row"><div class="detail-label">가입일</div><div class="detail-value">${formatDate(u.createdAt)}</div></div>
            </div>
            <div class="modal-actions">
                <select class="input" id="modalTier">
                    <option value="free" ${u.tier === 'free' ? 'selected' : ''}>무료</option>
                    <option value="paid" ${u.tier === 'paid' ? 'selected' : ''}>유료</option>
                    <option value="premium" ${u.tier === 'premium' ? 'selected' : ''}>프리미엄</option>
                </select>
                <button class="btn btn-primary btn-sm" onclick="updateTier('${u.id}')">등급 변경</button>
                ${u.status === 'active'
                    ? `<button class="btn btn-danger btn-sm" onclick="updateStatus('${u.id}','suspended')">계정 정지</button>`
                    : `<button class="btn btn-primary btn-sm" onclick="updateStatus('${u.id}','active')">계정 활성화</button>`}
            </div>`;
        modal.classList.add('show');
    } catch (e) { showToast(e.message, 'error'); }
}

async function updateStatus(userId, status) {
    try {
        await api(`/users/${userId}/status`, { method: 'PUT', body: JSON.stringify({ status }) });
        showToast('상태가 변경되었습니다');
        closeModal();
        loadUsers();
    } catch (e) { showToast(e.message, 'error'); }
}

async function updateTier(userId) {
    const tier = document.getElementById('modalTier').value;
    try {
        await api(`/users/${userId}/tier`, { method: 'PUT', body: JSON.stringify({ tier }) });
        showToast('등급이 변경되었습니다');
        closeModal();
        loadUsers();
    } catch (e) { showToast(e.message, 'error'); }
}

function closeModal() { document.getElementById('modalOverlay').classList.remove('show'); }

// ===== Payments =====
async function loadPayments() {
    try {
        const data = await api(`/payments?page=${state.payments.page}&limit=${PAGE_SIZE}`);
        state.payments.data = data.data || [];
        state.payments.total = data.total || 0;
        const totalPages = Math.ceil(state.payments.total / PAGE_SIZE) || 1;

        document.getElementById('contentArea').innerHTML = `
            <div class="page-header"><h1>결제 관리</h1><p>전체 ${formatNumber(state.payments.total)}건</p></div>
            <div class="card">
                <div class="table-wrapper">
                    <table>
                        <thead><tr><th>결제 ID</th><th>금액</th><th>유형</th><th>상태</th><th>일시</th></tr></thead>
                        <tbody>
                            ${state.payments.data.map(p => `
                                <tr class="no-hover">
                                    <td>${p.id ? p.id.substring(0,8) + '...' : '-'}</td>
                                    <td>${formatCurrency(p.amount)}</td>
                                    <td>${p.type || '-'}</td>
                                    <td>${paymentStatusBadge(p.status)}</td>
                                    <td>${formatDate(p.createdAt)}</td>
                                </tr>
                            `).join('') || '<tr class="no-hover"><td colspan="5" style="text-align:center;color:#888;">결제 내역이 없습니다</td></tr>'}
                        </tbody>
                    </table>
                </div>
                <div class="pagination">
                    <button onclick="state.payments.page--;loadPayments();" ${state.payments.page <= 1 ? 'disabled' : ''}>&laquo; 이전</button>
                    <span>${state.payments.page} / ${totalPages}</span>
                    <button onclick="state.payments.page++;loadPayments();" ${state.payments.page >= totalPages ? 'disabled' : ''}>다음 &raquo;</button>
                </div>
            </div>`;
    } catch (e) { showError(e.message); }
}

// ===== Usage Stats =====
async function loadUsage() {
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('contentArea').innerHTML = `
        <div class="page-header"><h1>사용량 통계</h1><p>일별 메시지 발송 현황</p></div>
        <div class="card">
            <div class="form-row">
                <label>조회 시작일</label>
                <input class="input" type="date" id="usageStart" value="${getDateOffset(-6)}">
                <label>종료일</label>
                <input class="input" type="date" id="usageEnd" value="${today}">
                <button class="btn btn-primary" onclick="fetchUsageRange()">조회</button>
            </div>
            <div id="usageChart"><div class="loading">날짜를 선택하고 조회 버튼을 클릭하세요</div></div>
            <div id="usageSummary"></div>
        </div>`;
    fetchUsageRange();
}

function getDateOffset(days) {
    const d = new Date();
    d.setDate(d.getDate() + days);
    return d.toISOString().split('T')[0];
}

async function fetchUsageRange() {
    const start = document.getElementById('usageStart').value;
    const end = document.getElementById('usageEnd').value;
    const chartEl = document.getElementById('usageChart');
    const summaryEl = document.getElementById('usageSummary');

    const dates = [];
    const d = new Date(start);
    const endDate = new Date(end);
    while (d <= endDate) { dates.push(d.toISOString().split('T')[0]); d.setDate(d.getDate() + 1); }

    try {
        const results = await Promise.all(dates.map(date => api(`/usage?date=${date}`).catch(() => ({ date, totalSms: 0, totalLms: 0, totalMms: 0 }))));

        const maxTotal = Math.max(1, ...results.map(r => (r.totalSms || 0) + (r.totalLms || 0) + (r.totalMms || 0)));

        let totalSms = 0, totalLms = 0, totalMms = 0, totalCost = 0;
        results.forEach(r => { totalSms += r.totalSms || 0; totalLms += r.totalLms || 0; totalMms += r.totalMms || 0; totalCost += r.totalCost || 0; });

        chartEl.innerHTML = `
            <div class="chart-legend">
                <div class="chart-legend-item"><div class="chart-legend-dot" style="background:#0381FE;"></div>SMS</div>
                <div class="chart-legend-item"><div class="chart-legend-dot" style="background:#27ae60;"></div>LMS</div>
                <div class="chart-legend-item"><div class="chart-legend-dot" style="background:#f39c12;"></div>MMS</div>
            </div>
            <div class="chart-container">
                ${results.map(r => {
                    const sms = r.totalSms || 0, lms = r.totalLms || 0, mms = r.totalMms || 0;
                    const total = sms + lms + mms;
                    const scale = 300;
                    return `<div class="chart-bar-group">
                        <div class="chart-label">${(r.date || '').substring(5)}</div>
                        <div class="chart-bars" style="max-width:${scale}px;">
                            ${sms ? `<div class="chart-bar sms" style="width:${Math.max(2, sms/maxTotal*scale)}px;" title="SMS: ${sms}"></div>` : ''}
                            ${lms ? `<div class="chart-bar lms" style="width:${Math.max(2, lms/maxTotal*scale)}px;" title="LMS: ${lms}"></div>` : ''}
                            ${mms ? `<div class="chart-bar mms" style="width:${Math.max(2, mms/maxTotal*scale)}px;" title="MMS: ${mms}"></div>` : ''}
                        </div>
                        <div class="chart-value">${formatNumber(total)}건</div>
                    </div>`;
                }).join('')}
            </div>`;

        summaryEl.innerHTML = `
            <div class="stats-grid" style="margin-top:24px;">
                <div class="stat-card blue"><div class="stat-value">${formatNumber(totalSms)}</div><div class="stat-label">SMS 발송</div></div>
                <div class="stat-card green"><div class="stat-value">${formatNumber(totalLms)}</div><div class="stat-label">LMS 발송</div></div>
                <div class="stat-card orange"><div class="stat-value">${formatNumber(totalMms)}</div><div class="stat-label">MMS 발송</div></div>
                <div class="stat-card purple"><div class="stat-value">${formatCurrency(totalCost)}</div><div class="stat-label">총 비용</div></div>
            </div>`;
    } catch (e) { chartEl.innerHTML = `<div class="loading" style="color:#e74c3c;">데이터 로딩 실패: ${e.message}</div>`; }
}

// ===== Config =====
async function loadConfig() {
    try {
        const configs = await api('/config');
        document.getElementById('contentArea').innerHTML = `
            <div class="page-header"><h1>설정 관리</h1><p>앱 설정값 관리</p></div>
            <div class="card">
                <div class="table-wrapper">
                    <table>
                        <thead><tr><th>설정 키</th><th>값</th><th>설명</th><th>작업</th></tr></thead>
                        <tbody id="configBody">
                            ${(configs || []).map((c, i) => `
                                <tr class="no-hover">
                                    <td><strong>${c.key}</strong></td>
                                    <td><input class="config-input" id="cfgVal_${i}" value="${escapeHtml(c.value || '')}"></td>
                                    <td style="color:#888;font-size:12px;">${c.description || '-'}</td>
                                    <td><button class="btn btn-primary btn-sm" onclick="saveConfig('${escapeHtml(c.key)}', document.getElementById('cfgVal_${i}').value)">저장</button></td>
                                </tr>
                            `).join('') || '<tr class="no-hover"><td colspan="4" style="text-align:center;color:#888;">설정 항목이 없습니다</td></tr>'}
                        </tbody>
                    </table>
                </div>
                <div style="margin-top:20px;">
                    <div class="card-title">새 설정 추가</div>
                    <div class="form-row">
                        <input class="input" id="newCfgKey" placeholder="키">
                        <input class="input" id="newCfgValue" placeholder="값">
                        <input class="input" id="newCfgDesc" placeholder="설명">
                        <button class="btn btn-primary" onclick="addConfig()">추가</button>
                    </div>
                </div>
            </div>`;
    } catch (e) { showError(e.message); }
}

async function saveConfig(key, value) {
    try {
        await api('/config', { method: 'PUT', body: JSON.stringify([{ key, value }]) });
        showToast('설정이 저장되었습니다');
    } catch (e) { showToast(e.message, 'error'); }
}

async function addConfig() {
    const key = document.getElementById('newCfgKey').value.trim();
    const value = document.getElementById('newCfgValue').value;
    const description = document.getElementById('newCfgDesc').value;
    if (!key) { showToast('키를 입력하세요', 'error'); return; }
    try {
        await api('/config', { method: 'PUT', body: JSON.stringify([{ key, value, description }]) });
        showToast('설정이 추가되었습니다');
        loadConfig();
    } catch (e) { showToast(e.message, 'error'); }
}

// ===== AI Settings =====
async function loadAI() {
    try {
        const configs = await api('/config');
        const aiKey = (configs || []).find(c => c.key === 'deepseek_api_key');
        const today = new Date().toISOString().split('T')[0];
        const usage = await api(`/usage?date=${today}`).catch(() => ({}));

        document.getElementById('contentArea').innerHTML = `
            <div class="page-header"><h1>AI 설정</h1><p>DeepSeek AI 연동 설정 및 통계</p></div>
            <div class="stats-grid">
                <div class="stat-card blue">
                    <div class="stat-icon">🤖</div>
                    <div class="stat-value">${formatNumber(usage.totalAiTokens || 0)}</div>
                    <div class="stat-label">오늘 AI 토큰 사용량</div>
                </div>
                <div class="stat-card green">
                    <div class="stat-icon">📊</div>
                    <div class="stat-value">${formatNumber(usage.userCount || 0)}</div>
                    <div class="stat-label">오늘 활성 사용자</div>
                </div>
            </div>
            <div class="card">
                <div class="card-title">DeepSeek API 키</div>
                <div class="form-row">
                    <input class="input" type="password" id="aiApiKey" value="${aiKey ? aiKey.value : ''}" style="flex:1;">
                    <button class="btn btn-secondary btn-sm" onclick="toggleApiKey()">보기/숨기기</button>
                    <button class="btn btn-primary btn-sm" onclick="saveAiKey()">저장</button>
                </div>
            </div>`;
    } catch (e) { showError(e.message); }
}

function toggleApiKey() {
    const el = document.getElementById('aiApiKey');
    el.type = el.type === 'password' ? 'text' : 'password';
}

async function saveAiKey() {
    const value = document.getElementById('aiApiKey').value;
    try {
        await api('/config', { method: 'PUT', body: JSON.stringify([{ key: 'deepseek_api_key', value, description: 'DeepSeek API Key' }]) });
        showToast('API 키가 저장되었습니다');
    } catch (e) { showToast(e.message, 'error'); }
}

// ===== Helpers =====
function escapeHtml(str) { return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
function showError(msg) { document.getElementById('contentArea').innerHTML = `<div class="loading" style="color:#e74c3c;">오류: ${msg}</div>`; }

// ===== Init =====
document.addEventListener('DOMContentLoaded', () => {
    if (state.token) { showApp(); } else {
        document.getElementById('loginPage').style.display = 'flex';
        document.getElementById('appPage').style.display = 'none';
    }
    // Close modal on overlay click
    document.getElementById('modalOverlay').addEventListener('click', (e) => {
        if (e.target === e.currentTarget) closeModal();
    });
});
