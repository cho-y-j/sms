#!/bin/bash
# SSL 인증서 초기 발급 스크립트
# 서버에서 최초 1회 실행

DOMAIN="sm.on1.kr"
EMAIL="admin@on1.kr"  # Let's Encrypt 알림용 이메일 (변경 필요)

echo "=== Step 1: Nginx HTTP-only 모드로 시작 ==="

# 임시 nginx 설정 (HTTP only, certbot challenge용)
cat > nginx/nginx-init.conf << 'INITCONF'
events { worker_connections 1024; }
http {
    server {
        listen 80;
        server_name sm.on1.kr;
        location /.well-known/acme-challenge/ {
            root /var/www/certbot;
        }
        location / {
            return 200 'BizConnect Server - SSL Setup';
            add_header Content-Type text/plain;
        }
    }
}
INITCONF

# 임시 설정으로 nginx 시작
docker compose up -d postgresql redis bizconnect-api
docker run -d --name temp-nginx \
    -p 80:80 \
    -v "$(pwd)/nginx/nginx-init.conf:/etc/nginx/nginx.conf:ro" \
    -v "bizconnect-v2_certbot-www:/var/www/certbot" \
    nginx:alpine

echo "=== Step 2: Let's Encrypt 인증서 발급 ==="
docker run --rm \
    -v "bizconnect-v2_certbot-etc:/etc/letsencrypt" \
    -v "bizconnect-v2_certbot-var:/var/lib/letsencrypt" \
    -v "bizconnect-v2_certbot-www:/var/www/certbot" \
    certbot/certbot certonly \
    --webroot \
    --webroot-path=/var/www/certbot \
    --email "$EMAIL" \
    --agree-tos \
    --no-eff-email \
    -d "$DOMAIN"

echo "=== Step 3: 임시 nginx 제거 ==="
docker stop temp-nginx && docker rm temp-nginx
rm nginx/nginx-init.conf

echo "=== Step 4: 전체 서비스 시작 (HTTPS) ==="
docker compose up -d

echo "=== 완료! ==="
echo "https://$DOMAIN 접속 확인"
echo "https://$DOMAIN/health 헬스체크"
echo "https://$DOMAIN/admin 관리자 패널"
