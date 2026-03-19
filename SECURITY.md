2# BizConnect V2 Security Architecture

## Overview

BizConnect V2 implements a comprehensive defense-in-depth security architecture protecting against three critical attack vectors that compromised the previous system:

1. **API Key Theft**
2. **SQL Injection**
3. **Brute Force Attacks**

## Attack Vector 1: API Key Theft Prevention

### Implementation

#### 1. Dual Authentication Layer
- **JWT Tokens**: Short-lived access tokens (15 minutes) + long-lived refresh tokens (7 days, one-time use)
- **API Keys**: Separate credentials for programmatic access with per-key rate limits

```kotlin
// JWT: Access token valid for 15 minutes only
val tokenPair = jwtManager.createTokenPair(userId, email)

// Refresh tokens enforced as one-time use
// After use, old refresh token is blacklisted
val newTokenPair = jwtManager.refreshAccessToken(oldRefreshToken)
```

#### 2. API Key Security
- **Generation**: 256-bit random keys using `SecureRandom`
- **Storage**: BCrypt hashed before database storage (NEVER store plaintext)
- **Rotation**: Old keys remain valid for 24 hours after rotation
- **Revocation**: Immediate API key blacklist on compromise

```kotlin
val apiKey = apiKeyManager.generateApiKey() // 256-bit random
val hash = apiKeyManager.hashApiKey(apiKey) // Store this, not the key
```

#### 3. Token Blacklisting
- All tokens blacklisted on logout
- Refresh token reuse detected and triggers full user token blacklist
- Token expiry checked at validation time

#### 4. Secure Key Management
- All secrets loaded from environment variables, NEVER hardcoded
- JWT_SECRET: 256-bit random (set via env var)
- ENCRYPTION_KEY: 32-byte Base64 encoded (set via env var)

**Deployment Checklist**:
```bash
# Generate secrets (run once, store securely)
JWT_SECRET=$(openssl rand -base64 32)
ENCRYPTION_KEY=$(openssl rand -base64 32)

# Add to .env (never commit to git)
echo "JWT_SECRET=$JWT_SECRET" >> .env
echo "ENCRYPTION_KEY=$ENCRYPTION_KEY" >> .env
```

## Attack Vector 2: SQL Injection Prevention

### Implementation

#### 1. Parameterized Queries (Exposed ORM)
All database access uses Jetbrains Exposed ORM with automatic parameterization:

```kotlin
// SAFE: All parameters are parameterized
UsersTable.select {
    UsersTable.email eq email  // Parameterized!
}.firstOrNull()

// NEVER write raw SQL
```

#### 2. Input Validation
Strict input validation on all user-provided data:

```kotlin
// Phone: Only Korean format allowed
InputValidator.validatePhoneNumber(phone)  // Throws if invalid

// Email: RFC 5322 pattern validation
InputValidator.validateEmail(email)

// Message: Length + content sanitization
InputValidator.validateMessageContent(message, maxLength = 1000)
```

#### 3. SQL Injection Pattern Detection
Regex-based detection of common SQL injection patterns:

```kotlin
// Detects: ', UNION, SELECT, DROP, etc.
if (InputValidator.checkSqlInjection(input)) {
    auditLogger.logSuspiciousActivity(...)
    throw IllegalArgumentException("Invalid input detected")
}
```

#### 4. XSS Prevention
Message content sanitized to prevent cross-site scripting:

```kotlin
// Detects: <script>, onerror=, onclick=, etc.
if (InputValidator.checkXss(message)) {
    throw IllegalArgumentException("Invalid content")
}
```

**Query Examples**:
```kotlin
// ✓ SAFE: Parameterized
UsersTable.select { UsersTable.email eq userInput }

// ✓ SAFE: Type-safe
val page = InputValidator.validateInt(pageInput, "page", min = 1, max = 1000)
TasksTable.select { TasksTable.userId eq userId }
    .limit(limit, offset.toLong())

// ✗ NEVER: String interpolation
// query("SELECT * FROM users WHERE email = '$email'")
```

## Attack Vector 3: Brute Force Attack Prevention

### Implementation

#### 1. Rate Limiting (Token Bucket Algorithm)
Per-endpoint rate limits with automatic IP blocking:

```kotlin
// Login: 5 attempts per 15 minutes per IP
rateLimiter.checkIPLimit("login", ipAddress)

// SMS: 500 per day per user
rateLimiter.checkUserLimit("sms_send", userId)

// Registration: 3 per hour per IP
rateLimiter.checkIPLimit("register", ipAddress)

// API calls: 100 per minute per key
rateLimiter.checkApiKeyLimit(apiKeyId, limitPerMinute = 100)
```

#### 2. Account Lockout
After 5 failed login attempts, account locked for 30 minutes:

```kotlin
// Failed attempt increments counter
loginAttempts = 5  // Lock triggered
lockedUntil = System.currentTimeMillis() + (30 * 60 * 1000)

// Account checked during next login attempt
if (passwordManager.isAccountLocked(lockedUntil)) {
    throw UnauthorizedException("Account is locked")
}
```

#### 3. IP Blacklisting
Automatic IP blocking after repeated violations:

```kotlin
// Too many login failures
if (!rateLimitResult.allowed) {
    ipManager.blacklistIp(
        ipAddress = ipAddress,
        reason = "Brute force login attempts",
        durationMs = 60 * 60 * 1000L  // 1 hour
    )
}

// Subsequent requests from blocked IP rejected immediately
if (ipManager.isIpBlacklisted(ipAddress)) {
    throw ForbiddenException("IP address is blocked")
}
```

#### 4. Password Strength Requirements
Enforced at registration and password change:

- Minimum 8 characters
- At least 1 uppercase letter
- At least 1 lowercase letter
- At least 1 number
- At least 1 special character (!@#$%^&*...)
- NOT in common passwords list

#### 5. BCrypt Password Hashing
Cost factor 12 (computationally expensive to prevent GPU attacks):

```kotlin
val hash = passwordManager.hashPassword(password)
// Cost: ~100ms per hash verification - slows brute force attacks
```

## Audit Logging

All security events logged for forensic analysis:

```kotlin
auditLogger.logLoginAttempt(userId, success, ipAddress, userAgent)
auditLogger.logSmsSend(userId, phone, messageId, charCount, ipAddress, userAgent)
auditLogger.logRateLimitViolation(userId, endpoint, ipAddress, userAgent)
auditLogger.logSuspiciousActivity(userId, activity, ipAddress, userAgent)
```

Logs include:
- Timestamp
- User ID
- Action type
- IP address
- User agent
- Severity level

## Environment-Based Configuration

### .env Variables (Required)

```
# Database
DB_HOST=postgresql
DB_PORT=5432
DB_NAME=bizconnect
DB_USER=bizconnect_user
DB_PASSWORD=<STRONG_PASSWORD>

# JWT (GENERATE NEW VALUES FOR PRODUCTION)
JWT_SECRET=<256-BIT-RANDOM>
JWT_ACCESS_EXPIRY_MINUTES=15
JWT_REFRESH_EXPIRY_DAYS=7

# Encryption
ENCRYPTION_KEY=<32-BYTES-BASE64>

# Redis
REDIS_HOST=redis
REDIS_PASSWORD=<STRONG_PASSWORD>

# CORS
ALLOWED_ORIGINS=https://yourdomain.com

# Firebase (optional, for FCM)
FIREBASE_PROJECT_ID=your-project-id
```

### Deployment Security

#### Docker Security
- Non-root user execution (UID 1000)
- No new privileges allowed
- Minimal capability set (NET_BIND_SERVICE only)
- Security options enabled

#### Network Security
- All services on private network (172.20.0.0/16)
- Database not exposed to host network
- Redis requires password authentication
- HTTPS required in production (via reverse proxy)

#### TLS/HTTPS Configuration (Reverse Proxy)
```nginx
upstream bizconnect {
    server bizconnect-api:8080;
}

server {
    listen 443 ssl http2;
    server_name api.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-XSS-Protection "1; mode=block" always;

    proxy_pass http://bizconnect;
}
```

## Security Best Practices for Operators

### 1. Secret Rotation
```bash
# Change secrets quarterly
JWT_SECRET=$(openssl rand -base64 32)
# Update in deployment system
# Restart API server

# Users with active sessions must re-authenticate
```

### 2. Monitoring
```bash
# Check audit logs for suspicious activity
SELECT * FROM audit_logs
WHERE severity IN ('WARNING', 'CRITICAL')
ORDER BY timestamp DESC;

# Monitor blocked IPs
SELECT * FROM ip_blacklist
WHERE expires_at IS NULL OR expires_at > NOW();
```

### 3. Backup and Recovery
```bash
# Daily database backups
pg_dump -h postgresql -U bizconnect_user bizconnect | gzip > backup-$(date +%Y%m%d).sql.gz

# Store offsite securely
```

### 4. Intrusion Detection
Monitor for:
- Multiple failed login attempts from single IP
- SQL injection patterns in logs
- XSS attempts in message content
- Unusual API key usage patterns
- High rate limit violations

## Remediation Procedures

### API Key Compromised
1. Revoke immediately: `apiKeyManager.revokeApiKey(keyId)`
2. Issue new key
3. Notify user via email
4. Audit all API calls from key since last login

### Account Compromised
1. Force password reset
2. Blacklist all current tokens
3. Alert user of suspicious activity
4. Review audit logs for unauthorized changes

### Brute Force Attack Detected
1. IP automatically blacklisted (1 hour)
2. User account locked (30 minutes)
3. Alert admin
4. Log all failed attempts for forensics

## Testing Security

### Manual Testing
```bash
# Test rate limiting
for i in {1..10}; do
    curl -X POST http://localhost:8080/api/auth/login \
        -H "Content-Type: application/json" \
        -d '{"email":"test@example.com","password":"wrong"}'
done
# Should be blocked after 5 attempts

# Test SQL injection detection
curl -X POST http://localhost:8080/api/auth/register \
    -H "Content-Type: application/json" \
    -d '{"email":"test' OR 1=1--@example.com","password":"Test123!","name":"Test","phone":"01012345678"}'
# Should reject with "Invalid input detected"

# Test XSS detection
curl -X POST http://localhost:8080/api/sms/send \
    -H "Authorization: Bearer TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"phone":"01012345678","message":"<script>alert(1)</script>"}'
# Should reject with "Invalid content"
```

## Incident Response

### Report Security Issues
**DO NOT** open public issues for security vulnerabilities.

Email: security@bizconnect.com
Include:
- Description of vulnerability
- Steps to reproduce
- Potential impact
- Your contact information

Expected response: 24 hours

## Compliance

- OWASP Top 10 protections implemented
- GDPR-ready (data encryption, right to deletion)
- SOC 2 security controls baseline
- PCI-DSS applicable controls (no payment processing)

---

**Last Updated**: March 2026
**Version**: 2.0.0
