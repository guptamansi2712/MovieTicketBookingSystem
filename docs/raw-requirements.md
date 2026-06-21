# Raw Requirements Used During Development

Build a movie ticket booking system at scale with multiple cities, multiple theaters per city, multiple shows per theater, and seat-level booking.

The system should support:

- Seat selection with time-bound holds that release automatically on expiry.
- Multiple pricing tiers: regular, premium, weekend.
- Discount codes.
- Payment.
- Booking confirmation.
- Refunds on cancellation under configurable refund policies.
- Multiple users attempting to book the same seat at the same time without double-allocation.
- Confirmation and reminder notifications delivered without blocking the booking flow.

Roles:

- Admin: manage cities, theaters, shows, seat layouts, pricing tiers, and refund policies.
- Customer: browse shows, book and cancel seats, view booking history.

In scope:

- REST APIs covering the core flows.
- Persistence to a database.
- Basic role-based access control.
- Input validation and error handling.
- Unit and integration tests for core flows.

Out of scope:

- UI or frontend.
- Deployment, containerization, or CI/CD.
- Distributed systems or microservices.
- Advanced authentication such as OAuth, SSO, MFA.
- Production-grade observability, monitoring, or alerting.

Additional user direction:

- Clear the project and start from scratch.
- Use Java Spring Boot.
- Use PostgreSQL.
- Use Flyway database migrations.
- Pay special attention to database concurrency while booking seats.
- Ask for questions or permissions whenever required, such as PostgreSQL installation.
