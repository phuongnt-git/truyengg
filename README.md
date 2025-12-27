# TruyenGG Spring Boot Application

Migration của ứng dụng TruyenGG từ PHP sang Spring Boot với Java 21.

## Kiến trúc

Ứng dụng sử dụng kiến trúc layered:
- **Controller Layer**: REST API endpoints và Thymeleaf templates
- **Service Layer**: Business logic với interface-based design
- **Repository Layer**: Data access với Spring Data JPA và Specifications
- **Domain Layer**: Entities và Enums

## Công nghệ sử dụng

- **Java 21**: LTS version với Records, Pattern Matching
- **Spring Boot 3.2.0**: Latest stable version
- **PostgreSQL**: Database chính
- **Flyway**: Database migrations
- **Spring Security**: JWT authentication và OAuth2
- **MapStruct**: Type-safe mapping
- **Caffeine**: Local caching
- **Thymeleaf**: Server-side rendering
- **SpringDoc OpenAPI**: API documentation

## Yêu cầu hệ thống

- JDK 21+
- PostgreSQL 16+
- Gradle 8.0+
- Docker & Docker Compose (optional)

## Cài đặt và chạy

### Sử dụng Docker Compose (Khuyến nghị)

```bash
# Build và chạy tất cả services
docker-compose up -d

# Xem logs
docker-compose logs -f app

# Dừng services
docker-compose down
```

### Chạy local

1. **Setup PostgreSQL:**
```bash
# Tạo database
createdb truyengg

# Hoặc sử dụng Docker
docker run -d --name postgres -e POSTGRES_DB=truyengg -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16-alpine
```

2. **Cấu hình environment variables:**
```bash
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=your-secret-key-change-in-production-min-256-bits
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret
export IMGBB_API_KEY=your-imgbb-api-key
export TURNSTILE_SECRET_KEY=your-turnstile-secret-key
```

3. **Build và chạy:**

**Cách 1: Sử dụng script tự động (Khuyến nghị)**
```bash
# Script này sẽ tự động:
# - Kiểm tra và khởi động Docker
# - Khởi động PostgreSQL container
# - Đợi PostgreSQL sẵn sàng
# - Chạy ứng dụng Spring Boot
# - Kiểm tra các trang
./start.sh
```

**Cách 2: Chạy thủ công**
```bash
# Khởi động PostgreSQL
docker-compose up -d postgres

# Đợi PostgreSQL sẵn sàng (khoảng 10-15 giây)
# Sau đó chạy ứng dụng
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=your-secret-key-change-in-production-min-256-bits
./gradlew bootRun
```

**Dừng ứng dụng:**
```bash
./stop.sh
```

Ứng dụng sẽ chạy tại: http://localhost:8080

## API Documentation

Sau khi chạy ứng dụng, truy cập:
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## Cấu trúc thư mục

```
truyengg-spring/
├── src/main/java/com/truyengg/
│   ├── config/          # Configuration classes
│   ├── controller/      # REST controllers
│   ├── domain/          # Entities, Repositories, Enums
│   ├── dto/             # DTOs và Mappers
│   ├── exception/       # Exception handling
│   ├── security/        # Security components
│   ├── service/         # Service interfaces và implementations
│   └── util/            # Utility classes
├── src/main/resources/
│   ├── db/migration/    # Flyway migrations
│   ├── templates/       # Thymeleaf templates
│   └── application.yml  # Configuration
└── src/test/            # Tests
```

## Database Migrations

Flyway sẽ tự động chạy migrations khi ứng dụng khởi động:
- `V1__Initial_schema.sql`: Tạo tất cả các bảng
- `V2__Insert_default_settings.sql`: Insert default settings
- `V3__Insert_mock_data.sql`: Mock data cho testing

## Security

- **JWT Authentication**: Stateless authentication với JWT tokens
- **BCrypt Password Hashing**: Secure password storage
- **Role-based Access Control**: Admin, User, Translator roles
- **Cloudflare Turnstile**: CAPTCHA verification
- **Google OAuth2**: Social login (cần implement)

## Caching

Caffeine cache được cấu hình cho:
- Home comics
- Comic details
- Categories
- Chapters
- Comments

Cache TTL: 10 minutes

## Testing

```bash
# Chạy tất cả tests
./gradlew test

# Chạy với coverage
./gradlew test jacocoTestReport
```

## Deployment

### Build production JAR:
```bash
./gradlew clean build -x test
```

### Run production:
```bash
java -jar build/libs/truyengg-1.0.0.jar --spring.profiles.active=prod
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_USERNAME` | PostgreSQL username | postgres |
| `DB_PASSWORD` | PostgreSQL password | postgres |
| `JWT_SECRET` | JWT secret key | (required) |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID | - |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret | - |
| `IMGBB_API_KEY` | ImgBB API key | - |
| `TURNSTILE_SECRET_KEY` | Cloudflare Turnstile secret | - |

## Migration từ PHP

Xem file `MIGRATION_GUIDE.md` để biết chi tiết về quá trình migration.

## License

MIT License

## Contributors

- Migration từ PHP sang Spring Boot

