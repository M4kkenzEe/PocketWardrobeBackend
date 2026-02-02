# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PocketWardrobe is a Kotlin backend built with Ktor framework for managing a wardrobe application. The backend provides REST APIs for user authentication, clothing management, outfit (looks) organization, and social sharing features.

## Build Commands

```bash
# Run the server
./gradlew run

# Run tests
./gradlew test

# Build everything
./gradlew build

# Build fat JAR with all dependencies
./gradlew buildFatJar

# Docker operations
./gradlew buildImage
./gradlew publishImageToLocalRegistry
./gradlew runDocker
```

Server runs at `http://0.0.0.0:8080` when started successfully.

## Architecture

### Core Technology Stack
- **Framework**: Ktor 3.1 with Netty engine
- **Database**: PostgreSQL with Exposed ORM v1 (legacy API) + HikariCP connection pooling
- **DI**: Koin for dependency injection
- **Authentication**: JWT with bcrypt password hashing
- **Serialization**: kotlinx.serialization for JSON

### External Service Integration
- **RemoveBgService**: Image processing service (configured via `REMOVE_BG_SERVICE_URL`) that removes backgrounds from clothing images and fetches images from Wildberries URLs
- **ClotheAnalysisService**: AI-powered image analysis service (configured via `ANALYSIS_SERVICE_URL`) that extracts clothing attributes (category, material, fit, season, style tags) from images

### Architecture Patterns

The codebase follows a **layered architecture**:

1. **Routes Layer** (`routes/`): HTTP endpoints and request handling
2. **Domain Layer** (`database/domain/repository/`): Repository interfaces
3. **Data Layer** (`database/data/`): Repository implementations and database models
4. **Service Layer** (`auth/service/`, `services/`): Business logic services
5. **Use Case Layer** (`usecases/`): Complex business operations that coordinate multiple repositories
6. **Plugins Layer** (`plugins/`): Ktor plugin configurations (CORS, rate limiting, error handling, logging)
7. **Validation Layer** (`validation/`): Input validation utilities (e.g., FileUploadValidator)
8. **DI Layer** (`di/`): Koin modules for dependency injection

### Database Schema

The application uses **Exposed ORM v1** (legacy API) with the following tables:

**Core Tables:**
- **UserTable**: User accounts with username, email, passwordHash, gender
- **ClotheTable**: Individual clothing items with name, imageUrl, storeUrl, plus AI-analyzed attributes: season, fit, material, category, styleTags
- **LookTable**: Outfit collections with name and url (composite image)

**Many-to-Many Junction Tables:**
- **UserClotheTable**: Links users to clothes (user's wardrobe) with soft delete (`isDeleted` flag)
- **UserLookTable**: Links users to looks (user's outfits) with soft delete (`isDeleted` flag)
- **LookItemTable**: Links looks to clothes with positioning data (size, x, y, z, rotation)

**Sharing System:**
- **SharedLookTable**: Share tokens for looks with lookId, shareToken (unique), createdAt

**Foreign Key Cascade Rules:**
- UserClotheTable and UserLookTable: CASCADE on user deletion
- LookItemTable: CASCADE on look deletion, RESTRICT on clothe deletion
- All shared look references: CASCADE on look deletion

**Soft Delete Pattern:**
UserClotheTable and UserLookTable implement soft deletes using `isDeleted` boolean flags, allowing users to "delete" items while preserving data integrity for shared looks.

### Authentication Flow

JWT-based authentication:
1. User registers via `/register` (password is hashed with bcrypt in UserService)
2. User logs in via `/login` to receive JWT token
3. Protected routes use `authenticate { }` block and access `UserPrincipal` from JWT claims
4. JWT configuration is loaded from `.env` file via `EnvironmentConfig` (JWT_SECRET, JWT_ISSUER, JWT_AUDIENCE, JWT_EXPIRES_IN)
5. UserPrincipal contains userId and username extracted from JWT payload

### File Upload Architecture

Two separate upload directories are used:
- **uploads/**: For processed clothing images (served at `/images/*`)
- **looks/**: For outfit composite images (served at `/looks/*`)

Both are served as static files and images are saved with UUID-based filenames.

### Key Workflows

**Adding Clothing via Upload**:
1. Receive multipart form with name, storeUrl, and image
2. Send image to RemoveBgService for background removal
3. Save processed image to `uploads/` directory
4. Store clothing record in ClotheTable
5. Create UserClotheTable entry linking user to clothe

**Adding Clothing via URL**:
1. Pass Wildberries URL to RemoveBgService `/get_wb_image/` endpoint
2. Service downloads and processes image
3. Save and store as above

**Creating Looks**:
1. Client uploads look composite image via `/looks/uploadImage` (max 5MB, JPEG/PNG only)
2. Client sends look data (name, lookItems with clothe IDs and positioning) as JSON to `/looks`
3. Validate all clothes have valid IDs before saving
4. Store look with references to existing clothes via LookItemTable with positioning data
5. Create UserLookTable entry linking user to look

**Sharing Looks**:
1. Owner creates share token via `POST /looks/{lookId}/share`
2. System generates unique shareToken stored in SharedLookTable
3. Recipients use shareToken to view and optionally import the look
4. Import can be FULL_LOOK (all items) or SELECTED_ITEMS (specific clothes)
5. ImportSharedLookUseCase handles copying clothes to recipient's wardrobe

**Analyzing Clothes**:
1. When clothing is uploaded, the image is sent to ClotheAnalysisService
2. Service extracts attributes: category, material, fit, season, styleTags
3. Attributes are stored directly in ClotheTable fields

### Repository Pattern

Repositories follow interface/implementation pattern:
- Interfaces in `database/domain/repository/`
- Implementations in `database/data/repository/`
- Injected via Koin modules in `di/DatabaseModule.kt`

All repositories:
- UserRepository
- ClotheRepository
- LookRepository
- UserClotheRepository
- UserLookRepository
- SharedLookRepository

### Use Case Pattern

Complex operations that coordinate multiple repositories are implemented as use cases:
- **ClotheUseCase**: Handles getting clothes by look ID
- **ImportSharedLookUseCase**: Handles importing shared looks to user's wardrobe (coordinates SharedLookRepository, ClotheRepository, LookRepository, UserClotheRepository)

### Module Organization

Koin modules are defined in `di/`:
- **authModule**: JwtService singleton
- **databaseModule**: All repository implementations, ClotheUseCase, and ImportSharedLookUseCase
- **remBgModule**: HttpClient, RemoveBgService, and ClotheAnalysisService

All modules are installed in `Application.module()`.

## Configuration

All configuration is loaded from environment variables (`.env` file or system env). See `auth/EnvironmentConfig.kt` for full list with validation.

### Required Environment Variables
```bash
# JWT Configuration
JWT_SECRET=your_secret_min_32_chars
JWT_ISSUER=https://your-issuer.com
JWT_AUDIENCE=your-audience
JWT_EXPIRES_IN=3600000

# Database Configuration
DB_URL=jdbc:postgresql://localhost:5432
DB_NAME=wardrobe
DB_USER=postgres
DB_PASSWORD=your_password

# External Services
REMOVE_BG_SERVICE_URL=http://localhost:8000/
ANALYSIS_SERVICE_URL=http://localhost:8001/
```

### Optional Environment Variables (with defaults)
```bash
# Server
SERVER_HOST=localhost
SERVER_PORT=8080

# File Storage
UPLOADS_DIRECTORY=uploads
LOOKS_DIRECTORY=looks
MAX_FILE_SIZE_MB=10

# Rate Limiting (requests per minute)
RATE_LIMIT_AUTH=10
RATE_LIMIT_UPLOAD=20
RATE_LIMIT_DEFAULT=100

# Database Pool (HikariCP)
DB_POOL_SIZE=10
DB_CONNECTION_TIMEOUT=30000
```

Schema is auto-created via `SchemaUtils.create()` on startup with HikariCP connection pooling.

## Testing

Tests are located in `src/test/kotlin/` using Ktor test utilities. Run with `./gradlew test`.

## Common Development Patterns

### Adding New Routes
1. Create route function in `routes/` package (e.g., `fun Application.myRoute()`)
2. Inject required repositories/services via Koin: `val repo: MyRepo by inject()`
3. Use `authenticate { }` block for protected endpoints
4. Extract userId from principal: `call.principal<UserPrincipal>()?.userId`
5. Register route function in `Application.module()`

### Adding New Repository
1. Define interface in `database/domain/repository/`
2. Implement in `database/data/repository/`
3. Register in `di/DatabaseModule.kt` with `factory` or `single`
4. Use Exposed transactions for all database operations

### Working with Exposed ORM
- This project uses **Exposed v1 (legacy API)**, not v2
- All database operations must be wrapped in `transaction { }` blocks
- Use DAO pattern: define `*Table` objects and `*Dao` classes extending `IntEntity`
- Convert DAOs to models using `daoToModel()` functions defined alongside models
- Import from `org.jetbrains.exposed.v1.*` packages, not `org.jetbrains.exposed.sql.*`

### Working with Soft Deletes
- UserClotheTable and UserLookTable use `isDeleted` flags
- Query methods should filter out soft-deleted records by default
- Use repository methods like `removeClotheFromUser()` and `removeLookFromUser()` which set `isDeleted = true`