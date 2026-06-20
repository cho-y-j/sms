# BizConnect V2 — Google Play 심사 통과 가이드

> 목적: 제한 권한(SMS·통화기록)을 가진 **기본 SMS 앱**으로 Google Play 심사를 통과시키기 위한
> 권한 명세서 문구·개인정보처리방침·데모영상 시나리오·제출 체크리스트.
> 코드 측 대응은 완료됨(아래 "코드 측 완료 사항" 참고). Play Console 제출은 사장님이 진행.

---

## 0. 제품 정체성 (심사관에게 각인시킬 한 문장)
> **"영업 현장의 1인 사용자가 자신의 휴대폰을 기본 메시지 앱으로 쓰면서, 부재중/통화 종료 시 상대에게
> 본인 명함·서류를 자동으로 회신(콜백)하고, 자주 보내는 서류를 템플릿으로 빠르게 보내는 개인 메시징 앱."**

대량/마케팅 발송 도구가 **아님**을 명확히. (공개 빌드에서 대량발송 메뉴 숨김 처리 완료)

---

## 1. 코드 측 완료 사항 (이번 작업)
- ✅ **기본 SMS 앱 전환 온보딩**: 최초 실행 시 기본앱 설정 요청 + 대화목록 상단 배너(미설정 시) + 설정 화면 — 단일 헬퍼(`DefaultSmsApp`)로 통합.
- ✅ **수신 SMS 시스템 저장**: 수신 문자를 시스템 문자 DB(`content://sms`)에 직접 저장 → 심사관이 테스트 문자 발송 시 정상 노출.
- ✅ **명함 이미지 영속 저장**: 콜백 발송 시점에 명함이 안 붙던 버그 수정(피커 URI → 내부 저장소 복사).
- ✅ **대량발송 진입 메뉴 숨김**: 정책 거절 트리거 제거(코드는 보존).
- ✅ **미사용 권한 `READ_PHONE_NUMBERS` 제거**: 제한권한 표면 축소.

---

## 2. 권한 명세서 (Permissions Declaration Form) 문구

Play Console → 앱 콘텐츠 → **민감한 권한 및 API** → SMS/통화 기록 권한 선언에 붙여넣을 문구.

### 2-1. 핵심 기능 설명 (Core functionality)
```
본 앱은 사용자의 기본 SMS 메시지 앱(default SMS handler)입니다. 사용자는 본 앱을 통해
문자(SMS/MMS)를 직접 주고받으며, 모든 메시지는 기기 내 시스템 메시지 저장소에 저장됩니다.

추가로, 영업 직군 1인 사용자를 위해 '자동 콜백' 기능을 제공합니다. 사용자가 전화를 받지
못했거나 통화가 종료되었을 때, 상대 전화번호로 사용자가 미리 설정해 둔 인사 문구와 본인
명함/서류 이미지를 자동으로 문자(MMS)로 회신합니다. 이 기능은 사용자 본인의 기기에서,
사용자 본인이 명시적으로 켠 경우에만 동작합니다.
```

### 2-2. 권한별 정당화

| 권한 | 정당화 문구 (영문 권장) |
|---|---|
| `SEND_SMS` / `READ_SMS` / `RECEIVE_SMS` / `RECEIVE_MMS` / `RECEIVE_WAP_PUSH` | "App is the user's default SMS handler. These permissions are required to send, receive, and store SMS/MMS messages — the app's core messaging function. The app requests the default-SMS-handler role on first launch (RoleManager.ROLE_SMS)." |
| `READ_CALL_LOG` | "Used solely on-device to identify the phone number of the just-ended/missed call so the app can send the user's pre-configured auto-reply (callback) SMS to that number. The call log is never transmitted off-device and is not used for any advertising or analytics. The feature is opt-in and off by default." |
| `READ_PHONE_STATE` | "Detects call state transitions (ringing → idle) to trigger the opt-in auto-callback after a call ends." |
| `READ_CONTACTS` | "Resolves the caller's/recipient's display name for the conversation and callback message." |

> ⚠️ **`READ_CALL_LOG`가 최대 변수**입니다. SMS 핸들러 면제로 커버되지 않으므로 위 문구 + 데모영상으로
> "콜백에 반드시 필요, 기기 내에서만 사용, 옵트인"임을 분명히 해야 합니다.

---

## 3. 데모영상 시나리오 (1~2분, 필수)

심사관이 "권한이 핵심 기능에 실제로 쓰인다"를 눈으로 확인하는 자료. 화면 녹화로 촬영.

```
[0:00] 앱 첫 실행 → "BizConnect를 기본 메시지 앱으로 설정" 시스템 다이얼로그 → '설정' 선택
[0:10] 대화 목록 화면 — 기존 문자들이 보임 (기본앱으로 동작 증명)
[0:18] 다른 폰에서 이 기기로 문자 전송 → 알림 + 대화목록에 수신 문자 표시 (수신/저장 증명)
[0:30] 새 문자 작성 → 전송 → 대화에 발신 문자 표시 (발신 증명)
[0:40] [콜백 설정] 진입 → '부재중 시 자동응답' ON, 인사 문구 입력, 명함 이미지 첨부
[0:55] 다른 폰에서 이 기기로 전화 → 받지 않음(부재중)
[1:05] 잠시 후, 부재중 번호로 인사문구 + 명함이 자동 문자(MMS)로 발송됨을 화면으로 확인
        → READ_CALL_LOG / READ_PHONE_STATE 의 실제 사용처를 시각적으로 증명
[1:20] [문자 관리(템플릿)] 진입 → 사업자등록증 템플릿 선택 → 1탭으로 첨부·발송
```

---

## 4. 개인정보처리방침 체크리스트
- [ ] 공개 URL 필요 (Play Console 필수). 다음 항목 명시:
  - 수집 데이터: 문자/연락처/통화기록(번호) — **기기 내 처리 및 사용자 서버(sm.on1.kr) 동기화 시 항목 명시**
  - 사용 목적: 메시지 송수신·콜백·템플릿. **광고/제3자 판매 없음** 명시
  - 통화기록: "콜백 대상 번호 식별 용도로만, 옵트인" 명시
- [ ] 앱 내 '데이터 안전(Data safety)' 설문과 처리방침 내용 **일치** (불일치 시 거절)
- [ ] SMS 동기화는 사용자 동의(앱 내 `isSmsSyncConsented`) 후에만 — 처리방침에 반영

---

## 5. 제출 전 최종 체크리스트
- [ ] **릴리즈 서명**: `keystore.properties` 준비(`keystore.properties.example` 참고), Play App Signing 등록 키와 일치
- [ ] **타겟 SDK 35** (충족)
- [ ] **AAB로 업로드** (Android Studio: Build → Generate Signed Bundle)
- [ ] **데이터 안전 설문** 작성 (위 4번과 일치)
- [ ] **권한 명세서**(2번) 제출 + **데모영상**(3번) 링크 첨부
- [ ] 대량발송 메뉴가 공개 빌드에서 **안 보이는지** 최종 확인
- [ ] 스토어 설명에 "대량/단체/마케팅 문자" 같은 표현 **사용 금지** → "개인 영업용 메시지·자동 콜백·서류 템플릿"으로 기술

---

## 6. 만약 그래도 거절된다면 (Plan B)
거절 사유가 `READ_CALL_LOG`이면:
- **옵션 A**: 자동 콜백을 "반자동"으로 — 통화 종료 알림에서 사용자가 탭하면 콜백 발송(번호 출처를 사용자 액션으로). 단, 안드9+에서 번호 확보가 제약될 수 있음.
- **옵션 B**: 콜백 기능 유지가 절대 조건이면 **원스토어(ONE store)/기업 배포/직접 APK**로 병행 배포 (국내 영업용 앱 일반적 경로).

> 권장: 우선 Play에 위 자료로 제출 → 결과 보고 판단.
