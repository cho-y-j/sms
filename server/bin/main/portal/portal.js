const API = '/api';
let token = localStorage.getItem('user_token');
let refreshToken = localStorage.getItem('user_refresh_token');
let userInfo = null;
let allContacts = [];
let allCategories = [];
let allTemplates = [];
let selectedRecipients = [];
let currentSendType = 'phone';
let currentJobId = null;
let progressInterval = null;

// ===== 10분 비활동 자동 로그아웃 (9분 경고 + 1분 카운트다운) =====
let inactivityTimer = null;
let warningTimer = null;
let warningCountdown = null;
let warningSeconds = 60;

function resetInactivityTimer() {
    if (inactivityTimer) clearTimeout(inactivityTimer);
    if (warningTimer) clearTimeout(warningTimer);
    if (warningCountdown) clearInterval(warningCountdown);
    hideLogoutWarning();

    // 9분 후 경고 표시
    inactivityTimer = setTimeout(() => {
        if (token) showLogoutWarning();
    }, 9 * 60 * 1000);
}

function showLogoutWarning() {
    warningSeconds = 60;
    let modal = document.getElementById('logoutWarningModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'logoutWarningModal';
        document.body.appendChild(modal);
    }
    modal.innerHTML = `
        <div style="position:fixed;inset:0;background:rgba(0,0,0,0.7);z-index:9999;display:flex;align-items:center;justify-content:center;">
            <div style="background:var(--surface);border:1px solid var(--border);border-radius:16px;padding:32px;text-align:center;max-width:360px;">
                <div style="font-size:40px;margin-bottom:12px;">&#9200;</div>
                <div style="font-size:16px;font-weight:700;margin-bottom:8px;">자동 로그아웃 안내</div>
                <div style="color:var(--text3);font-size:13px;margin-bottom:16px;">보안을 위해 <span id="logoutCountdown" style="color:var(--red);font-weight:700;">60</span>초 후 자동 로그아웃됩니다.</div>
                <button onclick="extendSession()" style="background:var(--accent);color:#fff;border:none;padding:12px 32px;border-radius:10px;font-size:14px;font-weight:600;cursor:pointer;width:100%;">계속 사용하기</button>
            </div>
        </div>`;
    modal.style.display = 'block';

    // 카운트다운 시작
    warningCountdown = setInterval(() => {
        warningSeconds--;
        const el = document.getElementById('logoutCountdown');
        if (el) el.textContent = warningSeconds;
        if (warningSeconds <= 0) {
            clearInterval(warningCountdown);
            hideLogoutWarning();
            doLogout();
            alert('보안을 위해 자동 로그아웃되었습니다.');
        }
    }, 1000);
}

function hideLogoutWarning() {
    const modal = document.getElementById('logoutWarningModal');
    if (modal) modal.style.display = 'none';
    if (warningCountdown) { clearInterval(warningCountdown); warningCountdown = null; }
}

function extendSession() {
    resetInactivityTimer();
    showToast('세션이 연장되었습니다');
}

['click','keypress','mousemove','scroll','touchstart'].forEach(e =>
    document.addEventListener(e, resetInactivityTimer));

// ===== Auth =====
async function doLogin() {
    const phone = document.getElementById('loginPhone').value;
    const password = document.getElementById('loginPassword').value;
    const errEl = document.getElementById('loginError');
    errEl.textContent = '';
    try {
        const deviceToken = localStorage.getItem('device_token') || '';
        const res = await fetch(`${API}/auth/login`, {
            method:'POST', headers:{'Content-Type':'application/json'},
            body: JSON.stringify({phone, email:phone, password, platform:'web', deviceToken})
        });
        const data = await res.json();
        if (data.requiresDeviceVerification === 'true') {
            // Show device verification modal
            showDeviceVerificationModal(phone, password, data.phone);
            return;
        }
        if (data.token) {
            token = data.token;
            refreshToken = data.refreshToken || '';
            localStorage.setItem('user_token', token);
            localStorage.setItem('user_refresh_token', refreshToken);
            if (data.deviceToken) localStorage.setItem('device_token', data.deviceToken);
            resetInactivityTimer();
            showApp();
        } else {
            errEl.textContent = data.error || '로그인 실패';
        }
    } catch(e) { errEl.textContent = '서버 연결 실패'; }
}

// ===== Device Verification =====
function showDeviceVerificationModal(loginId, password, userPhone) {
    let modal = document.getElementById('deviceVerifyModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'deviceVerifyModal';
        modal.className = 'modal-overlay show';
        document.body.appendChild(modal);
    } else {
        modal.classList.add('show');
    }
    modal.innerHTML = `
        <div class="modal-box" style="width:380px;">
            <div class="card-title">새 기기 인증</div>
            <p style="font-size:13px;color:var(--text2);margin-bottom:16px;">
                새로운 기기에서 로그인합니다. 보안을 위해 SMS 인증이 필요합니다.
            </p>
            <p style="font-size:12px;color:var(--text3);margin-bottom:12px;">인증번호가 ${userPhone} 번호로 발송됩니다.</p>
            <button class="btn btn-primary w-full" id="deviceSendCodeBtn" onclick="sendDeviceVerifyCode('${userPhone}')">인증번호 발송</button>
            <div id="deviceCodeInput" style="display:none;margin-top:12px;">
                <input class="input" id="deviceVerifyCode" placeholder="인증번호 6자리" maxlength="6" style="letter-spacing:6px;font-size:18px;text-align:center;">
                <div id="deviceVerifyTimer" style="font-size:11px;color:var(--accent);text-align:center;margin:4px 0;"></div>
                <div id="deviceVerifyError" style="font-size:12px;color:var(--red);margin:4px 0;"></div>
                <button class="btn btn-primary w-full" onclick="confirmDeviceVerify('${loginId}','${userPhone}')">확인</button>
            </div>
            <button class="btn btn-secondary w-full" style="margin-top:8px;" onclick="closeModal('deviceVerifyModal')">취소</button>
        </div>`;
}

let deviceVerifyTimerInterval = null;
async function sendDeviceVerifyCode(phone) {
    const btn = document.getElementById('deviceSendCodeBtn');
    btn.disabled = true; btn.textContent = '발송 중...';
    try {
        const res = await fetch(`${API}/auth/send-verification`, {
            method:'POST', headers:{'Content-Type':'application/json'},
            body: JSON.stringify({phone, purpose:'login_device'})
        });
        const data = await res.json();
        if (res.ok) {
            document.getElementById('deviceCodeInput').style.display = 'block';
            btn.textContent = '재발송';
            btn.disabled = false;
            // 3min timer
            let seconds = 180;
            clearInterval(deviceVerifyTimerInterval);
            deviceVerifyTimerInterval = setInterval(() => {
                seconds--;
                const m = Math.floor(seconds/60), s = seconds%60;
                document.getElementById('deviceVerifyTimer').textContent = `${m}:${s<10?'0':''}${s}`;
                if (seconds <= 0) { clearInterval(deviceVerifyTimerInterval); document.getElementById('deviceVerifyTimer').textContent = '만료됨'; }
            }, 1000);
        } else {
            btn.disabled = false; btn.textContent = '인증번호 발송';
            document.getElementById('deviceVerifyError').textContent = data.error || '발송 실패';
        }
    } catch(e) {
        btn.disabled = false; btn.textContent = '인증번호 발송';
        document.getElementById('deviceVerifyError').textContent = '서버 연결 실패';
    }
}

async function confirmDeviceVerify(loginId, phone) {
    const code = document.getElementById('deviceVerifyCode').value.trim();
    if (code.length !== 6) { document.getElementById('deviceVerifyError').textContent = '6자리 입력해주세요'; return; }
    const ua = navigator.userAgent;
    const deviceName = getDeviceName(ua);
    try {
        const res = await fetch(`${API}/auth/verify-device`, {
            method:'POST', headers:{'Content-Type':'application/json'},
            body: JSON.stringify({phone, code, purpose:'login_device', deviceName, platform:'web', email:loginId})
        });
        const data = await res.json();
        if (res.ok && data.token) {
            clearInterval(deviceVerifyTimerInterval);
            token = data.token;
            refreshToken = data.refreshToken || '';
            localStorage.setItem('user_token', token);
            localStorage.setItem('user_refresh_token', refreshToken);
            if (data.deviceToken) localStorage.setItem('device_token', data.deviceToken);
            closeModal('deviceVerifyModal');
            resetInactivityTimer();
            showApp();
        } else {
            document.getElementById('deviceVerifyError').textContent = data.error || '인증 실패';
        }
    } catch(e) { document.getElementById('deviceVerifyError').textContent = '서버 연결 실패'; }
}

function getDeviceName(ua) {
    let browser = 'Unknown';
    if (ua.includes('Chrome') && !ua.includes('Edg')) browser = 'Chrome';
    else if (ua.includes('Firefox')) browser = 'Firefox';
    else if (ua.includes('Safari') && !ua.includes('Chrome')) browser = 'Safari';
    else if (ua.includes('Edg')) browser = 'Edge';
    let os = 'Unknown';
    if (ua.includes('Windows')) os = 'Windows';
    else if (ua.includes('Mac OS')) os = 'macOS';
    else if (ua.includes('Linux')) os = 'Linux';
    else if (ua.includes('Android')) os = 'Android';
    else if (ua.includes('iPhone') || ua.includes('iPad')) os = 'iOS';
    return `${browser} / ${os}`;
}

function doLogout() {
    token = null; refreshToken = null; userInfo = null;
    localStorage.removeItem('user_token');
    localStorage.removeItem('user_refresh_token');
    if (inactivityTimer) clearTimeout(inactivityTimer);
    if (warningTimer) clearTimeout(warningTimer);
    if (warningCountdown) clearInterval(warningCountdown);
    hideLogoutWarning();
    document.getElementById('loginPage').style.display = 'flex';
    document.getElementById('appPage').style.display = 'none';
}

async function apiFetch(path, opts={}) {
    const headers = {'Content-Type':'application/json'};
    if (token) headers['Authorization'] = `Bearer ${token}`;
    let res = await fetch(`${API}${path}`, {...opts, headers});
    // 토큰 만료 시 자동 갱신 시도
    if (res.status === 401 && refreshToken) {
        try {
            const refreshRes = await fetch(`${API}/auth/refresh`, {
                method:'POST', headers:{'Content-Type':'application/json'},
                body: JSON.stringify({refreshToken})
            });
            if (refreshRes.ok) {
                const rd = await refreshRes.json();
                token = rd.accessToken || rd.token;
                refreshToken = rd.refreshToken || refreshToken;
                localStorage.setItem('user_token', token);
                localStorage.setItem('user_refresh_token', refreshToken);
                headers['Authorization'] = `Bearer ${token}`;
                res = await fetch(`${API}${path}`, {...opts, headers});
            } else { doLogout(); throw new Error('세션이 만료되었습니다. 다시 로그인해주세요.'); }
        } catch(e) { doLogout(); throw new Error('세션이 만료되었습니다.'); }
    }
    if (res.status === 401) { doLogout(); throw new Error('로그인이 필요합니다'); }
    if (res.status === 403) { const err = await res.json().catch(()=>({})); throw new Error(err.error || '접근이 제한되었습니다'); }
    if (res.status === 402) { const err = await res.json().catch(()=>({})); throw new Error(err.error || '잔액이 부족합니다'); }
    if (!res.ok) { const err = await res.json().catch(()=>({})); throw new Error(err.error || `오류 ${res.status}`); }
    return res.json();
}

// ===== Init =====
function showApp() {
    document.getElementById('loginPage').style.display = 'none';
    document.getElementById('appPage').style.display = 'block';
    loadUserInfo();
    loadContactsCache();
    loadCategoriesCache();
    loadTemplatesCache();
    navigate('sms');
}

async function loadUserInfo() { try { userInfo = await apiFetch('/user/me'); } catch(e) {} }
async function loadContactsCache() { try { const d = await apiFetch('/user/contacts?limit=5000'); allContacts = d.data||[]; } catch(e) {} }
async function loadCategoriesCache() { try { const d = await apiFetch('/user/categories'); allCategories = d.data||[]; } catch(e) {} }
async function loadTemplatesCache() { try { const d = await apiFetch('/user/templates'); allTemplates = d.data||[]; } catch(e) {} }

// ===== Navigation =====
function navigate(page) {
    document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t.dataset.page === page));
    const c = document.getElementById('content');
    if (progressInterval) { clearInterval(progressInterval); progressInterval = null; }
    switch(page) {
        case 'sms': renderSmsPage(c); break;
        case 'contacts': renderContactsPage(c); break;
        case 'templates': renderTemplatesPage(c); break;
        case 'history': renderHistoryPage(c); break;
        case 'account': renderAccountPage(c); break;
    }
}

// ===== SMS Page =====
function renderSmsPage(c) {
    selectedRecipients = [];
    currentSendType = 'phone';
    c.innerHTML = `
        <h1 class="page-title">문자 보내기</h1>
        <p class="page-sub">폰 발송(무료) 또는 유료 웹 발송을 선택하세요</p>

        <div class="send-type-toggle">
            <div class="send-type-btn active" id="typePhone" onclick="setSendType('phone')">
                <div class="send-type-icon">📱</div>
                <div class="send-type-label">폰 발송 (무료)</div>
                <div class="desc">내 폰으로 순차 발송<br>20건/분 속도</div>
            </div>
            <div class="send-type-btn" id="typePaid" onclick="setSendType('paid')">
                <div class="send-type-icon">⚡</div>
                <div class="send-type-label">유료 즉시 발송</div>
                <div class="desc">서버에서 즉시 발송<br>크레딧 차감</div>
            </div>
        </div>

        <!-- 광고 토글 -->
        <div class="card" style="padding:12px 16px;display:flex;align-items:center;justify-content:space-between;">
            <div>
                <span style="font-weight:600;font-size:13px;">광고성 문자</span>
                <span style="font-size:11px;color:var(--text3);margin-left:8px;">광고 문자는 유료 발송만 가능합니다</span>
            </div>
            <label class="toggle">
                <input type="checkbox" id="adToggle" onchange="onAdToggle()">
                <span class="toggle-slider"></span>
            </label>
        </div>

        <div class="sms-layout">
            <div>
                <div class="card">
                    <div style="display:flex;justify-content:space-between;align-items:center;">
                        <div class="card-title">메시지 내용</div>
                        <div style="display:flex;gap:6px;">
                            <button class="btn btn-sm btn-secondary" onclick="handleSmsImageUpload()">📎 이미지</button>
                            <button class="btn btn-sm btn-secondary" onclick="openTemplateSelect()">📋 템플릿</button>
                            <button class="btn btn-sm btn-accent" onclick="openAiGenerate()">✨ AI 작성</button>
                        </div>
                    </div>
                    <textarea class="input" id="smsMessage" placeholder="메시지를 입력하세요&#10;&#10;변수 사용: %이름%, %회사%, %메모%" oninput="updateCharCount()" style="min-height:160px;"></textarea>
                    <div style="display:flex;justify-content:space-between;align-items:center;">
                        <div class="char-count"><span id="charCount">0</span>자 (<span id="byteCount">0</span>바이트) · <span id="msgType" class="badge badge-phone">SMS</span></div>
                        <div id="adWarning" style="display:none;color:var(--orange);font-size:11px;">⚠️ 광고 감지됨</div>
                    </div>

                    <!-- 변수 미리보기 -->
                    <div id="previewArea" style="display:none;margin-top:12px;padding:12px;background:var(--bg);border-radius:8px;border:1px solid var(--border);">
                        <div style="font-size:11px;color:var(--text3);margin-bottom:4px;">미리보기 (첫 수신자 기준)</div>
                        <div id="previewText" style="font-size:13px;white-space:pre-wrap;"></div>
                    </div>
                </div>

                <!-- 발송 정보 & 버튼 -->
                <div class="card" id="sendInfoCard">
                    <div id="sendInfo" style="font-size:13px;color:var(--text2);margin-bottom:12px;"></div>
                    <div style="margin-bottom:8px;">
                        <div style="font-size:12px;font-weight:600;color:var(--text2);margin-bottom:6px;">예약 발송</div>
                        <div class="quick-schedule-row">
                            <button class="quick-sched-btn" onclick="setQuickSchedule('1h')">1시간 후</button>
                            <button class="quick-sched-btn" onclick="setQuickSchedule('today18')">오늘 18시</button>
                            <button class="quick-sched-btn" onclick="setQuickSchedule('tomorrow9')">내일 09시</button>
                            <input type="datetime-local" class="input" id="scheduledAt" style="margin:0;width:180px;font-size:12px;padding:6px 8px;" title="직접 선택" onchange="onScheduleChange()">
                            <button class="quick-sched-btn" onclick="clearSchedule()" style="color:var(--red);border-color:var(--red);">취소</button>
                        </div>
                        <div id="scheduleHint" style="font-size:11px;color:var(--accent2);margin-top:4px;display:none;"></div>
                    </div>
                    <button class="btn btn-primary w-full" id="sendBtn" onclick="sendSms()">발송하기</button>
                </div>

                <!-- 진행률 -->
                <div id="progressCard" class="card" style="display:none;">
                    <div class="card-title">발송 진행</div>
                    <div class="progress-bar-bg"><div class="progress-bar-fill" id="progressBar"></div></div>
                    <div id="progressText" style="font-size:12px;color:var(--text3);margin-top:8px;text-align:center;"></div>
                </div>
            </div>

            <div>
                <div class="card">
                    <div style="display:flex;justify-content:space-between;align-items:center;">
                        <div class="card-title">수신자 <span id="recipientCount" style="color:var(--accent2)">0</span>명</div>
                    </div>
                    <div style="display:flex;gap:6px;margin-bottom:8px;">
                        <input class="input" id="addPhone" placeholder="전화번호" style="margin:0;flex:1" onkeydown="if(event.key==='Enter')addRecipient()">
                        <button class="btn btn-secondary btn-sm" onclick="addRecipient()">추가</button>
                    </div>
                    <button class="btn btn-secondary btn-sm w-full" style="margin-bottom:8px;" onclick="openContactPicker()">📋 연락처에서 선택</button>
                    <div class="recipient-list" id="recipientList">
                        <div class="empty-hint">수신자를 추가하세요</div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Contact Picker Modal -->
        <div id="contactPickerModal" class="modal-overlay">
            <div class="modal-box" style="width:700px;max-height:85vh;overflow-y:auto;">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                    <div class="card-title" style="margin:0">연락처 선택</div>
                    <button class="btn btn-sm btn-secondary" onclick="closeModal('contactPickerModal')">닫기</button>
                </div>
                <!-- 카테고리 필터 -->
                <div id="categoryFilter" style="display:flex;gap:6px;margin-bottom:8px;flex-wrap:wrap;"></div>
                <input class="input" id="contactSearchInput" placeholder="이름/번호 검색" oninput="filterPickerContacts()">
                <div style="display:flex;justify-content:space-between;align-items:center;margin:8px 0;">
                    <button class="btn btn-sm btn-secondary" onclick="pickAll()">전체 선택</button>
                    <button class="btn btn-sm btn-secondary" onclick="unpickAll()">전체 해제</button>
                </div>
                <div id="contactPickerList" style="max-height:55vh;overflow-y:auto;"></div>
                <button class="btn btn-primary w-full" style="margin-top:12px" onclick="confirmContactPick()">선택 완료</button>
            </div>
        </div>

        <!-- AI Modal -->
        <div id="aiModal" class="modal-overlay">
            <div class="modal-box" style="width:450px;">
                <div class="card-title">AI 메시지 작성</div>
                <textarea class="input" id="aiPrompt" placeholder="예: VIP 고객에게 신년 인사 문자" style="min-height:80px"></textarea>
                <select class="input" id="aiTone">
                    <option value="정중">정중한 톤</option>
                    <option value="친근">친근한 톤</option>
                    <option value="공식">공식적인 톤</option>
                    <option value="캐주얼">캐주얼한 톤</option>
                </select>
                <div id="aiResult" style="display:none;margin:12px 0;padding:12px;background:var(--bg);border-radius:8px;font-size:13px;white-space:pre-wrap;border:1px solid var(--border);"></div>
                <div style="display:flex;gap:8px;">
                    <button class="btn btn-accent" style="flex:1" id="aiGenBtn" onclick="generateAi()">✨ 생성하기</button>
                    <button class="btn btn-primary" style="flex:1;display:none;" id="aiUseBtn" onclick="useAiResult()">사용하기</button>
                    <button class="btn btn-secondary" onclick="closeModal('aiModal')">닫기</button>
                </div>
            </div>
        </div>

        <!-- Template Select Modal -->
        <div id="templateSelectModal" class="modal-overlay">
            <div class="modal-box" style="width:500px;max-height:70vh;overflow-y:auto;">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                    <div class="card-title" style="margin:0">템플릿 선택</div>
                    <button class="btn btn-sm btn-secondary" onclick="closeModal('templateSelectModal')">닫기</button>
                </div>
                <div id="templateSelectList"></div>
            </div>
        </div>`;

    updateSendInfo();
}

function setSendType(type) {
    currentSendType = type;
    document.getElementById('typePhone').classList.toggle('active', type==='phone');
    document.getElementById('typePaid').classList.toggle('active', type==='paid');
    updateSendInfo();
}

function onAdToggle() {
    const isAd = document.getElementById('adToggle').checked;
    if (isAd) {
        setSendType('paid');
        document.getElementById('typePhone').style.opacity = '0.4';
        document.getElementById('typePhone').style.pointerEvents = 'none';
    } else {
        document.getElementById('typePhone').style.opacity = '1';
        document.getElementById('typePhone').style.pointerEvents = 'auto';
    }
    updateSendInfo();
}

function updateCharCount() {
    const msg = document.getElementById('smsMessage').value;
    const bytes = new Blob([msg]).size;
    document.getElementById('charCount').textContent = msg.length;
    document.getElementById('byteCount').textContent = bytes;
    const typeEl = document.getElementById('msgType');
    if (bytes <= 90) {
        typeEl.textContent = 'SMS'; typeEl.className = 'badge badge-phone';
    } else {
        typeEl.textContent = 'LMS'; typeEl.className = 'badge badge-paid';
    }

    // 변수 미리보기
    if (msg.includes('%') && selectedRecipients.length > 0) {
        const r = selectedRecipients[0];
        const contact = allContacts.find(c => c.phone === r.phone);
        let preview = msg
            .replace(/%이름%/g, contact?.name || r.name || '홍길동')
            .replace(/%회사%/g, contact?.company || '')
            .replace(/%전화번호%/g, r.phone)
            .replace(/%이메일%/g, contact?.email || '')
            .replace(/%메모%/g, contact?.memo || '');
        document.getElementById('previewArea').style.display = 'block';
        document.getElementById('previewText').textContent = preview;
    } else {
        document.getElementById('previewArea').style.display = 'none';
    }

    // 광고 감지 (간이)
    const adKeywords = ['할인','이벤트','쿠폰','세일','프로모션','특가','무료체험'];
    const found = adKeywords.filter(k => msg.includes(k));
    const warn = document.getElementById('adWarning');
    if (warn) {
        if (found.length >= 2) {
            warn.style.display = 'block';
            warn.textContent = `⚠️ 광고 감지: ${found.join(', ')}`;
        } else {
            warn.style.display = 'none';
        }
    }

    updateSendInfo();
}

function updateSendInfo() {
    const el = document.getElementById('sendInfo');
    if (!el) return;
    const count = selectedRecipients.length;
    const msg = document.getElementById('smsMessage')?.value || '';
    const bytes = new Blob([msg]).size;
    const isAd = document.getElementById('adToggle')?.checked || false;

    if (count === 0) {
        el.innerHTML = '<span style="color:var(--text3)">수신자를 추가하세요</span>';
        return;
    }

    if (currentSendType === 'phone') {
        if (count <= 10) {
            el.innerHTML = `📱 <strong>${count}건</strong> 즉시 폰 발송 (무료)`;
        } else {
            const eta = Math.ceil(count / 20);
            el.innerHTML = `📱 <strong>${count}건</strong>, 약 <strong>${eta}분</strong> 소요 (20건/분 순차 발송)
                <div style="font-size:11px;color:var(--text3);margin-top:4px;">⚡ 즉시 발송을 원하시면 유료 발송을 선택하세요</div>`;
        }
    } else {
        const unitCost = bytes <= 90 ? 9.8 : 29;
        const total = Math.ceil(unitCost * count);
        const balance = userInfo ? parseInt(userInfo.balance) : 0;
        const msgTypeLabel = bytes <= 90 ? 'SMS' : 'LMS';
        el.innerHTML = `⚡ <strong>${count}건 × ${unitCost}원 = ${total.toLocaleString()}원</strong> (${msgTypeLabel})
            <div style="font-size:11px;color:var(--text3);margin-top:4px;">현재 잔액: ${balance.toLocaleString()}원${isAd ? ' · (광고) 자동 표기' : ''}</div>`;
    }
}

// Quick schedule helpers
function setQuickSchedule(type) {
    const now = new Date();
    let d;
    if (type === '1h') {
        d = new Date(now.getTime() + 60 * 60 * 1000);
    } else if (type === 'today18') {
        d = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 18, 0, 0);
        if (d <= now) d = new Date(d.getTime() + 86400000); // next day if past
    } else if (type === 'tomorrow9') {
        d = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 9, 0, 0);
    }
    if (d) {
        const pad = n => String(n).padStart(2, '0');
        const val = `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
        document.getElementById('scheduledAt').value = val;
        onScheduleChange();
    }
    // Highlight active button
    document.querySelectorAll('.quick-sched-btn').forEach(b => b.classList.remove('active'));
    event.target.classList.add('active');
}

function clearSchedule() {
    document.getElementById('scheduledAt').value = '';
    document.querySelectorAll('.quick-sched-btn').forEach(b => b.classList.remove('active'));
    const hint = document.getElementById('scheduleHint');
    if (hint) { hint.style.display = 'none'; hint.textContent = ''; }
}

function onScheduleChange() {
    const val = document.getElementById('scheduledAt').value;
    const hint = document.getElementById('scheduleHint');
    if (val && hint) {
        const d = new Date(val);
        hint.style.display = 'block';
        hint.textContent = `${d.toLocaleString('ko-KR')} 에 예약 발송됩니다`;
    } else if (hint) {
        hint.style.display = 'none';
    }
}

// Recipient management
function addRecipient() {
    const input = document.getElementById('addPhone');
    const phone = input.value.trim().replace(/-/g,'');
    if (!phone) return;
    if (selectedRecipients.find(r => r.phone === phone)) { showToast('이미 추가됨','error'); return; }
    selectedRecipients.push({phone, name: phone});
    input.value = '';
    renderRecipients();
    updateSendInfo();
    updateCharCount();
}

function removeRecipient(idx) {
    selectedRecipients.splice(idx, 1);
    renderRecipients();
    updateSendInfo();
}

function renderRecipients() {
    const el = document.getElementById('recipientList');
    document.getElementById('recipientCount').textContent = selectedRecipients.length;
    if (selectedRecipients.length === 0) {
        el.innerHTML = '<div class="empty-hint">수신자를 추가하세요</div>';
        return;
    }
    el.innerHTML = selectedRecipients.map((r,i) =>
        `<div class="recipient-chip"><span class="recipient-name">${r.name !== r.phone ? esc(r.name)+' ' : ''}${r.phone}</span><span class="remove" onclick="removeRecipient(${i})">&times;</span></div>`
    ).join('');
}

// ===== Send =====
async function sendSms() {
    const message = document.getElementById('smsMessage').value.trim();
    if (!message) { showToast('메시지를 입력하세요','error'); return; }
    if (selectedRecipients.length === 0) { showToast('수신자를 추가하세요','error'); return; }

    const isAd = document.getElementById('adToggle')?.checked || false;
    const scheduledAt = document.getElementById('scheduledAt')?.value || '';
    const btn = document.getElementById('sendBtn');
    btn.disabled = true; btn.textContent = '발송 중...';

    try {
        if (currentSendType === 'phone') {
            const payload = {
                recipients: selectedRecipients.map(r => ({phone:r.phone, name:r.name})),
                message, isAd
            };
            if (scheduledAt) payload.scheduledAt = new Date(scheduledAt).getTime();
            const res = await apiFetch('/user/sms/phone-send', {
                method:'POST',
                body: JSON.stringify(payload)
            });
            showToast(res.message || '발송 요청 완료');

            // 진행률 표시
            if (res.jobId) {
                currentJobId = res.jobId;
                document.getElementById('progressCard').style.display = 'block';
                document.getElementById('sendInfoCard').style.display = 'none';
                pollProgress(res.jobId);
            }
        } else {
            const payload = {
                phones: selectedRecipients.map(r => r.phone),
                message, isAd
            };
            if (scheduledAt) payload.scheduledAt = new Date(scheduledAt).getTime();
            const res = await apiFetch('/user/sms/paid-send', {
                method:'POST',
                body: JSON.stringify(payload)
            });
            showToast(`발송 완료: 성공 ${res.success}건 (${Number(res.totalCost).toLocaleString()}원 차감)`);
            loadUserInfo();
        }

        // Reset UI for next send
        document.getElementById('smsMessage').value = '';
        selectedRecipients = [];
        renderRecipients();
        updateCharCount();
        document.getElementById('sendInfoCard').style.display = 'block';
        document.getElementById('progressCard').style.display = 'none';
        if (document.getElementById('scheduledAt')) document.getElementById('scheduledAt').value = '';
    } catch(e) {
        showToast(e.message || '발송 실패','error');
    } finally {
        btn.disabled = false; btn.textContent = '발송하기';
    }
}

function pollProgress(jobId) {
    const update = async () => {
        try {
            const p = await apiFetch(`/user/sms/job/${jobId}/progress`);
            const pct = parseInt(p.progress) || 0;
            const sent = parseInt(p.sent)||0, failed = parseInt(p.failed)||0;
            const total = parseInt(p.total)||0, pending = parseInt(p.pending)||0;
            document.getElementById('progressBar').style.width = pct + '%';
            document.getElementById('progressText').textContent =
                `${sent + failed} / ${total} 처리됨 (성공: ${sent}, 실패: ${failed}${pending > 0 ? `, 대기: ${pending}` : ''})`;

            if (p.status === 'completed' || pending === 0) {
                clearInterval(progressInterval);
                progressInterval = null;
                document.getElementById('progressText').textContent += ' ✅ 완료';
                document.getElementById('progressBar').style.width = '100%';
            }
        } catch(e) {}
    };
    update();
    progressInterval = setInterval(update, 3000);
}

// ===== Contact Picker =====
let pickedPhones = new Set();
let pickerFilter = '';

async function openContactPicker() {
    pickedPhones = new Set(selectedRecipients.map(r => r.phone));
    pickerFilter = '';
    document.getElementById('contactSearchInput').value = '';
    document.getElementById('contactPickerModal').classList.add('show');
    // Reload contacts to ensure fresh data
    await loadContactsCache();
    renderCategoryFilter();
    renderPickerList(allContacts);
}

function renderCategoryFilter() {
    const el = document.getElementById('categoryFilter');
    const contactCats = allContacts.map(c => c.category).filter(Boolean);
    const userCats = allCategories.map(c => c.name).filter(Boolean);
    const cats = [...new Set([...contactCats, ...userCats])].sort();
    el.innerHTML = `<button class="chip chip-active" onclick="filterByCategory('')">전체</button>` +
        cats.map(cat => `<button class="chip" onclick="filterByCategory('${esc(cat)}')">${esc(cat)}</button>`).join('');
}

function filterByCategory(cat) {
    pickerFilter = cat;
    document.querySelectorAll('#categoryFilter .chip').forEach(el => {
        el.classList.toggle('chip-active', el.textContent === (cat || '전체'));
    });
    filterPickerContacts();
}

function filterPickerContacts() {
    const q = document.getElementById('contactSearchInput').value.toLowerCase();
    let filtered = allContacts;
    if (pickerFilter) filtered = filtered.filter(c => c.category === pickerFilter);
    if (q) filtered = filtered.filter(c => c.name.toLowerCase().includes(q) || c.phone.includes(q));
    renderPickerList(filtered);
}

function renderPickerList(list) {
    document.getElementById('contactPickerList').innerHTML = list.length === 0
        ? '<div class="empty-hint">연락처가 없습니다</div>'
        : list.map(c => `
            <label class="picker-row">
                <input type="checkbox" ${pickedPhones.has(c.phone)?'checked':''} onchange="togglePick('${c.phone}',this.checked)">
                <span class="picker-name">${esc(c.name)}</span>
                <span class="picker-phone">${c.phone}</span>
                ${c.category ? `<span class="badge badge-phone">${esc(c.category)}</span>` : ''}
            </label>`).join('');
}

function togglePick(phone, checked) { if (checked) pickedPhones.add(phone); else pickedPhones.delete(phone); }
function pickAll() { const items = document.querySelectorAll('#contactPickerList input[type=checkbox]'); items.forEach(i => { i.checked = true; togglePick(i.closest('label').querySelector('.picker-phone').textContent, true); }); }
function unpickAll() { const items = document.querySelectorAll('#contactPickerList input[type=checkbox]'); items.forEach(i => { i.checked = false; }); pickedPhones.clear(); }

function confirmContactPick() {
    selectedRecipients = [];
    for (const phone of pickedPhones) {
        const contact = allContacts.find(c => c.phone === phone);
        selectedRecipients.push({phone, name: contact ? contact.name : phone});
    }
    renderRecipients();
    updateSendInfo();
    updateCharCount();
    closeModal('contactPickerModal');
}

// ===== AI =====
function openAiGenerate() {
    document.getElementById('aiModal').classList.add('show');
    document.getElementById('aiResult').style.display = 'none';
    document.getElementById('aiUseBtn').style.display = 'none';
    document.getElementById('aiPrompt').value = '';
}

async function generateAi() {
    const prompt = document.getElementById('aiPrompt').value;
    const tone = document.getElementById('aiTone').value;
    if (!prompt) { showToast('프롬프트를 입력하세요','error'); return; }
    const btn = document.getElementById('aiGenBtn');
    btn.disabled = true; btn.textContent = '생성 중...';
    try {
        const res = await apiFetch('/user/ai/generate', {method:'POST', body:JSON.stringify({prompt, tone})});
        document.getElementById('aiResult').textContent = res.content;
        document.getElementById('aiResult').style.display = 'block';
        document.getElementById('aiUseBtn').style.display = 'block';
    } catch(e) { showToast(e.message,'error'); }
    btn.disabled = false; btn.textContent = '✨ 생성하기';
}

function useAiResult() {
    document.getElementById('smsMessage').value = document.getElementById('aiResult').textContent;
    updateCharCount();
    closeModal('aiModal');
}

// ===== Template Select =====
function openTemplateSelect() {
    document.getElementById('templateSelectModal').classList.add('show');
    const listEl = document.getElementById('templateSelectList');

    // User templates
    let html = '<div style="font-weight:700;font-size:13px;margin-bottom:8px;">📝 내 템플릿</div>';
    html += allTemplates.length === 0
        ? '<div class="empty-hint">저장된 템플릿이 없습니다</div>'
        : allTemplates.map(t => `
            <div class="template-card" onclick="useTemplate('${esc(t.content.replace(/'/g,"\\'"))}')">
                <div style="font-weight:600;font-size:13px;">${esc(t.title)}</div>
                <div style="font-size:12px;color:var(--text3);margin-top:4px;">${esc(t.content.substring(0,80))}${t.content.length>80?'...':''}</div>
                ${t.isFromPhone==='true' ? '<span class="badge badge-phone" style="margin-top:4px;">📱 폰</span>' : ''}
            </div>`).join('');

    // Admin templates section (loaded async)
    html += '<div style="font-weight:700;font-size:13px;margin:16px 0 8px;">📦 제공 템플릿</div>';
    html += '<div id="templateSelectAdminList"><div class="empty-hint">로딩 중...</div></div>';
    listEl.innerHTML = html;

    // Load admin templates
    apiFetch('/user/admin-templates').then(data => {
        const el = document.getElementById('templateSelectAdminList');
        if (!el) return;
        const categories = data.data || [];
        if (categories.length === 0) { el.innerHTML = '<div class="empty-hint">제공 템플릿이 없습니다</div>'; return; }
        el.innerHTML = categories.map(cat => {
            const templates = cat.templates || [];
            return `
                <div style="margin-bottom:12px;">
                    <div style="font-weight:600;font-size:12px;color:var(--accent2);margin-bottom:6px;">${cat.icon || '📋'} ${esc(cat.name)}</div>
                    ${templates.map(t => `
                        <div class="template-card" onclick="useTemplate('${esc(t.content.replace(/'/g,"\\'").replace(/\n/g,"\\n"))}')">
                            <div style="font-weight:600;font-size:13px;">${esc(t.title)}</div>
                            <div style="font-size:12px;color:var(--text3);margin-top:4px;">${esc(t.content.substring(0,80))}${t.content.length>80?'...':''}</div>
                        </div>`).join('')}
                </div>`;
        }).join('');
    }).catch(() => {
        const el = document.getElementById('templateSelectAdminList');
        if (el) el.innerHTML = '<div class="empty-hint">로딩 실패</div>';
    });
}

function useTemplate(content) {
    document.getElementById('smsMessage').value = content.replace(/\\n/g, '\n');
    updateCharCount();
    closeModal('templateSelectModal');
}

// ===== Contacts Page =====
async function renderContactsPage(c) {
    c.innerHTML = `
        <div style="display:flex;justify-content:space-between;align-items:flex-start;">
            <div><h1 class="page-title">연락처 관리</h1><p class="page-sub">고객 연락처를 관리하고 문자 발송에 활용하세요</p></div>
            <div style="display:flex;gap:8px;">
                <button class="btn btn-secondary" onclick="downloadContactsCsv()">CSV 다운로드</button>
                <button class="btn btn-secondary" onclick="showImportModal()">일괄 가져오기</button>
                <button class="btn btn-primary" onclick="showAddContact()">+ 연락처 추가</button>
            </div>
        </div>

        <!-- 카테고리 칩 -->
        <div id="contactCategoryChips" style="display:flex;gap:6px;margin-bottom:12px;flex-wrap:wrap;"></div>

        <div class="card">
            <div style="display:flex;gap:8px;margin-bottom:12px;">
                <input class="input" id="contactSearch" placeholder="이름/번호 검색" style="margin:0;flex:1" onkeydown="if(event.key==='Enter')searchContacts()">
                <button class="btn btn-secondary" onclick="searchContacts()">검색</button>
            </div>
            <div id="contactTable"></div>
            <div class="pagination" id="contactPagination"></div>
        </div>

        <!-- Modals -->
        <div id="contactModal" class="modal-overlay">
            <div class="modal-box" style="width:420px;">
                <div class="card-title" id="contactModalTitle">연락처 추가</div>
                <input class="input" id="cName" placeholder="이름 *">
                <input class="input" id="cPhone" placeholder="전화번호 *">
                <input class="input" id="cEmail" placeholder="이메일">
                <input class="input" id="cCompany" placeholder="회사">
                <select class="input" id="cCategory"><option value="">카테고리 선택</option></select>
                <div style="display:flex;gap:8px;">
                    <input class="input" id="cBirthday" placeholder="생일 (MM-DD)" type="text" style="margin:0;flex:1;">
                    <input class="input" id="cAnniversary" placeholder="기념일 (MM-DD)" type="text" style="margin:0;flex:1;">
                </div>
                <textarea class="input" id="cMemo" placeholder="메모" style="min-height:60px"></textarea>
                <input type="hidden" id="cEditId">
                <div style="display:flex;gap:8px;">
                    <button class="btn btn-primary" style="flex:1" onclick="saveContact()">저장</button>
                    <button class="btn btn-secondary" onclick="closeModal('contactModal')">취소</button>
                </div>
            </div>
        </div>

        <div id="importModal" class="modal-overlay">
            <div class="modal-box" style="width:500px;">
                <div class="card-title">연락처 일괄 가져오기</div>
                <div style="display:flex;gap:8px;margin-bottom:12px;">
                    <button class="btn btn-sm" id="importFormatJson" style="background:var(--accent);color:#fff;" onclick="setImportFormat('json')">JSON</button>
                    <button class="btn btn-sm btn-secondary" id="importFormatCsv" onclick="setImportFormat('csv')">CSV</button>
                </div>
                <p id="importHintJson" style="font-size:12px;color:var(--text3);margin-bottom:12px;">JSON 형식:<br>[{"name":"홍길동","phone":"01012345678","company":"ABC","category":"VIP"}]</p>
                <p id="importHintCsv" style="font-size:12px;color:var(--text3);margin-bottom:12px;display:none;">CSV 형식 (첫 줄 헤더):<br>name,phone,company,category,email,memo<br>홍길동,01012345678,ABC,VIP,,</p>
                <textarea class="input" id="importData" style="min-height:150px" placeholder='[{"name":"홍길동","phone":"01012345678"}]'></textarea>
                <div style="margin-bottom:8px;">
                    <label class="btn btn-secondary btn-sm" style="cursor:pointer;">
                        📂 파일 선택
                        <input type="file" id="importFile" accept=".json,.csv,.txt" style="display:none" onchange="loadImportFile(event)">
                    </label>
                    <span id="importFileName" style="font-size:11px;color:var(--text3);margin-left:8px;"></span>
                </div>
                <div style="display:flex;gap:8px;">
                    <button class="btn btn-primary" style="flex:1" onclick="doImport()">가져오기</button>
                    <button class="btn btn-secondary" onclick="closeModal('importModal')">취소</button>
                </div>
            </div>
        </div>

        <!-- Category Manage Modal -->
        <div id="categoryManageModal" class="modal-overlay">
            <div class="modal-box" style="width:380px;">
                <div class="card-title">카테고리 관리</div>
                <div id="categoryManageList"></div>
                <div style="display:flex;gap:6px;margin-top:12px;">
                    <input class="input" id="newCatName" placeholder="새 카테고리" style="margin:0;flex:1">
                    <button class="btn btn-primary btn-sm" onclick="addCategory()">추가</button>
                </div>
                <button class="btn btn-secondary w-full" style="margin-top:12px" onclick="closeModal('categoryManageModal')">닫기</button>
            </div>
        </div>`;

    renderContactCategoryChips();
    loadContacts(1);
}

function renderContactCategoryChips() {
    const el = document.getElementById('contactCategoryChips');
    if (!el) return;
    const cats = [...new Set(allContacts.map(c => c.category).filter(Boolean))];
    el.innerHTML = `<button class="chip chip-active" onclick="filterContacts('')">전체</button>` +
        cats.map(cat => `<button class="chip" onclick="filterContacts('${esc(cat)}')">${esc(cat)}</button>`).join('') +
        `<button class="chip" style="border-style:dashed;" onclick="openCategoryManage()">+ 관리</button>`;
}

let contactFilterCat = '';
function filterContacts(cat) {
    contactFilterCat = cat;
    document.querySelectorAll('#contactCategoryChips .chip').forEach(el => {
        el.classList.toggle('chip-active', el.textContent === (cat || '전체'));
    });
    loadContacts(1);
}

let contactPage = 1;
async function loadContacts(page) {
    contactPage = page;
    const search = document.getElementById('contactSearch')?.value || '';
    try {
        let url = `/user/contacts?page=${page}&limit=20&search=${encodeURIComponent(search)}`;
        if (contactFilterCat) url += `&category=${encodeURIComponent(contactFilterCat)}`;
        const data = await apiFetch(url);
        const items = data.data || [];
        const total = data.total || 0;
        const totalPages = Math.ceil(total / 20) || 1;

        document.getElementById('contactTable').innerHTML = `<table>
            <thead><tr><th>이름</th><th>전화번호</th><th>회사</th><th>카테고리</th><th>작업</th></tr></thead>
            <tbody>${items.length===0
                ? '<tr><td colspan="5" class="empty-td">연락처가 없습니다</td></tr>'
                : items.map(c=>`<tr>
                    <td style="font-weight:600"><a href="#" onclick="showContactDetail('${c.id}');return false;" style="color:inherit;text-decoration:none;border-bottom:1px dashed var(--text3);">${esc(c.name)}</a></td>
                    <td>${c.phone}</td>
                    <td style="color:var(--text3)">${c.company||'-'}</td>
                    <td style="position:relative;"><span class="badge badge-phone" style="cursor:pointer;" onclick="showCategoryDropdown(event,'${c.id}','${esc(c.category||'')}')">${c.category?esc(c.category):'미지정'}</span></td>
                    <td><button class="btn btn-sm btn-secondary" onclick='editContact(${JSON.stringify(c)})'>수정</button> <button class="btn btn-sm btn-danger" onclick="deleteContact('${c.id}')">삭제</button></td>
                </tr>`).join('')}</tbody></table>`;

        document.getElementById('contactPagination').innerHTML = `
            <button onclick="loadContacts(${page-1})" ${page<=1?'disabled':''}>이전</button>
            <span>${page}/${totalPages} (${total}건)</span>
            <button onclick="loadContacts(${page+1})" ${page>=totalPages?'disabled':''}>다음</button>`;
    } catch(e) { showToast(e.message,'error'); }
}

function searchContacts() { loadContacts(1); }

function showAddContact() {
    document.getElementById('contactModalTitle').textContent = '연락처 추가';
    ['cName','cPhone','cEmail','cCompany','cMemo','cEditId','cBirthday','cAnniversary'].forEach(id => document.getElementById(id).value='');
    populateCategorySelect();
    document.getElementById('contactModal').classList.add('show');
}

function editContact(c) {
    document.getElementById('contactModalTitle').textContent = '연락처 수정';
    document.getElementById('cEditId').value = c.id;
    document.getElementById('cName').value = c.name;
    document.getElementById('cPhone').value = c.phone;
    document.getElementById('cEmail').value = c.email||'';
    document.getElementById('cCompany').value = c.company||'';
    document.getElementById('cBirthday').value = c.birthday||'';
    document.getElementById('cAnniversary').value = c.anniversary||'';
    document.getElementById('cMemo').value = c.memo||'';
    populateCategorySelect(c.category);
    document.getElementById('contactModal').classList.add('show');
}

function populateCategorySelect(selected='') {
    const sel = document.getElementById('cCategory');
    const cats = [...new Set(allContacts.map(c=>c.category).filter(Boolean))];
    sel.innerHTML = '<option value="">카테고리 없음</option>' + cats.map(c=>`<option value="${esc(c)}" ${c===selected?'selected':''}>${esc(c)}</option>`).join('');
}

async function saveContact() {
    const editId = document.getElementById('cEditId').value;
    const body = { name:document.getElementById('cName').value, phone:document.getElementById('cPhone').value,
        email:document.getElementById('cEmail').value, company:document.getElementById('cCompany').value,
        category:document.getElementById('cCategory').value, memo:document.getElementById('cMemo').value,
        birthday:document.getElementById('cBirthday').value, anniversary:document.getElementById('cAnniversary').value };
    if (!body.name||!body.phone) { showToast('이름과 전화번호 필수','error'); return; }
    try {
        if (editId) { await apiFetch(`/user/contacts/${editId}`,{method:'PUT',body:JSON.stringify(body)}); showToast('수정됨'); }
        else { await apiFetch('/user/contacts',{method:'POST',body:JSON.stringify(body)}); showToast('추가됨'); }
        closeModal('contactModal');
        loadContacts(contactPage);
        loadContactsCache();
    } catch(e) { showToast(e.message,'error'); }
}

async function deleteContact(id) {
    if (!confirm('삭제하시겠습니까?')) return;
    try { await apiFetch(`/user/contacts/${id}`,{method:'DELETE'}); showToast('삭제됨'); loadContacts(contactPage); loadContactsCache(); } catch(e) { showToast(e.message,'error'); }
}

function showImportModal() {
    document.getElementById('importModal').classList.add('show');
    importFormat = 'json';
}

let importFormat = 'json';
function setImportFormat(fmt) {
    importFormat = fmt;
    document.getElementById('importFormatJson').style.background = fmt==='json' ? 'var(--accent)' : '';
    document.getElementById('importFormatJson').style.color = fmt==='json' ? '#fff' : '';
    document.getElementById('importFormatJson').className = fmt==='json' ? 'btn btn-sm' : 'btn btn-sm btn-secondary';
    document.getElementById('importFormatCsv').style.background = fmt==='csv' ? 'var(--accent)' : '';
    document.getElementById('importFormatCsv').style.color = fmt==='csv' ? '#fff' : '';
    document.getElementById('importFormatCsv').className = fmt==='csv' ? 'btn btn-sm' : 'btn btn-sm btn-secondary';
    document.getElementById('importHintJson').style.display = fmt==='json' ? 'block' : 'none';
    document.getElementById('importHintCsv').style.display = fmt==='csv' ? 'block' : 'none';
    document.getElementById('importData').placeholder = fmt==='json'
        ? '[{"name":"홍길동","phone":"01012345678"}]'
        : 'name,phone,company,category,email,memo\n홍길동,01012345678,ABC,VIP,,';
}

function loadImportFile(event) {
    const file = event.target.files[0];
    if (!file) return;
    document.getElementById('importFileName').textContent = file.name;
    const reader = new FileReader();
    reader.onload = function(e) {
        document.getElementById('importData').value = e.target.result;
        // Auto-detect format
        if (file.name.endsWith('.csv') || file.name.endsWith('.txt')) setImportFormat('csv');
        else if (file.name.endsWith('.json')) setImportFormat('json');
    };
    reader.readAsText(file);
}

function parseCsvToContacts(csvText) {
    const lines = csvText.trim().split('\n').map(l => l.trim()).filter(l => l);
    if (lines.length < 2) throw new Error('CSV에 데이터가 없습니다 (헤더 + 1줄 이상 필요)');
    const headers = lines[0].split(',').map(h => h.trim().toLowerCase());
    const contacts = [];
    for (let i = 1; i < lines.length; i++) {
        const vals = lines[i].split(',').map(v => v.trim());
        const obj = {};
        headers.forEach((h, idx) => { if (vals[idx]) obj[h] = vals[idx]; });
        if (obj.name && obj.phone) contacts.push(obj);
    }
    return contacts;
}

async function doImport() {
    try {
        const raw = document.getElementById('importData').value.trim();
        let contacts;
        if (importFormat === 'csv') {
            contacts = parseCsvToContacts(raw);
        } else {
            contacts = JSON.parse(raw);
        }
        if (!Array.isArray(contacts) || contacts.length === 0) { showToast('가져올 연락처가 없습니다','error'); return; }
        const res = await apiFetch('/user/contacts/import',{method:'POST',body:JSON.stringify({contacts})});
        showToast(`${res.imported}건 가져오기 완료`);
        closeModal('importModal');
        loadContacts(1); loadContactsCache();
    } catch(e) { showToast('오류: '+e.message,'error'); }
}

function downloadContactsCsv() {
    if (allContacts.length === 0) { showToast('다운로드할 연락처가 없습니다','error'); return; }
    const headers = ['name','phone','email','company','category','memo'];
    const headerKo = ['이름','전화번호','이메일','회사','카테고리','메모'];
    let csv = '\uFEFF' + headerKo.join(',') + '\n';
    allContacts.forEach(c => {
        csv += headers.map(h => {
            const val = String(c[h] || '').replace(/"/g, '""');
            return val.includes(',') || val.includes('"') || val.includes('\n') ? `"${val}"` : val;
        }).join(',') + '\n';
    });
    const blob = new Blob([csv], {type:'text/csv;charset=utf-8;'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `bizconnect_contacts_${new Date().toISOString().slice(0,10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    showToast(`${allContacts.length}건 다운로드 완료`);
}

function openCategoryManage() {
    document.getElementById('categoryManageModal').classList.add('show');
    renderCategoryManageList();
}

function renderCategoryManageList() {
    document.getElementById('categoryManageList').innerHTML = allCategories.length===0
        ? '<div class="empty-hint">카테고리가 없습니다</div>'
        : allCategories.map(c=>`<div style="display:flex;justify-content:space-between;align-items:center;padding:8px 0;border-bottom:1px solid var(--border);">
            <span class="badge badge-phone">${esc(c.name)}</span>
            <button class="btn btn-sm btn-danger" onclick="deleteCategory('${c.id}')">삭제</button>
        </div>`).join('');
}

async function addCategory() {
    const name = document.getElementById('newCatName').value.trim();
    if (!name) return;
    try {
        await apiFetch('/user/categories',{method:'POST',body:JSON.stringify({name})});
        document.getElementById('newCatName').value = '';
        await loadCategoriesCache();
        renderCategoryManageList();
        renderContactCategoryChips();
        showToast('카테고리 추가됨');
    } catch(e) { showToast(e.message,'error'); }
}

async function deleteCategory(id) {
    try {
        await apiFetch(`/user/categories/${id}`,{method:'DELETE'});
        await loadCategoriesCache();
        renderCategoryManageList();
        renderContactCategoryChips();
    } catch(e) { showToast(e.message,'error'); }
}

// ===== Templates Page =====
function renderTemplatesPage(c) {
    c.innerHTML = `
        <div style="display:flex;justify-content:space-between;align-items:center;">
            <div><h1 class="page-title">템플릿 관리</h1><p class="page-sub">자주 사용하는 문자를 저장하세요</p></div>
            <div style="display:flex;gap:8px;">
                <button class="btn btn-primary" onclick="showAddTemplate()">+ 새 템플릿</button>
            </div>
        </div>

        <div style="font-weight:700;font-size:15px;margin-bottom:8px;">📝 내 템플릿</div>
        <div class="template-grid" id="templateGrid"></div>

        <div style="font-weight:700;font-size:15px;margin:24px 0 8px;">📦 제공 템플릿</div>
        <div id="adminTemplatesInline"><div class="empty-hint">로딩 중...</div></div>

        <div id="templateModal" class="modal-overlay">
            <div class="modal-box" style="width:450px;">
                <div class="card-title" id="tplModalTitle">새 템플릿</div>
                <input class="input" id="tplTitle" placeholder="템플릿 이름">
                <textarea class="input" id="tplContent" placeholder="메시지 내용 (%이름%, %회사% 사용 가능)" style="min-height:120px"></textarea>
                <button class="btn btn-sm btn-secondary" style="margin-bottom:10px;" onclick="handleTemplateImageUpload()">📎 이미지 첨부</button>
                <input type="hidden" id="tplEditId">
                <div style="display:flex;gap:8px;">
                    <button class="btn btn-primary" style="flex:1" onclick="saveTemplate()">저장</button>
                    <button class="btn btn-secondary" onclick="closeModal('templateModal')">취소</button>
                </div>
            </div>
        </div>

        <div id="adminTemplatesModal" class="modal-overlay">
            <div class="modal-box" style="width:560px;max-height:80vh;overflow-y:auto;">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
                    <div class="card-title" style="margin:0">📦 제공 템플릿</div>
                    <button class="btn btn-sm btn-secondary" onclick="closeModal('adminTemplatesModal')">닫기</button>
                </div>
                <div id="adminTemplatesList"><div class="empty-hint">로딩 중...</div></div>
            </div>
        </div>`;
    renderTemplateGrid();
    loadAdminTemplatesInline();
}

async function loadAdminTemplatesInline() {
    const el = document.getElementById('adminTemplatesInline');
    if (!el) return;
    try {
        const data = await apiFetch('/user/admin-templates');
        const categories = data.data || [];
        if (categories.length === 0) {
            el.innerHTML = '<div class="empty-hint">제공 템플릿이 없습니다</div>';
            return;
        }
        el.innerHTML = categories.map(cat => {
            const templates = cat.templates || [];
            return `
                <div style="margin-bottom:20px;">
                    <div style="font-weight:600;font-size:14px;margin-bottom:8px;color:var(--accent2);">${cat.icon || '📋'} ${esc(cat.name)}</div>
                    ${templates.length === 0 ? '<div class="empty-hint">템플릿이 없습니다</div>' :
                    `<div class="template-grid">${templates.map(t => `
                        <div class="template-card">
                            <div style="font-weight:600;font-size:13px;margin-bottom:4px;">${esc(t.title)}</div>
                            <div style="font-size:12px;color:var(--text2);white-space:pre-wrap;max-height:80px;overflow:hidden;margin-bottom:8px;">${esc(t.content)}</div>
                            <div style="display:flex;gap:6px;">
                                <button class="btn btn-sm btn-primary" onclick="useAdminTemplate('${esc(t.content.replace(/'/g,"\\'").replace(/\n/g,"\\n"))}')">사용</button>
                                <button class="btn btn-sm btn-secondary" onclick="saveAdminTemplateToMine('${esc(t.title.replace(/'/g,"\\'"))}','${esc(t.content.replace(/'/g,"\\'").replace(/\n/g,"\\n"))}')">내 템플릿 저장</button>
                            </div>
                        </div>
                    `).join('')}</div>`}
                </div>`;
        }).join('');
    } catch(e) {
        el.innerHTML = `<div class="empty-hint" style="color:var(--red);">제공 템플릿 로딩 실패</div>`;
    }
}

function renderTemplateGrid() {
    const el = document.getElementById('templateGrid');
    if (!el) return;
    el.innerHTML = allTemplates.length===0
        ? '<div class="empty-hint" style="padding:40px;">템플릿이 없습니다. + 새 템플릿을 눌러 추가하세요.</div>'
        : allTemplates.map(t=>`
            <div class="template-card">
                <div style="font-weight:700;margin-bottom:6px;">${esc(t.title)}</div>
                <div style="font-size:12px;color:var(--text2);white-space:pre-wrap;max-height:80px;overflow:hidden;">${esc(t.content)}</div>
                <div style="display:flex;gap:6px;margin-top:12px;">
                    <button class="btn btn-sm btn-primary" onclick="navigate('sms');setTimeout(()=>useTemplate('${esc(t.content.replace(/'/g,"\\'"))}'),100)">사용</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteTemplate('${t.id}')">삭제</button>
                </div>
            </div>`).join('');
}

function showAddTemplate() {
    document.getElementById('tplModalTitle').textContent = '새 템플릿';
    document.getElementById('tplTitle').value = '';
    document.getElementById('tplContent').value = '';
    document.getElementById('tplEditId').value = '';
    document.getElementById('templateModal').classList.add('show');
}

async function saveTemplate() {
    const title = document.getElementById('tplTitle').value;
    const content = document.getElementById('tplContent').value;
    if (!title||!content) { showToast('이름과 내용 필수','error'); return; }
    try {
        await apiFetch('/user/templates',{method:'POST',body:JSON.stringify({title,content})});
        showToast('템플릿 저장됨');
        closeModal('templateModal');
        await loadTemplatesCache();
        renderTemplateGrid();
    } catch(e) { showToast(e.message,'error'); }
}

async function deleteTemplate(id) {
    if (!confirm('삭제하시겠습니까?')) return;
    try { await apiFetch(`/user/templates/${id}`,{method:'DELETE'}); await loadTemplatesCache(); renderTemplateGrid(); } catch(e) { showToast(e.message,'error'); }
}

// ===== History Page =====
let historyTab = 'history';
async function renderHistoryPage(c) {
    historyTab = 'history';
    c.innerHTML = `<h1 class="page-title">발송 이력</h1><p class="page-sub">웹에서 발송한 문자 내역</p>
        <div class="history-tabs">
            <button class="history-tab active" id="htabHistory" onclick="switchHistoryTab('history')">발송 이력</button>
            <button class="history-tab" id="htabScheduled" onclick="switchHistoryTab('scheduled')">예약 문자</button>
        </div>
        <div class="card">
            <div id="historyContent"></div>
            <div id="historyTable" style="display:block;"></div>
            <div class="pagination" id="historyPagination"></div>
            <div id="scheduledContent" style="display:none;"></div>
        </div>`;
    loadHistory(1);
}

function switchHistoryTab(tab) {
    historyTab = tab;
    document.getElementById('htabHistory').className = 'history-tab' + (tab==='history' ? ' active' : '');
    document.getElementById('htabScheduled').className = 'history-tab' + (tab==='scheduled' ? ' active' : '');
    if (tab === 'history') {
        document.getElementById('historyTable').style.display = 'block';
        document.getElementById('historyPagination').style.display = 'flex';
        document.getElementById('scheduledContent').style.display = 'none';
        loadHistory(1);
    } else {
        document.getElementById('historyTable').style.display = 'none';
        document.getElementById('historyPagination').style.display = 'none';
        document.getElementById('scheduledContent').style.display = 'block';
        loadScheduledMessages();
    }
}

async function loadScheduledMessages() {
    const el = document.getElementById('scheduledContent');
    el.innerHTML = '<div class="empty-hint">로딩 중...</div>';
    try {
        const data = await apiFetch('/user/sms/scheduled');
        const items = data.data || [];
        if (items.length === 0) {
            el.innerHTML = '<div class="empty-hint">예약된 문자가 없습니다</div>';
            return;
        }
        el.innerHTML = `<table>
            <thead><tr><th>수신자</th><th>메시지</th><th>예약 시간</th><th>방식</th><th>작업</th></tr></thead>
            <tbody>${items.map(s => `<tr>
                <td style="font-weight:500">${esc(s.recipientName||'')} ${s.recipientPhone}</td>
                <td style="color:var(--text2);max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${esc(s.messageContent)}</td>
                <td style="font-size:12px;">${new Date(Number(s.scheduledAt)).toLocaleString('ko-KR')}</td>
                <td><span class="badge badge-${s.sendMethod==='phone'?'phone':'paid'}">${s.sendMethod==='phone'?'폰':'유료'}</span></td>
                <td><button class="btn btn-sm btn-danger" onclick="cancelScheduledSms('${s.id}')">취소</button></td>
            </tr>`).join('')}</tbody></table>`;
    } catch(e) {
        el.innerHTML = `<div class="empty-hint" style="color:var(--red);">로딩 실패: ${e.message}</div>`;
    }
}

async function cancelScheduledSms(id) {
    if (!confirm('이 예약을 취소하시겠습니까?')) return;
    try {
        await apiFetch(`/user/sms/scheduled/${id}`, {method:'DELETE'});
        showToast('예약이 취소되었습니다');
        loadScheduledMessages();
    } catch(e) { showToast(e.message, 'error'); }
}

async function loadHistory(page) {
    try {
        const data = await apiFetch(`/user/sms/history?page=${page}&limit=20`);
        const items = data.data||[]; const total = data.total||0; const totalPages = Math.ceil(total/20)||1;
        document.getElementById('historyTable').innerHTML = `<table>
            <thead><tr><th>수신자</th><th>메시지</th><th>방식</th><th>상태</th><th>시간</th></tr></thead>
            <tbody>${items.length===0?'<tr><td colspan="5" class="empty-td">이력이 없습니다</td></tr>'
            :items.map(h=>`<tr style="${h.status==='failed'?'background:var(--danger-bg, #fff5f5);':''}">
                <td style="font-weight:500">${esc(h.name||'')} ${h.phone}</td>
                <td style="color:var(--text2);max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${esc(h.message)}</td>
                <td><span class="badge badge-${h.method}">${h.method==='phone'?'📱 폰':'⚡ 유료'}</span></td>
                <td><span class="badge badge-${h.status}">${{pending:'대기',sent:'발송',failed:'실패',cancelled:'취소'}[h.status]||h.status}</span>${h.status==='failed'&&h.errorMessage?'<div style="font-size:10px;color:#dc2626;margin-top:2px;">'+esc(h.errorCode?'['+h.errorCode+'] ':'')+esc(h.errorMessage)+'</div>':''}</td>
                <td style="font-size:11px;color:var(--text3)">${new Date(Number(h.createdAt)).toLocaleString('ko-KR')}</td>
            </tr>`).join('')}</tbody></table>`;
        document.getElementById('historyPagination').innerHTML = `
            <button onclick="loadHistory(${page-1})" ${page<=1?'disabled':''}>이전</button>
            <span>${page}/${totalPages} (${total}건)</span>
            <button onclick="loadHistory(${page+1})" ${page>=totalPages?'disabled':''}>다음</button>`;
    } catch(e) { showToast(e.message,'error'); }
}

// ===== Account Page =====
async function renderAccountPage(c) {
    try {
        const u = await apiFetch('/user/me'); userInfo = u;
        const tl = {free:'무료',paid:'Business',premium:'Premium'}[u.tier]||u.tier;
        c.innerHTML = `<h1 class="page-title">내 계정</h1><p class="page-sub">계정 정보 및 크레딧</p>
            <div class="grid-4">
                <div class="stat-box"><div class="stat-val">${tl}</div><div class="stat-lbl">플랜</div></div>
                <div class="stat-box"><div class="stat-val">${Number(u.balance).toLocaleString()}</div><div class="stat-lbl">크레딧 (원)</div></div>
                <div class="stat-box"><div class="stat-val">${u.todayUsage}</div><div class="stat-lbl">오늘 발송</div></div>
                <div class="stat-box"><div class="stat-val">${u.contactCount}</div><div class="stat-lbl">연락처</div></div>
            </div>
            <div class="grid-2" style="margin-top:16px;">
                <div class="card">
                    <div class="card-title">내 정보</div>
                    <div style="font-size:13px;line-height:2.2;">
                        <div><strong>이름:</strong> ${esc(u.name)}</div>
                        <div><strong>전화번호:</strong> ${u.phone}</div>
                        <div><strong>이메일:</strong> ${u.email}</div>
                    </div>
                </div>
                <div class="card">
                    <div class="card-title">크레딧 충전</div>
                    <p style="font-size:11px;color:var(--text3);margin-bottom:12px;">유료 웹 발송에 사용되는 크레딧</p>
                    <div style="display:flex;gap:8px;margin-bottom:12px;">
                        <input class="input" type="number" id="chargeAmount" placeholder="충전 금액" value="10000" style="margin:0;flex:1;">
                        <span style="align-self:center;color:var(--text3);">원</span>
                    </div>
                    <div class="grid-3" style="margin-bottom:12px;">
                        <button class="btn btn-secondary" onclick="document.getElementById('chargeAmount').value='5000'">5,000원</button>
                        <button class="btn btn-secondary" onclick="document.getElementById('chargeAmount').value='10000'">10,000원</button>
                        <button class="btn btn-secondary" onclick="document.getElementById('chargeAmount').value='50000'">50,000원</button>
                    </div>
                    <div style="display:flex;gap:8px;">
                        <button class="btn btn-primary" style="flex:1" onclick="goPayment(parseInt(document.getElementById('chargeAmount').value),'card')">카드결제</button>
                        <button class="btn btn-secondary" style="flex:1" onclick="goPayment(parseInt(document.getElementById('chargeAmount').value),'kakaopay')">카카오페이</button>
                        <button class="btn btn-secondary" style="flex:1" onclick="goPayment(parseInt(document.getElementById('chargeAmount').value),'naverpay')">네이버페이</button>
                    </div>
                    <div style="font-size:10px;color:var(--text3);margin-top:8px;">SMS 9.8원 · LMS 29원 · MMS 63원</div>
                </div>
            </div>`;
    } catch(e) { c.innerHTML = `<div style="color:var(--red);padding:40px;">로딩 실패: ${e.message}</div>`; }
}

// ===== Payment =====
async function goPayment(amount, method) {
    if (!amount || amount < 1000) { showToast('1,000원 이상 입력하세요', 'error'); return; }
    try {
        const res = await apiFetch('/payment/prepare', {
            method: 'POST',
            body: JSON.stringify({amount, goodsName: `크레딧 ${amount.toLocaleString()}원 충전`, method, type: 'credit_charge'})
        });
        if (res.checkoutUrl) {
            window.open(res.checkoutUrl, 'payment', 'width=500,height=700');
            showToast('결제 창이 열렸습니다');
        } else { showToast(res.error || '결제 준비 실패', 'error'); }
    } catch(e) { showToast(e.message, 'error'); }
}

// ===== Admin Templates =====
async function showAdminTemplates() {
    document.getElementById('adminTemplatesModal').classList.add('show');
    const listEl = document.getElementById('adminTemplatesList');
    listEl.innerHTML = '<div class="empty-hint">로딩 중...</div>';
    try {
        const data = await apiFetch('/user/admin-templates');
        const categories = data.data || [];
        if (categories.length === 0) {
            listEl.innerHTML = '<div class="empty-hint">제공 템플릿이 없습니다</div>';
            return;
        }
        listEl.innerHTML = categories.map(cat => {
            const templates = cat.templates || [];
            return `
                <div style="margin-bottom:16px;">
                    <div style="font-weight:700;font-size:14px;margin-bottom:8px;">${cat.icon || '📋'} ${esc(cat.name)}</div>
                    ${templates.length === 0 ? '<div class="empty-hint">템플릿이 없습니다</div>' :
                    templates.map(t => `
                        <div class="template-card" style="margin-bottom:8px;">
                            <div style="font-weight:600;font-size:13px;margin-bottom:4px;">${esc(t.title)}</div>
                            <div style="font-size:12px;color:var(--text2);white-space:pre-wrap;max-height:60px;overflow:hidden;margin-bottom:8px;">${esc(t.content)}</div>
                            <div style="display:flex;gap:6px;">
                                <button class="btn btn-sm btn-primary" onclick="useAdminTemplate('${esc(t.content.replace(/'/g,"\\'").replace(/\n/g,"\\n"))}')">사용하기</button>
                                <button class="btn btn-sm btn-secondary" onclick="saveAdminTemplateToMine('${esc(t.title.replace(/'/g,"\\'"))}','${esc(t.content.replace(/'/g,"\\'").replace(/\n/g,"\\n"))}')">내 템플릿 저장</button>
                            </div>
                        </div>
                    `).join('')}
                </div>`;
        }).join('');
    } catch(e) {
        listEl.innerHTML = `<div class="empty-hint" style="color:var(--red);">로딩 실패: ${e.message}</div>`;
    }
}

function useAdminTemplate(content) {
    closeModal('adminTemplatesModal');
    navigate('sms');
    setTimeout(() => {
        const msgEl = document.getElementById('smsMessage');
        if (msgEl) { msgEl.value = content.replace(/\\n/g, '\n'); updateCharCount(); }
    }, 100);
}

async function saveAdminTemplateToMine(title, content) {
    try {
        await apiFetch('/user/templates', {method:'POST', body:JSON.stringify({title, content: content.replace(/\\n/g, '\n')})});
        showToast('내 템플릿에 저장되었습니다');
        await loadTemplatesCache();
    } catch(e) { showToast(e.message, 'error'); }
}

// ===== Image Upload =====
function uploadImage(callback) {
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
                const res = await apiFetch('/user/upload/image', {
                    method: 'POST',
                    body: JSON.stringify({ data: reader.result, fileName: file.name })
                });
                callback(res);
            } catch(e) { showToast('이미지 업로드 실패: ' + e.message, 'error'); }
        };
        reader.readAsDataURL(file);
    };
    input.click();
}

function handleSmsImageUpload() {
    uploadImage((res) => {
        // Insert short link into message textarea
        const textarea = document.getElementById('smsMessage');
        const currentVal = textarea.value;
        textarea.value = currentVal + (currentVal ? '\n' : '') + res.previewUrl;
        updateCharCount();

        // Show thumbnail preview
        let previewContainer = document.getElementById('imagePreviewContainer');
        if (!previewContainer) {
            previewContainer = document.createElement('div');
            previewContainer.id = 'imagePreviewContainer';
            previewContainer.className = 'image-preview-container';
            textarea.parentNode.insertBefore(previewContainer, textarea.nextSibling);
        }
        const thumb = document.createElement('div');
        thumb.className = 'image-thumb';
        thumb.innerHTML = `<img src="${res.publicUrl}" onclick="showImageFullscreen('${res.publicUrl}')"><span class="image-thumb-remove" onclick="event.stopPropagation();this.parentNode.remove()">&times;</span>`;
        previewContainer.appendChild(thumb);

        showToast('이미지 업로드 완료');
    });
}

function handleTemplateImageUpload() {
    uploadImage((res) => {
        const textarea = document.getElementById('tplContent');
        const currentVal = textarea.value;
        textarea.value = currentVal + (currentVal ? '\n' : '') + res.previewUrl;
        showToast('이미지 업로드 완료');
    });
}

// ===== Category Dropdown (inline change) =====
function showCategoryDropdown(event, contactId, currentCat) {
    event.stopPropagation();
    const existing = document.getElementById('catDropdown');
    if (existing) existing.remove();

    const contactCats = allContacts.map(c => c.category).filter(Boolean);
    const userCats = allCategories.map(c => c.name).filter(Boolean);
    const cats = [...new Set([...contactCats, ...userCats])].sort();

    const dropdown = document.createElement('div');
    dropdown.id = 'catDropdown';
    dropdown.style.cssText = 'position:fixed;background:#fff;border:1px solid var(--border);border-radius:8px;box-shadow:0 4px 16px rgba(0,0,0,0.15);z-index:9999;min-width:140px;padding:4px 0;max-height:240px;overflow-y:auto;';
    const rect = event.target.getBoundingClientRect();
    dropdown.style.left = rect.left + 'px';
    dropdown.style.top = (rect.bottom + 4) + 'px';

    let html = `<div style="padding:6px 12px;font-size:11px;color:var(--text3);border-bottom:1px solid var(--border);">카테고리 변경</div>`;
    html += `<div style="padding:8px 12px;cursor:pointer;font-size:13px;${!currentCat?'color:var(--accent);font-weight:600;':''}" onmouseover="this.style.background='var(--bg)'" onmouseout="this.style.background=''" onclick="changeCategoryTo('${contactId}','')">미지정</div>`;
    cats.forEach(cat => {
        const active = cat === currentCat;
        html += `<div style="padding:8px 12px;cursor:pointer;font-size:13px;${active?'color:var(--accent);font-weight:600;':''}" onmouseover="this.style.background='var(--bg)'" onmouseout="this.style.background=''" onclick="changeCategoryTo('${contactId}','${esc(cat)}')">${esc(cat)}</div>`;
    });
    dropdown.innerHTML = html;
    document.body.appendChild(dropdown);

    setTimeout(() => {
        document.addEventListener('click', function closeDrop() {
            const d = document.getElementById('catDropdown');
            if (d) d.remove();
            document.removeEventListener('click', closeDrop);
        });
    }, 10);
}

async function changeCategoryTo(contactId, category) {
    const d = document.getElementById('catDropdown');
    if (d) d.remove();
    try {
        await apiFetch(`/user/contacts/${contactId}`, {method:'PUT', body:JSON.stringify({category})});
        showToast('카테고리 변경됨');
        loadContacts(contactPage);
        loadContactsCache();
    } catch(e) { showToast(e.message, 'error'); }
}

// ===== Contact Detail =====
async function showContactDetail(contactId) {
    let modal = document.getElementById('contactDetailModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'contactDetailModal';
        modal.className = 'modal-overlay';
        document.body.appendChild(modal);
    }
    modal.innerHTML = `
        <div class="modal-box" style="width:600px;max-height:85vh;overflow-y:auto;">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
                <div class="card-title" style="margin:0">고객 상세 정보</div>
                <button class="btn btn-sm btn-secondary" onclick="closeModal('contactDetailModal')">닫기</button>
            </div>
            <div id="contactDetailContent"><div class="empty-hint">로딩 중...</div></div>
        </div>`;
    modal.classList.add('show');

    try {
        let detail;
        try {
            detail = await apiFetch(`/user/contacts/${contactId}/detail`);
        } catch(e) {
            const contact = allContacts.find(c => String(c.id) === String(contactId));
            if (!contact) throw new Error('연락처를 찾을 수 없습니다');
            detail = { contact, messages: [], stats: {total:0, sent:0, received:0} };
        }

        const c = detail.contact || detail;
        const messages = detail.messages || [];
        const stats = detail.stats || {total:0, sent:0, received:0};
        const cJson = JSON.stringify(c).replace(/\\/g,'\\\\').replace(/'/g,"\\'");

        document.getElementById('contactDetailContent').innerHTML = `
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:16px;">
                <div class="card" style="margin:0;">
                    <div style="font-size:20px;font-weight:700;margin-bottom:12px;">${esc(c.name)}</div>
                    <div style="font-size:13px;line-height:2;">
                        <div><strong>전화번호:</strong> ${c.phone || '-'}</div>
                        <div><strong>이메일:</strong> ${c.email || '-'}</div>
                        <div><strong>회사:</strong> ${c.company || '-'}</div>
                        <div><strong>카테고리:</strong> ${c.category ? '<span class="badge badge-phone">'+esc(c.category)+'</span>' : '미지정'}</div>
                        ${c.birthday ? '<div><strong>생일:</strong> ' + esc(c.birthday) + '</div>' : ''}
                        ${c.anniversary ? '<div><strong>기념일:</strong> ' + esc(c.anniversary) + '</div>' : ''}
                    </div>
                </div>
                <div class="card" style="margin:0;">
                    <div class="card-title">통계</div>
                    <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:8px;text-align:center;">
                        <div><div style="font-size:24px;font-weight:700;color:var(--accent);">${stats.total || 0}</div><div style="font-size:11px;color:var(--text3);">전체</div></div>
                        <div><div style="font-size:24px;font-weight:700;color:var(--green,#22c55e);">${stats.sent || 0}</div><div style="font-size:11px;color:var(--text3);">발송</div></div>
                        <div><div style="font-size:24px;font-weight:700;color:var(--blue,#3b82f6);">${stats.received || 0}</div><div style="font-size:11px;color:var(--text3);">수신</div></div>
                    </div>
                </div>
            </div>

            <div class="card" style="margin:0;margin-bottom:16px;">
                <div class="card-title">메모 / 노트</div>
                <textarea class="input" id="detailMemo" style="min-height:60px;">${esc(c.memo || c.notes || '')}</textarea>
                <button class="btn btn-sm btn-primary" onclick="saveDetailMemo('${contactId}')">메모 저장</button>
            </div>

            <div class="card" style="margin:0;">
                <div class="card-title">최근 메시지</div>
                ${messages.length === 0 ? '<div class="empty-hint">메시지 이력이 없습니다</div>' :
                '<div style="max-height:360px;overflow-y:auto;padding:12px 8px;background:var(--bg);border-radius:8px;">' +
                messages.slice().reverse().map(m => {
                    const isSent = (m.type || m.status || 'sent') === 'sent';
                    const ts = m.timestamp || m.createdAt;
                    const timeStr = ts ? new Date(Number(ts)).toLocaleString('ko-KR',{month:'short',day:'numeric',hour:'2-digit',minute:'2-digit'}) : '';
                    const msgText = m.body || m.message || '';
                    return '<div style="display:flex;flex-direction:column;align-items:' + (isSent ? 'flex-end' : 'flex-start') + ';margin-bottom:10px;">' +
                        '<div style="max-width:75%;padding:10px 14px;border-radius:' + (isSent ? '14px 14px 4px 14px' : '14px 14px 14px 4px') + ';background:' + (isSent ? 'var(--accent,#3b82f6)' : 'var(--surface,#e5e7eb)') + ';color:' + (isSent ? '#fff' : 'var(--text1)') + ';font-size:13px;white-space:pre-wrap;word-break:break-word;">' + esc(msgText) + '</div>' +
                        '<div style="font-size:10px;color:var(--text3);margin-top:3px;">' + (isSent ? '발송' : '수신') + ' · ' + timeStr + '</div>' +
                    '</div>';
                }).join('') + '</div>'}
            </div>

            <div style="margin-top:12px;display:flex;gap:8px;">
                <button class="btn btn-primary" onclick="closeModal('contactDetailModal');editContact(${cJson})">수정하기</button>
                <button class="btn btn-secondary" onclick="closeModal('contactDetailModal');navigate('sms');setTimeout(function(){selectedRecipients=[{phone:'${c.phone}',name:'${esc(c.name)}'}];renderRecipients();updateSendInfo();},100);">문자 보내기</button>
            </div>`;
    } catch(e) {
        document.getElementById('contactDetailContent').innerHTML = '<div class="empty-hint" style="color:var(--red);">로딩 실패: '+e.message+'</div>';
    }
}

async function saveDetailMemo(contactId) {
    const memo = document.getElementById('detailMemo').value;
    try {
        await apiFetch(`/user/contacts/${contactId}`, {method:'PUT', body:JSON.stringify({memo})});
        showToast('메모 저장됨');
        loadContactsCache();
    } catch(e) { showToast(e.message, 'error'); }
}

// ===== Image Fullscreen =====
function showImageFullscreen(url) {
    let overlay = document.getElementById('imageFullscreenOverlay');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'imageFullscreenOverlay';
        overlay.className = 'image-fullscreen-overlay';
        overlay.onclick = function() { this.classList.remove('show'); };
        document.body.appendChild(overlay);
    }
    overlay.innerHTML = `<img src="${url}" onclick="event.stopPropagation()">`;
    overlay.classList.add('show');
}

// ===== Helpers =====
function closeModal(id) { document.getElementById(id).classList.remove('show'); }
function esc(s) { return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
function showToast(msg, type='success') {
    const c = document.getElementById('toastContainer');
    const t = document.createElement('div');
    t.className = `toast toast-${type}`;
    t.textContent = msg; c.appendChild(t);
    setTimeout(() => t.remove(), 4000);
}

// ===== Start =====
document.addEventListener('DOMContentLoaded', () => {
    if (token) showApp(); else {
        document.getElementById('loginPage').style.display = 'flex';
        document.getElementById('appPage').style.display = 'none';
    }
});
