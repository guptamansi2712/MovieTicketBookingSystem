# AGENTS.md

## Development Agent Notes

- Primary stack requested by the user: Java 17, Spring Boot, PostgreSQL, Flyway.
- No frontend, deployment, CI/CD, OAuth, SSO, MFA, or production observability was added because the assignment marks those out of scope.
- The central concurrency decision is to maintain one `show_seats` row per physical seat per show and serialize seat hold / booking attempts with PostgreSQL row locks using `SELECT ... FOR UPDATE`.
- Hold expiry is handled in two ways: a scheduled job releases expired holds, and booking operations opportunistically release expired holds before attempting to lock seats.
- Authentication is intentionally basic for the assignment: clients send `X-User-Id`, and Spring Security maps that database user to `ADMIN` or `CUSTOMER`.
- Notifications are persisted asynchronously from booking events so confirmation/cancellation work does not block on delivery.

## Skills Used During Development

- Spring Boot REST API design
- PostgreSQL schema design and transaction modeling
- Flyway database migrations
- Spring Security role-based access control
- Spring JDBC transactional service implementation
- Integration-test design against a local PostgreSQL test database
