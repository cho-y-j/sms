# BizConnect V2 Deployment Guide

## Quick Start (Development)

```bash
# Clone repository
git clone <repo-url>
cd bizconnect-v2

# Copy environment template
cp .env.example .env

# Generate secrets for development (DO NOT use in production)
echo "JWT_SECRET=$(openssl rand -base64 32)" >> .env
echo "ENCRYPTION_KEY=$(openssl rand -base64 32)" >> .env

# Start services
docker-compose up -d

# Verify health
curl http://localhost:8080/health
# Expected response: {"status":"ok","timestamp":"...","version":"2.0.0"}
```

## Production Deployment

### Prerequisites
- Docker & Docker Compose 1.29+
- PostgreSQL 16+ (or use docker-compose)
- Redis 7+ (or use docker-compose)
- 4GB RAM minimum
- 20GB disk space
- Ubuntu 22.04 LTS or similar

### Step 1: Prepare Secrets

```bash
# Generate strong secrets (save these securely)
JWT_SECRET=$(openssl rand -base64 32)
ENCRYPTION_KEY=$(openssl rand -base64 32)
DB_PASSWORD=$(openssl rand -base64 32)
REDIS_PASSWORD=$(openssl rand -base64 32)

# Store in secure vault (AWS Secrets Manager, HashiCorp Vault, etc.)
# Example with AWS:
aws secretsmanager create-secret \
    --name bizconnect/prod/jwt-secret \
    --secret-string "$JWT_SECRET"
```

### Step 2: Create Production .env

```bash
# .env (NEVER commit to git, keep secure)
ENVIRONMENT=production
SERVER_PORT=8080

DB_HOST=postgresql
DB_PORT=5432
DB_NAME=bizconnect
DB_USER=bizconnect_user
DB_PASSWORD=<GENERATED_STRONG_PASSWORD>

REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=<GENERATED_STRONG_PASSWORD>

JWT_SECRET=<GENERATED_256_BIT_SECRET>
JWT_ACCESS_EXPIRY_MINUTES=15
JWT_REFRESH_EXPIRY_DAYS=7

ENCRYPTION_KEY=<GENERATED_32_BYTE_BASE64>

FIREBASE_PROJECT_ID=your-firebase-project
FIREBASE_SERVICE_ACCOUNT_KEY=/path/to/firebase-key.json

ALLOWED_ORIGINS=https://api.yourdomain.com,https://app.yourdomain.com
```

### Step 3: Build Docker Image

```bash
# Build for production
docker build -t bizconnect-api:2.0.0 .

# Tag for registry
docker tag bizconnect-api:2.0.0 registry.yourdomain.com/bizconnect-api:2.0.0

# Push to registry
docker push registry.yourdomain.com/bizconnect-api:2.0.0
```

### Step 4: Deploy with Docker Compose

```bash
# Pull latest images
docker-compose pull

# Start services
docker-compose -f docker-compose.yml up -d

# Verify startup
docker-compose logs -f bizconnect-api

# Check health
curl http://localhost:8080/health
```

### Step 5: Configure Reverse Proxy (Nginx)

```nginx
# /etc/nginx/sites-available/bizconnect.conf

upstream bizconnect {
    server localhost:8080;
    keepalive 32;
}

# HTTP redirect to HTTPS
server {
    listen 80;
    server_name api.yourdomain.com;
    return 301 https://$server_name$request_uri;
}

# HTTPS with TLS 1.3
server {
    listen 443 ssl http2;
    server_name api.yourdomain.com;

    # SSL certificate (use Let's Encrypt)
    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    # Strong SSL configuration
    ssl_protocols TLSv1.3 TLSv1.2;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;

    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
    add_header Content-Security-Policy "default-src 'none'; script-src 'self'; style-src 'self'" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "no-referrer" always;

    # Rate limiting (optional, in addition to app-level)
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=100r/m;
    limit_req zone=api_limit burst=200 nodelay;

    # Proxy settings
    proxy_pass http://bizconnect;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 60s;
    proxy_send_timeout 60s;
    proxy_read_timeout 60s;
}
```

Enable Nginx configuration:
```bash
sudo ln -s /etc/nginx/sites-available/bizconnect.conf /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### Step 6: Set Up SSL with Let's Encrypt

```bash
sudo apt-get install certbot python3-certbot-nginx

# Obtain certificate
sudo certbot certonly --nginx -d api.yourdomain.com

# Auto-renewal (certbot auto-renews)
sudo systemctl enable certbot.timer
```

### Step 7: Database Initialization

```bash
# Backup script
#!/bin/bash
BACKUP_DIR="/backups/bizconnect"
DATE=$(date +%Y%m%d_%H%M%S)

mkdir -p $BACKUP_DIR

docker exec bizconnect-db pg_dump -U bizconnect_user bizconnect | \
    gzip > $BACKUP_DIR/backup_$DATE.sql.gz

# Keep last 30 days
find $BACKUP_DIR -name "backup_*.sql.gz" -mtime +30 -delete

echo "Backup completed: $BACKUP_DIR/backup_$DATE.sql.gz"
```

Schedule via cron:
```bash
# Backup daily at 2 AM
0 2 * * * /usr/local/bin/backup-bizconnect.sh
```

### Step 8: Monitoring & Logging

```bash
# View logs
docker-compose logs -f bizconnect-api

# Log rotation
cat > /etc/docker/daemon.json << EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF

# Restart Docker
sudo systemctl restart docker
```

### Step 9: Health Checks

```bash
# Basic health check
curl -i http://localhost:8080/health

# Response should be:
# HTTP/1.1 200 OK
# {"status":"ok","timestamp":"2026-03-17T...","version":"2.0.0"}

# Application health checks
docker-compose ps
# All services should show "healthy" status
```

## Scaling (Production)

### Load Balancing

For multiple instances:

```yaml
# docker-compose-prod.yml
version: '3.9'

services:
  bizconnect-api-1:
    extends:
      file: docker-compose.yml
      service: bizconnect-api
    container_name: bizconnect-api-1

  bizconnect-api-2:
    extends:
      file: docker-compose.yml
      service: bizconnect-api
    container_name: bizconnect-api-2

  nginx:
    image: nginx:latest
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - bizconnect-api-1
      - bizconnect-api-2
```

### Redis Clustering (Production)

Replace in-memory rate limiter with Redis:

```kotlin
// Replace InMemoryTokenBlacklist with RedisTokenBlacklist
// Replace RateLimiter with RedisRateLimiter
val redis = RedisClient(
    host = System.getenv("REDIS_HOST") ?: "localhost",
    port = System.getenv("REDIS_PORT")?.toInt() ?: 6379,
    password = System.getenv("REDIS_PASSWORD")
)
```

## Maintenance

### Database Maintenance

```bash
# Analyze query performance
docker exec bizconnect-db psql -U bizconnect_user -d bizconnect -c "ANALYZE;"

# Vacuum (cleanup dead rows)
docker exec bizconnect-db psql -U bizconnect_user -d bizconnect -c "VACUUM ANALYZE;"

# Index maintenance
docker exec bizconnect-db psql -U bizconnect_user -d bizconnect -c "REINDEX DATABASE bizconnect;"
```

### Upgrade Process

```bash
# 1. Backup database
docker exec bizconnect-db pg_dump -U bizconnect_user bizconnect | gzip > backup.sql.gz

# 2. Pull new version
docker-compose pull

# 3. Stop services gracefully
docker-compose down --timeout=30

# 4. Start new version
docker-compose up -d

# 5. Monitor logs
docker-compose logs -f bizconnect-api

# 6. Verify health
curl http://localhost:8080/health
```

### Rollback Procedure

```bash
# 1. Stop current version
docker-compose down --timeout=30

# 2. Restore database from backup
gunzip < backup.sql.gz | docker exec -i bizconnect-db \
    psql -U bizconnect_user bizconnect

# 3. Start previous version
docker-compose up -d
```

## Troubleshooting

### Container won't start

```bash
# Check logs
docker-compose logs bizconnect-api

# Common issues:
# - Database not ready: Check postgresql health
# - Missing env vars: Verify .env file
# - Port already in use: Check "docker ps"
```

### Slow queries

```bash
# Enable query logging
docker exec bizconnect-db psql -U bizconnect_user -d bizconnect << EOF
ALTER SYSTEM SET log_statement = 'all';
ALTER SYSTEM SET log_min_duration_statement = 1000;
SELECT pg_reload_conf();
EOF

# View slow query logs
docker exec bizconnect-db tail -f /var/log/postgresql/postgresql.log
```

### Memory issues

```bash
# Check resource usage
docker stats

# Increase JVM heap
# Edit Dockerfile:
# ENTRYPOINT ["java", "-Xmx2g", "-Xms512m", "-jar", "server.jar"]
```

## Security Checklist

- [ ] All secrets in .env (not in git)
- [ ] HTTPS enabled with valid certificate
- [ ] Database password changed from default
- [ ] Redis password changed from default
- [ ] JWT_SECRET is 256-bit random
- [ ] ENCRYPTION_KEY is 32-byte Base64
- [ ] ALLOWED_ORIGINS set correctly
- [ ] Firewall rules configured (ports 80, 443 only)
- [ ] Regular backups scheduled
- [ ] Audit logs monitored
- [ ] Rate limiting working (test with load)
- [ ] Docker runs as non-root user
- [ ] Security headers enabled (Nginx)
- [ ] TLS 1.3 enforced

## Support

For issues:
1. Check logs: `docker-compose logs bizconnect-api`
2. Review SECURITY.md for attack prevention
3. Check health endpoint: `curl http://localhost:8080/health`
4. Contact: support@bizconnect.com

---

**Last Updated**: March 2026
**Version**: 2.0.0
