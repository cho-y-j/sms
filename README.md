# BizConnect V2 - Default SMS App for Android

A complete, production-ready Android SMS application built with Kotlin and Jetpack Compose that registers as the default messaging app on Android. Includes a backend server built with Ktor.

## Project Structure

```
bizconnect-v2/
├── app/                          # Android application module
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/bizconnect/v2/
│       │   │   ├── app/                  # Application & Activities
│       │   │   ├── receiver/             # Broadcast Receivers
│       │   │   ├── service/              # Services
│       │   │   ├── di/                   # Dependency Injection
│       │   │   ├── data/                 # Data layer (DB, preferences)
│       │   │   ├── util/                 # Utilities
│       │   │   └── ui/                   # UI layer (Compose screens)
│       │   └── res/
│       │       ├── values/               # Strings, colors, themes
│       │       ├── values-night/         # Dark theme
│       │       ├── drawable/             # Vector drawables
│       │       └── xml/                  # Network config, file paths
│       └── test/
├── server/                       # Ktor backend server
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/bizconnect/server/
│       ├── plugins/              # Ktor plugins (auth, CORS, etc)
│       ├── routes/               # API endpoints
│       ├── models/               # Data transfer objects
│       ├── database/             # Exposed ORM
│       └── Application.kt
├── gradle/libs.versions.toml    # Version catalog
├── build.gradle.kts             # Root Gradle configuration
├── settings.gradle.kts
├── gradle.properties
├── Dockerfile                   # Docker image for server
├── docker-compose.yml           # Docker Compose setup
└── .gitignore
```

## Key Features

### Android App (com.bizconnect.v2)

- **Default SMS App Registration**: Fully implements the 4 required components for Android default SMS app status
  - MainActivity with SMS send-to intent filters
  - SmsReceiver for SMS_DELIVER broadcasts
  - MmsReceiver for MMS WAP_PUSH broadcasts
  - HeadlessSmsSendService for quick replies

- **Broadcast Receivers**:
  - `SmsReceiver`: Handles incoming SMS, saves to database, shows notifications
  - `MmsReceiver`: Processes incoming MMS messages
  - `BootReceiver`: Restarts services after device boot
  - `CallStateReceiver`: Detects incoming calls for callback feature
  - `AlarmReceiver`: Triggers scheduled message sending

- **Services**:
  - `SmsSendService`: Foreground service for bulk SMS/LMS/MMS sending
  - `CallbackService`: Auto-callback feature after calls
  - `SyncService`: Background data synchronization
  - `HeadlessSmsSendService`: Quick reply from notifications

- **Database**: Room with SQLite
  - SMS message entity and DAO
  - Full message history tracking

- **UI**: Jetpack Compose with Material 3
  - Samsung One UI 6 color palette
  - Light and dark themes
  - MainScreen with default app status detection

- **Utilities**:
  - `PhoneNumberUtil`: Korean phone number validation and formatting
  - `DateTimeUtil`: Korean locale date/time formatting
  - `PermissionUtil`: Runtime permission management
  - `NotificationUtil`: Rich notifications
  - `SecurityUtil`: Encryption and password hashing

### Backend Server (Ktor + Exposed)

- **API Endpoints**:
  - `GET /health`: Health check
  - `POST /api/v1/messages/send`: Send SMS/MMS
  - `GET /api/v1/messages/{id}`: Get message status
  - `GET /api/v1/messages`: List messages (paginated)

- **Database**: PostgreSQL with Exposed ORM
  - Users table with phone and email
  - SMS Messages table with status tracking

- **Security**:
  - JWT authentication
  - CORS configuration
  - Rate limiting support
  - Password hashing with BCrypt

- **Infrastructure**:
  - Ktor server framework
  - HikariCP connection pooling
  - Structured logging

## Dependencies

### Core
- Kotlin 2.0.21
- Android SDK 35 (compileSdk)
- Java 17

### Android
- Jetpack Compose 2024.12.01
- Room 2.6.1
- Hilt 2.51.1
- Retrofit 2.11.0
- OkHttp 4.12.0
- Firebase Cloud Messaging
- Coil image loading
- DataStore preferences

### Backend
- Ktor 2.3.12
- Exposed ORM 0.56.0
- PostgreSQL JDBC 42.7.3
- HikariCP 5.1.0
- JWT auth0 4.4.0
- BCrypt 0.10.1

## Getting Started

### Prerequisites

- Android Studio Koala or later
- Gradle 8.7.3
- JDK 17
- Docker and Docker Compose (for backend)

### Build Android App

```bash
cd bizconnect-v2
./gradlew :app:build
```

### Run Android Tests

```bash
./gradlew :app:test
./gradlew :app:connectedAndroidTest
```

### Build Backend

```bash
./gradlew :server:build
```

### Run Backend with Docker

```bash
docker-compose up -d
```

This starts:
- PostgreSQL database on port 5432
- Redis cache on port 6379
- Ktor API server on port 8080

### Environment Variables

**Android App** (BuildConfig):
- `API_BASE_URL`: API endpoint (debug: localhost:8080, release: api.bizconnect.com)

**Backend Server**:
- `PORT`: Server port (default: 8080)
- `ENVIRONMENT`: production or development
- `DB_URL`: PostgreSQL connection (default: jdbc:postgresql://db:5432/bizconnect)
- `DB_USER`: Database user (default: bizconnect)
- `DB_PASSWORD`: Database password
- `JWT_SECRET`: JWT signing key (required: change in production!)
- `JWT_ISSUER`: JWT issuer (default: https://bizconnect.com)
- `JWT_AUDIENCE`: JWT audience (default: bizconnect-api)

## Android Manifest Highlights

The app registers as a default SMS app with these key components:

```xml
<!-- Main Activity with SMS intent filters -->
<activity android:name=".app.MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <action android:name="android.intent.action.SENDTO" />
        <data android:scheme="sms" />
        <data android:scheme="mms" />
    </intent-filter>
</activity>

<!-- SMS Receive Receiver (priority 999) -->
<receiver android:name=".receiver.SmsReceiver">
    <intent-filter android:priority="999">
        <action android:name="android.provider.Telephony.SMS_DELIVER" />
    </intent-filter>
</receiver>

<!-- MMS Receive Receiver -->
<receiver android:name=".receiver.MmsReceiver">
    <intent-filter>
        <action android:name="android.provider.Telephony.WAP_PUSH_DELIVER" />
        <data android:mimeType="application/vnd.wap.mms-message" />
    </intent-filter>
</receiver>

<!-- Headless SMS Send Service (quick reply) -->
<service android:name=".service.HeadlessSmsSendService">
    <intent-filter>
        <action android:name="android.intent.action.RESPOND_VIA_MESSAGE" />
    </intent-filter>
</service>
```

## Permissions

The app requests:
- SMS: SEND_SMS, RECEIVE_SMS, READ_SMS, RECEIVE_MMS
- Phone: READ_PHONE_STATE, READ_CALL_LOG
- Contacts: READ_CONTACTS
- Notifications: POST_NOTIFICATIONS (Android 13+)
- Services: FOREGROUND_SERVICE (with types)

## Data Flow

### Incoming SMS
1. SmsReceiver catches SMS_DELIVER intent
2. Parses SMS PDU
3. Saves to Room database
4. Shows notification
5. Syncs with backend

### Outgoing SMS
1. User sends message
2. SmsSendService splits into parts
3. Uses SmsManager.sendMultipartTextMessage()
4. Saves to sent folder
5. Updates status in database
6. Reports to server

### Scheduled Messages
1. User schedules message
2. AlarmManager triggers at scheduled time
3. AlarmReceiver wakes up device
4. SmsSendService sends message
5. Status updated

## Security Features

- JWT-based API authentication
- Password hashing with BCrypt
- Encrypted sensitive data on device (Android Keystore)
- TLS/SSL for all network communication
- Input validation on all endpoints
- CORS properly configured

## Database Schema

### Users Table
- id, phoneNumber, email, passwordHash, displayName, status, timestamps

### SMS Messages Table
- id, userId, phoneNumber, messageBody, messageType, isIncoming, status
- Retry tracking (sendAttempts, maxAttempts)
- Failure reason tracking

## Testing

The project includes placeholders for:
- Unit tests (JUnit 4)
- Android instrumentation tests (Espresso)
- Compose UI tests

## Development

### IDE Setup
- Use Android Studio with Kotlin plugin
- Enable Code Inspection
- Use ktlint for formatting

### Build Variants
- **Debug**: Uses localhost API, full logging
- **Release**: Uses production API, minified code, ProGuard rules applied

### Proguard Rules
Included for:
- Retrofit models
- Room entities
- Hilt annotations
- Firebase
- Kotlin coroutines

## API Documentation

### POST /api/v1/messages/send
Send SMS/MMS message

Request:
```json
{
  "recipients": ["+82101234567", "+82109876543"],
  "messageBody": "Hello World",
  "messageType": "SMS",
  "scheduledTime": null
}
```

Response:
```json
{
  "id": 1234567890,
  "status": "PENDING",
  "message": "Message queued for sending",
  "timestamp": 1234567890000
}
```

## Performance Considerations

- Background operations use Coroutines
- Database queries optimized with Room indexes
- Notification thumbnails use Coil for lazy loading
- Message pagination (50 items per page default)
- Rate limiting on backend (Ktor rate-limit plugin)

## Troubleshooting

### App not showing as default SMS app
- Ensure all 4 required components are in manifest
- Set proper intent-filter priorities
- Check permissions are granted
- Clear app cache and reopen settings

### Database errors
- Ensure proper Room migrations
- Check database schema matches entities
- Validate foreign key constraints

### API connection errors
- Check API_BASE_URL in BuildConfig
- Verify server is running (curl http://localhost:8080/health)
- Check network connectivity
- Validate JWT token if using auth endpoints

## License

Proprietary - BizConnect Corp

## Support

For issues and feature requests, contact the development team.
