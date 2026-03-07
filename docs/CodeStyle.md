# Spring Boot Backend Style Guide

> Unified coding standards and design patterns for the SoundGround backend

## Table of Contents

1. [General Principles](#general-principles)
2. [Naming Conventions](#naming-conventions)
3. [Project Structure (Layered)](#project-structure-layered)
4. [Dependency Injection](#dependency-injection)
5. [REST API Design](#rest-api-design)
6. [Data Access & JPA](#data-access--jpa)
7. [Exception Handling](#exception-handling)
8. [Testing Strategy](#testing-strategy)
9. [Documentation](#documentation)
10. [Git Commit Messages](#git-commit-messages)

---

## General Principles

### Code Quality Rules

- ✅ **SOLID Principles:** Follow strictly to ensure maintainable OOP.
- ✅ **Statelessness:** Services should be stateless; store state in DB or Cache.
- ✅ **Immutability:** Use `final` fields and Java **Records** for DTOs.
- ✅ **Lombok:** Use to reduce boilerplate, but avoid `@Data` on JPA entities.
- ✅ **Fail Fast:** Validate inputs at the Controller/DTO level using `jakarta.validation`.

### Enforcement Tools

- **Checkstyle:** UpperCamelCase for class name and lowerCamelCase for methods and UPPER_SNAKE_CASE for constants
- **Prettier:** For automated code formatting.
- **Sonarlint:** For catching "code smells" and security vulnerabilities locally.

---

## Naming Conventions

### Classes & Interfaces

```
✅ CORRECT                     ❌ INCORRECT
TrackController.java           trackController.java
AudioService.java              Audio.java
UserRepository.java            IUserRepository.java
GlobalExceptionHandler.java    ErrorHandler.java
TrackRequest.java              TrackDTO.java
```

**Rules:**

- **Controllers:** Suffix with `Controller` (e.g., `TrackController`)
- **Services:** Suffix with `Service`. Use interfaces only if multiple implementations exist.
- **Repositories:** Suffix with `Repository` (e.g., `UserRepository`)
- **DTOs:** Suffix with `Request` or `Response` (e.g., `AuthRequest`, `UserResponse`)
- **Entities:** Singular PascalCase (e.g., `User`, `Track`)

### Methods & Variables

- **Methods:** camelCase, starting with a verb (e.g., `findTrackById`, `saveUser`)
- **Variables:** camelCase, descriptive (e.g., `retryCount`)
- **Constants:** UPPER_SNAKE_CASE (e.g., `MAX_UPLOAD_SIZE`)

### Spring Boot Specifics

````text
✅ CORRECT                                      ❌ INCORRECT
application-dev.yml                             application-DEV.yml
soundground.file-upload.max-size=10MB           soundground.fileUpload.maxSize=10MB
SecurityConfig.java                             Security.java
findByArtistIdAndStatus(UUID id, Status s)      getArtistTracks(UUID id, Status s)
---

## Project Structure (Layered)

In this structure, we organize by the functional role of the class. This ensures a clear separation of concerns from the API entry point down to the database.

```text
src/main/java/com/soundground/api
├── config/             # Security, Bean definitions
├── controllers/        # REST Endpoints (Entry points)
├── services/           # Business Logic & Transactions
├── repositories/       # Database access (Spring Data JPA)
├── entities/           # JPA Database Models
├── dtos/               # Data Transfer Objects (Records)
├── exceptions/         # Custom exceptions & Global Handler
└── utils/              # Helper classes (Date formatting, etc.)
````

---

## Dependency Injection

**Rule:** Always use **Constructor Injection**. Avoid `@Autowired` on fields as it makes unit testing difficult and hides dependencies.

```java
// ✅ CORRECT - Constructor injection with Lombok
@Service
@RequiredArgsConstructor
public class TrackService {
    private final TrackRepository trackRepository;
    private final StorageService storageService;

    // Business logic...
}

// ❌ INCORRECT - Field injection
@Service
public class TrackService {
    @Autowired
    private TrackRepository trackRepository;
}
```

---

## REST API Design

### Controller Structure

```java
// ✅ CORRECT - REST Controller with explicit mapping
@RestController
@RequestMapping("/api/v1/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;

    @PostMapping
    public ResponseEntity<TrackResponse> uploadTrack(
        @Valid @RequestBody TrackRequest request
    ) {
        TrackResponse response = trackService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

The DTO pattern is a **mandatory standard** in our Spring Boot architecture. We strictly separate the data we store in our database (Entities) from the data we expose to or accept from the outside world (DTOs).

### Why We Mandate DTOs

1. **Security (Prevent Mass Assignment):** If you accept an Entity directly in a `@PostMapping`, a malicious user could send `{"isAdmin": true}` and overwrite sensitive database columns. DTOs act as a strict whitelist of allowed fields.
2. **Data Hiding:** Entities often contain internal state (password hashes, audit timestamps, internal IDs) that clients should never see.
3. **Decoupling:** Database schemas change. API contracts should remain stable. DTOs allow you to refactor your database without breaking mobile apps or frontend clients.
4. **Performance:** DTOs allow you to fetch and transmit only the specific fields a client needs, reducing payload size.

---

### DTO Implementation Rules

#### 1. Use Java Records for DTOs

Since Java 14, `record` is the perfect structure for DTOs. They are immutable, concise, and automatically generate getters, `equals()`, `hashCode()`, and `toString()`.

````java
// ✅ CORRECT - Immutable, concise, clear intent
public record UserProfileDto(
    UUID id,
    String username,
    String email,
    String bio
) {}

// ❌ INCORRECT - Boilerplate-heavy, mutable, uses class instead of record
@Data // Lombok @Data makes this mutable
public class UserProfileDto {
    private UUID id;
    private String username;
    // ...
}

## Data Access & JPA

### Entity Conventions

```java
@Entity
@Table(name = "tracks")
@Getter @Setter
@NoArgsConstructor
public class Track extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User artist;
}
````

**Rules:**

- **Avoid `@Data`:** It can cause infinite loops in `hashCode` with lazy-loaded relationships.
- **Auditing:** Use `@CreatedDate` and `@LastModifiedDate` annotations.

---

## Exception Handling

Use a `@RestControllerAdvice` to handle exceptions globally. This ensures the frontend receives a consistent error object.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TrackNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TrackNotFoundException ex) {
        var error = new ErrorResponse(
            "RESOURCE_NOT_FOUND",
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
}
```

---

## Documentation

## Git Commit Messages

### Format

```
<type>(<scope>): <subject>

<body (optional)>

<footer (optional)>
```

### Types

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Maintenance tasks

### Examples

```bash
# ✅ CORRECT
feat(auth): implement Google OAuth login
fix(player): resolve seek bar jumping issue
docs(readme): update installation instructions
test(tracks): add unit tests for upload service

# ❌ INCORRECT
Update files
Fixed bug
WIP
```

## Checklist Before Pushing

- [ ] Does the code compile without warnings?
- [ ] Are all new endpoints validated with `@Valid`?
- [ ] No `System.out.println()` (use `log.info()` or `log.error()`).
- [ ] Secrets (API Keys) are in `application-dev.yml` and NOT hardcoded.

---

## Things to Avoid

❌ Field injection (`@Autowired` on variables)  
❌ Exposing Entities directly to the frontend (use DTOs)  
❌ Logic in Controllers (keep it in the Service layer)  
❌ Catching generic `Exception` (catch specific exceptions)  
❌ Returning `null` (throw an exception)  
❌ Hardcoded status codes (use `HttpStatus` enum)
