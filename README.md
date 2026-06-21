# Movie Ticket Booking System

Spring Boot REST backend for a movie ticket booking system with city/theater/show management, seat-level holds, booking confirmation, payments, cancellation refunds, and asynchronous notifications.

## Tech Stack

- Java 17
- Spring Boot
- Spring Web MVC
- Spring Security
- Spring JDBC / transactions
- PostgreSQL
- Flyway migrations
- JUnit integration tests against local PostgreSQL

## Major Assumptions

- This is a backend-only assignment; no UI is included.
- Authentication is intentionally simple: pass `X-User-Id` on protected requests. Seeded users are `1` admin and `2` customer.
- Payment is modeled as a captured provider reference, not a real payment gateway integration.
- A seat hold lasts `booking.default-hold-minutes` minutes and can expire through the scheduled job or opportunistically during booking operations.
- Cancellation releases seats back to inventory. Refund eligibility is based on the show's configured refund policy.
- Notification delivery is simulated by asynchronously writing rows to the `notifications` table.
- PostgreSQL is used for both local runtime and integration tests. Tests use a separate local database so Docker is not required.

## Concurrency Design

The system creates one `show_seats` row for every seat in every show. Seat selection is serialized by PostgreSQL row locks:

1. The hold API starts a transaction.
2. It releases expired holds.
3. It selects the requested `show_seats` rows with `FOR UPDATE`, ordered by row id to reduce deadlock risk.
4. It verifies all seats are available.
5. It marks them `HELD` with a shared hold token and expiry.

Booking confirmation locks the hold row and the held `show_seats` rows again before converting them to `BOOKED`. This prevents double-allocation when multiple customers race for the same seat.

## Local Setup

Install PostgreSQL and create a database:

```bash
createdb movie_ticket_booking
createuser movie_user
psql movie_ticket_booking -c "ALTER USER movie_user WITH PASSWORD 'movie_password';"
psql movie_ticket_booking -c "GRANT ALL PRIVILEGES ON DATABASE movie_ticket_booking TO movie_user;"
```

Create the test database:

```bash
createdb -O movie_user movie_ticket_booking_test
```

Run the app:

```bash
./mvnw spring-boot:run
```

Environment variables:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/movie_ticket_booking
DATABASE_USERNAME=movie_user
DATABASE_PASSWORD=movie_password
```

Flyway runs automatically on startup and seeds:

- Admin: `X-User-Id: 1`
- Customer: `X-User-Id: 2`
- Pricing tier: `1`
- Refund policy: `1`
- Discount code: `WELCOME10`

## API Overview

Health:

```http
GET /health
```

Public catalog:

```http
GET /catalog/cities
GET /catalog/shows?cityId=1
GET /catalog/shows/{showId}/seats
```

Admin APIs require `X-User-Id: 1`:

```http
POST /admin/cities
POST /admin/theaters
POST /admin/movies
POST /admin/pricing-tiers
POST /admin/refund-policies
POST /admin/discount-codes
POST /admin/shows
```

Customer APIs require `X-User-Id: 2`:

```http
POST /customer/holds
POST /customer/bookings
POST /customer/bookings/{bookingId}/cancel
GET /customer/bookings
```

## Postman Collection

Import [MovieTicketBookingSystem.postman_collection.json](/Users/mansigupta/Documents/MovieTicketBookingSystem/postman/MovieTicketBookingSystem.postman_collection.json) into Postman. The collection includes variables for `baseUrl`, admin/customer user ids, generated entity ids, `holdToken`, and `bookingId`.

## Example Flow

Create a city:

```bash
curl -X POST http://localhost:8080/admin/cities \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' \
  -d '{"name":"Bengaluru"}'
```

Create a theater with a screen and seats:

```bash
curl -X POST http://localhost:8080/admin/theaters \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' \
  -d '{
    "cityId": 1,
    "name": "PVR Orion",
    "address": "Rajajinagar",
    "screenName": "Screen 1",
    "seats": [
      {"rowLabel":"A","seatNumber":1,"seatTier":"REGULAR"},
      {"rowLabel":"A","seatNumber":2,"seatTier":"REGULAR"},
      {"rowLabel":"B","seatNumber":1,"seatTier":"PREMIUM"}
    ]
  }'
```

Create a movie:

```bash
curl -X POST http://localhost:8080/admin/movies \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' \
  -d '{"title":"Interstellar","language":"English","durationMinutes":169,"certificate":"UA"}'
```

Create a show:

```bash
curl -X POST http://localhost:8080/admin/shows \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' \
  -d '{"movieId":1,"screenId":1,"startsAt":"2026-07-01T19:30:00+05:30","pricingTierId":1,"refundPolicyId":1}'
```

Hold seats:

```bash
curl -X POST http://localhost:8080/customer/holds \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 2' \
  -d '{"showId":1,"seatIds":[1,2]}'
```

Confirm booking:

```bash
curl -X POST http://localhost:8080/customer/bookings \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 2' \
  -d '{"holdToken":"<hold-token>","discountCode":"WELCOME10","paymentReference":"pay_demo_123"}'
```

Cancel booking:

```bash
curl -X POST http://localhost:8080/customer/bookings/1/cancel \
  -H 'X-User-Id: 2'
```

## Tests

Run:

```bash
./mvnw test
```

The integration tests use the local PostgreSQL database `movie_ticket_booking_test`. PostgreSQL must be running before tests are executed.
