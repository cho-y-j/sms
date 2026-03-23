// ===== State =====
const state = {
    token: localStorage.getItem('admin_token'),
    currentPage: 'dashboard',
    users: { page: 1, data: [], total: 0 },
    payments: { page: 1, data: [], total: 0 },
};

const API = '/api/admin';
const PAGE_SIZE = 20;

// ===== 관리자 자동 로그아웃 (10분 + 1분 경고) =====
let adminInactivityTimer = null;
let adminWarningCountdown = null;
let adminWarningSeconds = 60;

function resetAdminTimer() {
    if (adminInactivityTimer) clearTimeout(adminInactivityTimer);
    hideAdminWarning();
    if (!state.token) return;
    adminInactivityTimer = setTimeout(() => { showAdminWarning(); }, 9 * 60 * 1000);
}

function showAdminWarning() {
    adminWarningSeconds = 60;
    let modal = document.getElementById('adminLogoutWarning');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'adminLogoutWarning';
        modal.innerHTML = `<div style="position:fixed;inset:0;background:rgba(0,0,0,0.7);z-index:9999;display:flex;align-items:center;justify-content:center;">
            <div style="background:#fff;border-radius:16px;padding:32px;text-align:center;max-width:360px;box-shadow:0 20px 60px rgba(0,0,0,0.3);">
                <div style="font-size:40px;margin-bottom:12px;">🔒</div>
                <div style="font-size:16px;font-weight:700;margin-bottom:8px;">관리자 자동 로그아웃</div>
                <div style="color:#666;font-size:13px;margin-bottom:16px;">보안을 위해 <span id="adminCountdown" style="color:#e74c3c;font-weight:700;">60</span>초 후 자동 로그아웃됩니다.</div>
                <button onclick="extendAdminSession()" style="background:#0381FE;color:#fff;border:none;padding:12px 32px;border-radius:10px;font-size:14px;font-weight:600;cursor:pointer;width:100%;">계속 사용하기</button>
            </div>
        </div>`;
        document.body.appendChild(modal);
    }
    modal.style.display = 'block';
    adminWarningCountdown = setInterval(() => {
        adminWarningSeconds--;
        const el = document.getElementById('adminCountdown');
        if (el) el.textContent = adminWarningSeconds;
        if (adminWarningSeconds <= 0) {
            clearInterval(adminWarningCountdown);
            hideAdminWarning();
            logout();
            alert('보안을 위해 관리자 세션이 자동 로그아웃되었습니다.');
        }
    }, 1000);
}

function hideAdminWarning() {
    const modal = document.getElementById('adminLogoutWarning');
    if (modal) modal.style.display = 'none';
    if (adminWarningCountdown) { clearInterval(adminWarningCountdown); adminWarningCountdown = null; }
}

function extendAdminSession() {
    resetAdminTimer();
    showToast('세션이 연장되었습니다');
}

['click','keypress','mousemove','scroll','touchstart'].forEach(e =>
    document.addEventListener(e, () => { if (state.token && !document.getElementById('adminLogoutWarning')?.style.display?.includes('block')) resetAdminTimer(); }));

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
            resetAdminTimer();
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
    if (adminInactivityTimer) clearTimeout(adminInactivityTimer);
    hideAdminWarning();
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
        case 'templates': loadAdminTemplates(); break;
        case 'sms-monitor': loadSmsMonitor(); break;
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
            <div class="modal-actions" style="flex-direction:column;gap:12px;">
                <div style="font-weight:600;font-size:13px;color:#888;">등급 변경</div>
                <div class="form-row">
                    <select class="input" id="modalTier">
                        <option value="free" ${u.tier === 'free' ? 'selected' : ''}>무료</option>
                        <option value="paid" ${u.tier === 'paid' ? 'selected' : ''}>유료 (Business)</option>
                        <option value="premium" ${u.tier === 'premium' ? 'selected' : ''}>프리미엄 (Premium)</option>
                    </select>
                    <button class="btn btn-primary btn-sm" onclick="updateTier('${u.id}')">변경</button>
                </div>

                <div style="font-weight:600;font-size:13px;color:#888;margin-top:8px;">비밀번호 변경</div>
                <div class="form-row">
                    <input class="input" type="text" id="modalNewPassword" placeholder="새 비밀번호 (4자 이상)">
                    <button class="btn btn-primary btn-sm" onclick="changePassword('${u.id}')">변경</button>
                </div>

                <div style="font-weight:600;font-size:13px;color:#888;margin-top:8px;">크레딧 충전/차감</div>
                <div class="form-row">
                    <input class="input" type="number" id="modalCreditAmount" placeholder="금액 (음수=차감)">
                    <input class="input" type="text" id="modalCreditReason" placeholder="사유" value="관리자 수동 충전">
                    <button class="btn btn-primary btn-sm" onclick="adjustCredit('${u.id}')">적용</button>
                </div>

                <div style="display:flex;gap:8px;margin-top:12px;">
                    ${u.status === 'active'
                        ? `<button class="btn btn-danger btn-sm" onclick="updateStatus('${u.id}','suspended')">계정 정지</button>`
                        : `<button class="btn btn-primary btn-sm" onclick="updateStatus('${u.id}','active')">계정 활성화</button>`}
                    <button class="btn btn-danger btn-sm" onclick="deleteUser('${u.id}','${u.displayName||''}')">회원 삭제</button>
                </div>
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

async function changePassword(userId) {
    const password = document.getElementById('modalNewPassword').value;
    if (!password || password.length < 4) { showToast('비밀번호는 4자 이상이어야 합니다', 'error'); return; }
    try {
        await api(`/users/${userId}/password`, { method: 'PUT', body: JSON.stringify({ password }) });
        showToast('비밀번호가 변경되었습니다');
        document.getElementById('modalNewPassword').value = '';
    } catch (e) { showToast(e.message, 'error'); }
}

async function adjustCredit(userId) {
    const amount = parseFloat(document.getElementById('modalCreditAmount').value);
    const reason = document.getElementById('modalCreditReason').value || '관리자 수동 조정';
    if (isNaN(amount) || amount === 0) { showToast('금액을 입력하세요', 'error'); return; }
    try {
        await api(`/users/${userId}/credit`, { method: 'POST', body: JSON.stringify({ amount, reason }) });
        showToast(`크레딧 ${amount > 0 ? '+' : ''}${formatNumber(amount)}원 ${amount > 0 ? '충전' : '차감'} 완료`);
        showUserDetail(userId); // 새로고침
    } catch (e) { showToast(e.message, 'error'); }
}

async function deleteUser(userId, name) {
    if (!confirm(`정말 "${name}" 회원을 삭제하시겠습니까?\n\n모든 데이터(연락처, 발송이력, 크레딧)가 삭제됩니다.\n이 작업은 되돌릴 수 없습니다.`)) return;
    try {
        await api(`/users/${userId}`, { method: 'DELETE' });
        showToast('회원이 삭제되었습니다');
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

// ===== Admin Templates =====
let adminCategories = [];
let adminTemplates = [];

async function loadAdminTemplates() {
    try {
        const catData = await api('/template-categories');
        adminCategories = catData.data || [];
        const tplData = await api('/templates');
        adminTemplates = tplData.data || [];

        document.getElementById('contentArea').innerHTML = `
            <div class="page-header"><h1>템플릿 관리</h1><p>사용자에게 제공할 기본 템플릿을 관리합니다</p></div>

            <!-- 카테고리 관리 -->
            <div class="card">
                <div class="card-title">카테고리</div>
                <div class="form-row" style="margin-bottom:12px;">
                    <input class="input" id="newCatName" placeholder="카테고리 이름">
                    <input class="input" id="newCatIcon" placeholder="아이콘 (이모지)" value="📋" style="width:80px;">
                    <button class="btn btn-primary" onclick="addTemplateCategory()">추가</button>
                </div>
                <div id="categoryList">
                    ${adminCategories.length === 0 ? '<div style="color:#888;text-align:center;padding:20px;">카테고리가 없습니다</div>' :
                    adminCategories.map(c => `
                        <div style="display:flex;justify-content:space-between;align-items:center;padding:8px 0;border-bottom:1px solid #eee;">
                            <span>${c.icon || '📋'} <strong>${escapeHtml(c.name)}</strong> <span style="color:#888;font-size:11px;">(${adminTemplates.filter(t=>t.categoryId===c.id).length}개 템플릿)</span></span>
                            <button class="btn btn-danger btn-sm" onclick="deleteTemplateCategory('${c.id}')">삭제</button>
                        </div>
                    `).join('')}
                </div>
            </div>

            <!-- 템플릿 관리 -->
            <div class="card">
                <div class="card-title">템플릿</div>
                <div class="form-row" style="margin-bottom:12px;">
                    <select class="input" id="newTplCategory">
                        <option value="">카테고리 선택</option>
                        ${adminCategories.map(c => `<option value="${c.id}">${c.icon||'📋'} ${escapeHtml(c.name)}</option>`).join('')}
                    </select>
                    <input class="input" id="newTplTitle" placeholder="템플릿 제목">
                </div>
                <textarea class="input" id="newTplContent" placeholder="템플릿 내용 (%이름%, %회사% 등 변수 사용 가능)" style="min-height:80px;margin-bottom:12px;"></textarea>
                <div style="display:flex;gap:8px;">
                    <button class="btn btn-secondary btn-sm" onclick="uploadAdminTemplateImage()">📎 이미지 첨부</button>
                    <button class="btn btn-primary" onclick="addAdminTemplate()">템플릿 추가</button>
                </div>
            </div>

            <!-- 카테고리별 템플릿 목록 -->
            ${adminCategories.map(cat => {
                const catTemplates = adminTemplates.filter(t => t.categoryId === cat.id);
                return `
                <div class="card">
                    <div class="card-title">${cat.icon||'📋'} ${escapeHtml(cat.name)}</div>
                    ${catTemplates.length === 0 ? '<div style="color:#888;text-align:center;padding:12px;">템플릿이 없습니다</div>' :
                    `<div class="table-wrapper"><table>
                        <thead><tr><th>제목</th><th>내용 (미리보기)</th><th>작업</th></tr></thead>
                        <tbody>
                            ${catTemplates.map(t => `
                                <tr class="no-hover">
                                    <td><strong>${escapeHtml(t.title)}</strong></td>
                                    <td style="color:#666;max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(typeof t.content === 'string' ? t.content.substring(0,80) : '')}</td>
                                    <td style="white-space:nowrap;">
                                        <button class="btn btn-primary btn-sm" onclick="editAdminTemplate('${t.id}')">수정</button>
                                        <button class="btn btn-danger btn-sm" onclick="deleteAdminTemplate('${t.id}')">삭제</button>
                                    </td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table></div>`}
                </div>`;
            }).join('')}

            <!-- 편집 모달 -->
            <div class="modal-overlay" id="tplEditOverlay" style="display:none;position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.5);z-index:1000;justify-content:center;align-items:center;">
                <div style="background:#fff;border-radius:12px;padding:24px;width:500px;max-width:90vw;">
                    <h3 style="margin:0 0 16px;">템플릿 수정</h3>
                    <select class="input" id="editTplCategory">
                        ${adminCategories.map(c => `<option value="${c.id}">${c.icon||'📋'} ${escapeHtml(c.name)}</option>`).join('')}
                    </select>
                    <input class="input" id="editTplTitle" placeholder="제목">
                    <textarea class="input" id="editTplContent" placeholder="내용" style="min-height:120px;"></textarea>
                    <button class="btn btn-secondary btn-sm" style="margin-bottom:8px;" onclick="uploadAdminTemplateImageToEdit()">📎 이미지 첨부</button>
                    <input type="hidden" id="editTplId">
                    <div style="display:flex;gap:8px;margin-top:12px;">
                        <button class="btn btn-primary" style="flex:1" onclick="saveEditTemplate()">저장</button>
                        <button class="btn btn-secondary" onclick="document.getElementById('tplEditOverlay').style.display='none'">취소</button>
                    </div>
                </div>
            </div>`;
    } catch (e) { showError(e.message); }
}

async function addTemplateCategory() {
    const name = document.getElementById('newCatName').value.trim();
    const icon = document.getElementById('newCatIcon').value.trim() || '📋';
    if (!name) { showToast('카테고리 이름을 입력하세요', 'error'); return; }
    try {
        await api('/template-categories', { method: 'POST', body: JSON.stringify({ name, icon }) });
        showToast('카테고리가 추가되었습니다');
        loadAdminTemplates();
    } catch (e) { showToast(e.message, 'error'); }
}

async function deleteTemplateCategory(id) {
    if (!confirm('이 카테고리와 포함된 템플릿이 모두 삭제됩니다. 계속하시겠습니까?')) return;
    try {
        await api(`/template-categories/${id}`, { method: 'DELETE' });
        showToast('카테고리가 삭제되었습니다');
        loadAdminTemplates();
    } catch (e) { showToast(e.message, 'error'); }
}

async function addAdminTemplate() {
    const categoryId = document.getElementById('newTplCategory').value;
    const title = document.getElementById('newTplTitle').value.trim();
    const content = document.getElementById('newTplContent').value.trim();
    if (!categoryId || !title || !content) { showToast('카테고리, 제목, 내용을 모두 입력하세요', 'error'); return; }
    try {
        await api('/templates', { method: 'POST', body: JSON.stringify({ categoryId, title, content }) });
        showToast('템플릿이 추가되었습니다');
        loadAdminTemplates();
    } catch (e) { showToast(e.message, 'error'); }
}

function editAdminTemplate(id) {
    const tpl = adminTemplates.find(t => t.id === id);
    if (!tpl) return;
    document.getElementById('editTplId').value = tpl.id;
    document.getElementById('editTplCategory').value = tpl.categoryId;
    document.getElementById('editTplTitle').value = tpl.title;
    document.getElementById('editTplContent').value = tpl.content;
    document.getElementById('tplEditOverlay').style.display = 'flex';
}

async function saveEditTemplate() {
    const id = document.getElementById('editTplId').value;
    const categoryId = document.getElementById('editTplCategory').value;
    const title = document.getElementById('editTplTitle').value.trim();
    const content = document.getElementById('editTplContent').value.trim();
    if (!title || !content) { showToast('제목과 내용을 입력하세요', 'error'); return; }
    try {
        await api(`/templates/${id}`, { method: 'PUT', body: JSON.stringify({ categoryId, title, content }) });
        showToast('템플릿이 수정되었습니다');
        document.getElementById('tplEditOverlay').style.display = 'none';
        loadAdminTemplates();
    } catch (e) { showToast(e.message, 'error'); }
}

async function deleteAdminTemplate(id) {
    if (!confirm('이 템플릿을 삭제하시겠습니까?')) return;
    try {
        await api(`/templates/${id}`, { method: 'DELETE' });
        showToast('템플릿이 삭제되었습니다');
        loadAdminTemplates();
    } catch (e) { showToast(e.message, 'error'); }
}

// ===== SMS Monitor =====
let smsMonitorState = { page: 1, statusFilter: 'all' };

async function loadSmsMonitor() {
    try {
        const [statsData, errorsData] = await Promise.all([
            api('/sms-stats'),
            api('/sms-errors')
        ]);
        const stats = statsData.data || [];
        const errors = errorsData.data || [];

        // Today's stats
        const today = stats.length > 0 ? stats[stats.length - 1] : { sent: '0', failed: '0', successRate: '0' };
        const todaySent = parseInt(today.sent || '0');
        const todayFailed = parseInt(today.failed || '0');
        const todayRate = today.successRate || '0';

        document.getElementById('contentArea').innerHTML = `
            <div class="page-header"><h1>발송 모니터</h1><p>SMS 발송 현황 및 에러 추적</p></div>

            <!-- 발송 현황 요약 -->
            <div class="stats-grid">
                <div class="stat-card green"><div class="stat-icon">✅</div><div class="stat-value">${formatNumber(todaySent)}</div><div class="stat-label">오늘 성공</div></div>
                <div class="stat-card" style="background:linear-gradient(135deg,#fee2e2,#fecaca);"><div class="stat-icon">❌</div><div class="stat-value" style="color:#dc2626;">${formatNumber(todayFailed)}</div><div class="stat-label">오늘 실패</div></div>
                <div class="stat-card blue"><div class="stat-icon">📊</div><div class="stat-value">${todayRate}%</div><div class="stat-label">성공률</div></div>
                <div class="stat-card orange"><div class="stat-icon">📡</div><div class="stat-value">${formatNumber(todaySent + todayFailed)}</div><div class="stat-label">오늘 전체</div></div>
            </div>

            <!-- 7일간 추이 -->
            <div class="card">
                <div class="card-title">최근 7일 발송 추이</div>
                <div class="table-wrapper">
                    <table>
                        <thead><tr><th>날짜</th><th>성공</th><th>실패</th><th>전체</th><th>성공률</th></tr></thead>
                        <tbody>
                            ${[...stats].reverse().filter(s => parseInt(s.total||'0') > 0).map(s => `
                                <tr class="no-hover">
                                    <td>${s.date || '-'}</td>
                                    <td style="color:#16a34a;font-weight:600;">${formatNumber(parseInt(s.sent||'0'))}</td>
                                    <td style="color:#dc2626;font-weight:600;">${formatNumber(parseInt(s.failed||'0'))}</td>
                                    <td>${formatNumber(parseInt(s.total||'0'))}</td>
                                    <td><span class="badge badge-${parseInt(s.successRate||'0')>=90?'active':'suspended'}">${s.successRate||'0'}%</span></td>
                                </tr>
                            `).join('') || '<tr class="no-hover"><td colspan="5" style="text-align:center;color:#888;">아직 발송 이력이 없습니다</td></tr>'}
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- 최근 에러 목록 -->
            <div class="card">
                <div class="card-title">에러코드별 현황</div>
                <div class="table-wrapper">
                    <table>
                        <thead><tr><th>에러코드</th><th>에러 메시지</th><th>발생 건수</th><th>마지막 발생</th></tr></thead>
                        <tbody>
                            ${errors.length === 0 ? '<tr class="no-hover"><td colspan="4" style="text-align:center;color:#888;">에러 없음 👍</td></tr>' :
                            errors.map(e => `
                                <tr class="no-hover">
                                    <td><span style="background:#fee2e2;color:#dc2626;padding:2px 8px;border-radius:4px;font-weight:600;font-size:12px;">${escapeHtml(e.errorCode||'UNKNOWN')}</span></td>
                                    <td style="color:#666;max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(e.errorMessage||'-')}</td>
                                    <td style="font-weight:600;color:#dc2626;">${formatNumber(parseInt(e.count||'0'))}</td>
                                    <td style="font-size:12px;color:#888;">${e.lastOccurred ? formatDate(parseInt(e.lastOccurred)) : '-'}</td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- 발송 이력 -->
            <div class="card">
                <div class="card-title">발송 이력</div>
                <div class="form-row" style="margin-bottom:12px;">
                    <select class="input" id="smsLogStatusFilter" onchange="smsMonitorState.statusFilter=this.value;smsMonitorState.page=1;loadSmsLogs();">
                        <option value="all" ${smsMonitorState.statusFilter==='all'?'selected':''}>전체</option>
                        <option value="sent" ${smsMonitorState.statusFilter==='sent'?'selected':''}>성공</option>
                        <option value="failed" ${smsMonitorState.statusFilter==='failed'?'selected':''}>실패</option>
                    </select>
                    <button class="btn btn-primary" onclick="smsMonitorState.page=1;loadSmsLogs();">조회</button>
                </div>
                <div id="smsLogsTable"><div class="loading">로딩 중...</div></div>
                <div class="pagination" id="smsLogsPagination"></div>
            </div>`;

        loadSmsLogs();
    } catch (e) { showError(e.message); }
}

async function loadSmsLogs() {
    try {
        const data = await api(`/sms-logs?page=${smsMonitorState.page}&limit=${PAGE_SIZE}&status=${smsMonitorState.statusFilter}`);
        const logs = data.data || [];
        const total = parseInt(data.total || '0');
        const totalPages = Math.ceil(total / PAGE_SIZE) || 1;

        document.getElementById('smsLogsTable').innerHTML = `
            <div class="table-wrapper">
                <table>
                    <thead><tr><th>수신번호</th><th>메시지</th><th>상태</th><th>에러코드</th><th>에러사유</th><th>발송시간</th></tr></thead>
                    <tbody>
                        ${logs.length === 0 ? '<tr class="no-hover"><td colspan="6" style="text-align:center;color:#888;">이력이 없습니다</td></tr>' :
                        logs.map(l => `
                            <tr class="no-hover" style="${l.status==='failed'?'background:#fff5f5;':''}">
                                <td>${escapeHtml(l.recipientPhone||'-')}</td>
                                <td style="color:#666;max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(l.messagePreview||'-')}</td>
                                <td><span class="badge badge-${l.status==='sent'?'active':'suspended'}">${l.status==='sent'?'성공':'실패'}</span></td>
                                <td style="font-size:11px;${l.status==='failed'?'color:#dc2626;font-weight:600;':''}">${escapeHtml(l.errorCode||'-')}</td>
                                <td style="font-size:11px;color:#888;max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(l.errorMessage||'-')}</td>
                                <td style="font-size:11px;color:#888;">${l.sentAt ? formatDate(parseInt(l.sentAt)) : '-'}</td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>`;

        document.getElementById('smsLogsPagination').innerHTML = `
            <button onclick="smsMonitorState.page--;loadSmsLogs();" ${smsMonitorState.page <= 1 ? 'disabled' : ''}>&laquo; 이전</button>
            <span>${smsMonitorState.page} / ${totalPages} (${formatNumber(total)}건)</span>
            <button onclick="smsMonitorState.page++;loadSmsLogs();" ${smsMonitorState.page >= totalPages ? 'disabled' : ''}>다음 &raquo;</button>`;
    } catch (e) {
        document.getElementById('smsLogsTable').innerHTML = `<div class="loading" style="color:#e74c3c;">로딩 실패: ${e.message}</div>`;
    }
}

// ===== AI Settings =====
async function loadAI() {
    try {
        const configs = await api('/config');
        const aiKey = (configs || []).find(c => c.key === 'deepseek_api_key');
        const today = new Date().toISOString().split('T')[0];
        const usage = await api(`/usage?date=${today}`).catch(() => ({}));

        document.getElementById('contentArea').innerHTML = `
            <div class="page-header"><h1>AI 설정</h1><p>AI API 설정 및 통계</p></div>
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
                <div class="card-title">AI API 키</div>
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
        await api('/config', { method: 'PUT', body: JSON.stringify([{ key: 'deepseek_api_key', value, description: 'AI API Key' }]) });
        showToast('API 키가 저장되었습니다');
    } catch (e) { showToast(e.message, 'error'); }
}

// ===== Image Upload (Admin) =====
function adminUploadImage(callback) {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    input.onchange = async (e) => {
        const file = e.target.files[0];
        if (!file) return;
        if (file.size > 5 * 1024 * 1024) { showToast('5MB 이하 이미지만 가능합니다', 'error'); return; }

        const reader = new FileReader();
        reader.onload = async () => {
            try {
                const headers = { 'Content-Type': 'application/json' };
                if (state.token) headers['Authorization'] = `Bearer ${state.token}`;
                const res = await fetch('/api/user/upload/image', {
                    method: 'POST',
                    headers,
                    body: JSON.stringify({ data: reader.result, fileName: file.name })
                });
                if (!res.ok) { const err = await res.json().catch(() => ({})); throw new Error(err.error || 'Upload failed'); }
                const data = await res.json();
                callback(data);
            } catch(e) { showToast('이미지 업로드 실패: ' + e.message, 'error'); }
        };
        reader.readAsDataURL(file);
    };
    input.click();
}

function uploadAdminTemplateImage() {
    adminUploadImage((res) => {
        const textarea = document.getElementById('newTplContent');
        const currentVal = textarea.value;
        textarea.value = currentVal + (currentVal ? '\n' : '') + res.previewUrl;
        showToast('이미지 업로드 완료');
    });
}

function uploadAdminTemplateImageToEdit() {
    adminUploadImage((res) => {
        const textarea = document.getElementById('editTplContent');
        const currentVal = textarea.value;
        textarea.value = currentVal + (currentVal ? '\n' : '') + res.previewUrl;
        showToast('이미지 업로드 완료');
    });
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
