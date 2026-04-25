# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build (skip tests)
./mvnw package -DskipTests

# Run the application
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=DemoApplicationTests

# Run a single test method
./mvnw test -Dtest=DemoApplicationTests#contextLoads
```

## Stack

- **Java 21**, Spring Boot 4.0.6
- **Spring MVC** (`spring-boot-starter-webmvc`) for REST/web layer
- **Spring Security** (`spring-boot-starter-security`) — included but unconfigured; by default all endpoints require HTTP Basic authentication
- **Lombok** — annotation processor wired into both compile and test-compile phases

## Project Structure

Standard Maven layout under `src/main/java/com/example/demo/`. Entry point is `DemoApplication`. The application currently has no controllers, services, or persistence layer — this is a bare scaffold ready to be built out.

## Git Workflow

**REQUIRED: After every file creation or meaningful code change, immediately commit and push to GitHub.** Do not wait, do not skip, do not batch. Every task ends with a commit and a push.

- Commit as soon as a file is created or a meaningful change is complete
- Push immediately after every commit — never leave commits local
- Use clear, descriptive commit messages in the imperative mood (e.g. `Add UserController with GET /users endpoint`, not `added stuff`)

## Spring Security Note

Because `spring-boot-starter-security` is on the classpath with no custom `SecurityFilterChain` bean, Spring Boot auto-configures HTTP Basic auth and generates a random password on startup. Add a `@Configuration` class with a `SecurityFilterChain` bean to override this before exposing any endpoints.
