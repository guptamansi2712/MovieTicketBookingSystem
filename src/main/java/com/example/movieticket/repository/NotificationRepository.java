package com.example.movieticket.repository;

import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void markBookingNotificationSent(long userId, long bookingId, String type, String payload) {
        jdbcTemplate.update("""
                INSERT INTO notifications(user_id, booking_id, type, payload, status, sent_at)
                VALUES (?, ?, ?, ?, 'SENT', ?)
                """, userId, bookingId, type, payload, OffsetDateTime.now());
    }

    public int queueUpcomingShowReminders() {
        return jdbcTemplate.update("""
                INSERT INTO notifications(user_id, booking_id, type, payload, status)
                SELECT b.user_id, b.id, 'SHOW_REMINDER', 'Reminder for booking ' || b.booking_reference, 'QUEUED'
                FROM bookings b
                JOIN shows sh ON sh.id = b.show_id
                WHERE b.status = 'CONFIRMED'
                  AND sh.starts_at BETWEEN now() + interval '30 minutes' AND now() + interval '31 minutes'
                  AND NOT EXISTS (
                    SELECT 1 FROM notifications n
                    WHERE n.booking_id = b.id AND n.type = 'SHOW_REMINDER'
                  )
                """);
    }
}
