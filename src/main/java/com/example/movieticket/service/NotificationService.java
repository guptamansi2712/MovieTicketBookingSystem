package com.example.movieticket.service;

import java.time.OffsetDateTime;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final JdbcTemplate jdbcTemplate;

    public NotificationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Async
    @EventListener
    public void onBookingEvent(BookingNotificationEvent event) {
        jdbcTemplate.update("""
                INSERT INTO notifications(user_id, booking_id, type, payload, status, sent_at)
                VALUES (?, ?, ?, ?, 'SENT', ?)
                """, event.userId(), event.bookingId(), event.type(), event.payload(), OffsetDateTime.now());
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void queueUpcomingShowReminders() {
        jdbcTemplate.update("""
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
