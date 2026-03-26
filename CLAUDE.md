# BizConnect V2 - Claude Code 규칙

## 핵심 원칙
1. **근본적 해결**: 임시방편(workaround) 금지. 항상 근본 원인을 찾아 구조적으로 해결
2. **완벽한 테스트**: 기능 구현 후 반드시 테스트 검증. 빠뜨리지 말 것
3. **보안 우선**: API 키 하드코딩 금지, 토큰 자동 갱신, 암호화 저장

## 기술 규칙
- JWT 토큰: 만료 시 Refresh Token으로 자동 갱신 (사용자 재로그인 방지)
- API 응답: mapOf에 Int/Long과 String 혼합 금지 → 전부 String 또는 buildJsonObject 사용
- SMS 발송: Room DB 저장은 시스템 Provider 저장 후에 (중복 방지)
- 보안 키: .env 파일로 분리, 코드에 절대 하드코딩 금지

## 서버 정보
- 회사: (주)다인온
- 도메인: sm.on1.kr
- 관리자: sm.on1.kr/mgmt-dainon
- S3 버킷: bizconnect-uploads (ap-northeast-2)
- SMS 프록시: 3.37.75.111 (Wideshot API)
- 발신번호: 01097804669 (등록제)
