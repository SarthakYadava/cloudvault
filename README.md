# CloudVault

CloudVault is a private document-storage API built with Spring Boot, PostgreSQL,
and Amazon S3. It is designed as the backend for a client document exchange
portal used by small professional-service teams.

The current backend includes account registration, JWT login, per-user file
ownership, and a production-minded S3 foundation. Presigned browser transfers
and a web dashboard are planned for the next milestones.

## Current Features

- Upload PDF, PNG, JPEG, and text files up to 10 MB.
- Transfer files directly between clients and private S3 using expiring URLs.
- Verify direct uploads with S3 before marking metadata as available.
- Register users and authenticate with short-lived JWT access tokens.
- Hash passwords with BCrypt; plaintext passwords are never stored.
- Isolate every file by owner in both PostgreSQL queries and S3 object keys.
- Store file bytes in a private Amazon S3 bucket.
- Store searchable file metadata in PostgreSQL.
- List files with pagination.
- Stream file downloads without writing files to local disk.
- Delete both the S3 object and its database metadata.
- Generate UUID-based object keys to avoid filename collisions.
- Load AWS credentials from the standard AWS credential provider chain.
- Apply Flyway database migrations at startup.
- Return consistent JSON errors without exposing AWS credentials or internals.
- Publish Swagger UI and Spring Boot health endpoints.
- Run automated service and application-context tests in GitHub Actions.

## Architecture

```mermaid
flowchart LR
    Client["Browser or API client"] --> Auth["JWT authentication"]
    Auth --> API["Spring Boot REST API"]
    API --> Validation["File validation"]
    API --> Database["PostgreSQL metadata"]
    API --> URLs["Short-lived presigned URLs"]
    URLs --> S3["Private Amazon S3 bucket"]
    Client --> S3
```

## Technology

- Java 21
- Spring Boot 3.5
- Spring Web and Bean Validation
- Spring Security and OAuth2 Resource Server JWT support
- Spring Data JPA
- PostgreSQL and Flyway
- AWS SDK for Java v2
- OpenAPI/Swagger UI
- JUnit, Mockito, and H2
- Gradle, Docker Compose, and GitHub Actions

## Prerequisites

- JDK 21
- AWS CLI with the `file-java90` profile
- Access to the private `file-java90` bucket in `eu-north-1`
- PostgreSQL, or Docker Desktop for the provided Compose service

The application never stores AWS access keys in source code. For local
development, it uses the restricted IAM profile created with:

```powershell
aws configure --profile file-java90
```

## Local Setup

Start PostgreSQL:

```powershell
docker compose up -d postgres
```

This workspace also has a portable PostgreSQL 18 installation. Start or stop it
after a reboot with:

```powershell
.\scripts\start-postgres.ps1
.\scripts\stop-postgres.ps1
```

Set the AWS profile for the current PowerShell session:

```powershell
$env:AWS_PROFILE="file-java90"
$env:AWS_REGION="eu-north-1"
$env:S3_BUCKET="file-java90"

$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$env:JWT_SECRET=[Convert]::ToBase64String($bytes)
```

Keep the same `JWT_SECRET` between restarts while developing. Changing it
immediately invalidates previously issued access tokens.

Run the application:

```powershell
.\gradlew.bat bootRun
```

Useful URLs:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- API specification: `http://localhost:8080/v3/api-docs`
- Health check: `http://localhost:8080/actuator/health`

## API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Register and receive a JWT |
| `POST` | `/api/auth/login` | Log in and receive a JWT |
| `POST` | `/api/files` | Upload a multipart file |
| `POST` | `/api/files/upload-requests` | Create a direct S3 upload URL |
| `POST` | `/api/files/{id}/complete` | Verify and complete a direct upload |
| `GET` | `/api/files?page=0&size=20` | List file metadata |
| `GET` | `/api/files/{id}/download` | Download a file |
| `GET` | `/api/files/{id}/download-url` | Create a direct S3 download URL |
| `DELETE` | `/api/files/{id}` | Delete a file |

Example upload:

```powershell
$login = Invoke-RestMethod `
  -Method POST `
  -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" `
  -Body '{"email":"sarthak@example.com","password":"StrongPass123"}'

curl.exe -X POST http://localhost:8080/api/files `
  -H "Authorization: Bearer $($login.accessToken)" `
  -F "file=@C:\path\to\document.pdf"
```

For direct browser uploads:

1. Call `POST /api/files/upload-requests` with filename, content type, and size.
2. Send the exact file bytes to `uploadUrl` using the returned method and headers.
3. Call `POST /api/files/{id}/complete`.
4. Request `GET /api/files/{id}/download-url` when a temporary download is needed.

The completion call rejects and removes objects whose S3 size or content type
does not match the original upload request.

## Configuration

| Environment variable | Default | Description |
| --- | --- | --- |
| `AWS_PROFILE` | AWS SDK default chain | Local AWS profile name |
| `AWS_REGION` | `eu-north-1` | S3 bucket region |
| `S3_BUCKET` | `file-java90` | Private S3 bucket |
| `JWT_SECRET` | Required | Random signing secret of at least 32 characters |
| `JWT_EXPIRATION` | `PT1H` | Access-token lifetime |
| `PRESIGNED_URL_EXPIRATION` | `PT10M` | Direct S3 URL lifetime |
| `DB_URL` | `jdbc:postgresql://localhost:5432/cloudvault` | JDBC URL |
| `DB_USERNAME` | `cloudvault` | Database user |
| `DB_PASSWORD` | `cloudvault` | Database password |

## Security Decisions

- S3 Block Public Access stays enabled.
- The application uses a least-privilege IAM user locally.
- AWS secrets are stored in the shared AWS credentials file, not this project.
- API requests are stateless and protected by signed, expiring JWTs.
- Passwords are stored as BCrypt hashes.
- File lookups always include the authenticated owner ID.
- Presigned links expire after ten minutes by default.
- Direct uploads remain pending until their S3 metadata is verified.
- Object keys are generated by the server and do not trust uploaded filenames.
- Allowed content types and file size are validated before upload.
- Storage failures are logged server-side and returned as generic API errors.

MIME types are currently checked from the multipart request and are not strong
proof of file contents. Content-signature detection is planned before calling
the project production-ready.

## Roadmap

1. Add filename search, audit events, and expiring share links.
2. Add LocalStack and Testcontainers integration tests.
3. Build a Thymeleaf drag-and-drop dashboard.
4. Add refresh tokens, logout/revocation, and account management.
5. Deploy the API and database with infrastructure as code.
