package com.example.movieticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.movieticket.service.BookingService;
import com.example.movieticket.web.ApiException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MovieTicketBookingSystemApplicationTests {
    @TestConfiguration
    static class SynchronousAsyncConfig {
        @Bean
        TaskExecutor taskExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired
    BookingService bookingService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    long showId;
    long seatId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                TRUNCATE TABLE notifications, payments, booking_seats, show_seats, seat_holds,
                bookings, shows, seats, screens, theaters, cities, movies, app_users,
                pricing_tiers, refund_policies, discount_codes
                RESTART IDENTITY CASCADE
                """);

        jdbcTemplate.update("INSERT INTO app_users(id, name, email, role) VALUES (1, 'Admin User', 'admin@example.com', 'ADMIN')");
        jdbcTemplate.update("INSERT INTO app_users(id, name, email, role) VALUES (2, 'Customer One', 'customer@example.com', 'CUSTOMER')");
        jdbcTemplate.update("INSERT INTO app_users(id, name, email, role) VALUES (10, 'Alice', 'alice@example.com', 'CUSTOMER')");
        jdbcTemplate.update("INSERT INTO app_users(id, name, email, role) VALUES (11, 'Bob', 'bob@example.com', 'CUSTOMER')");
        jdbcTemplate.update("INSERT INTO pricing_tiers(id, name, regular_price, premium_price, weekend_multiplier) VALUES (1, 'Standard', 180.00, 280.00, 1.25)");
        jdbcTemplate.update("INSERT INTO refund_policies(id, name, cutoff_minutes_before_show, refund_percent) VALUES (1, 'Default full refund until two hours before show', 120, 100.00)");
        jdbcTemplate.update("""
                INSERT INTO discount_codes(id, code, percent_off, active, valid_from, valid_until, max_uses)
                VALUES (1, 'WELCOME10', 10.00, true, now() - interval '1 day', now() + interval '365 days', 1000)
                """);
        Long cityId = jdbcTemplate.queryForObject("INSERT INTO cities(name) VALUES ('Bengaluru') RETURNING id", Long.class);
        Long theaterId = jdbcTemplate.queryForObject("INSERT INTO theaters(city_id, name, address) VALUES (?, 'PVR', 'MG Road') RETURNING id", Long.class, cityId);
        Long screenId = jdbcTemplate.queryForObject("INSERT INTO screens(theater_id, name) VALUES (?, 'Screen 1') RETURNING id", Long.class, theaterId);
        seatId = jdbcTemplate.queryForObject("INSERT INTO seats(screen_id, row_label, seat_number, seat_tier) VALUES (?, 'A', 1, 'REGULAR') RETURNING id", Long.class, screenId);
        Long movieId = jdbcTemplate.queryForObject("INSERT INTO movies(title, language, duration_minutes, certificate) VALUES ('Inception', 'English', 148, 'UA') RETURNING id", Long.class);
        showId = jdbcTemplate.queryForObject("""
                INSERT INTO shows(movie_id, screen_id, starts_at, pricing_tier_id, refund_policy_id)
                VALUES (?, ?, ?, 1, 1)
                RETURNING id
                """, Long.class, movieId, screenId, OffsetDateTime.now().plusDays(3));
        jdbcTemplate.update("INSERT INTO show_seats(show_id, seat_id) VALUES (?, ?)", showId, seatId);
    }

    @Test
    void confirmBookingConvertsHoldToBookedSeat() {
        Map<String, Object> hold = bookingService.holdSeats(10, showId, List.of(seatId));
        Map<String, Object> booking = bookingService.confirmBooking(
                10,
                (UUID) hold.get("holdToken"),
                "WELCOME10",
                "pay_test_1");

        assertThat(booking.get("bookingReference")).asString().startsWith("MTB-");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM show_seats WHERE show_id = ? AND seat_id = ?", String.class, showId, seatId))
                .isEqualTo("BOOKED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isEqualTo(1);
    }

    @Test
    void cancellationReleasesSeatAndRecordsRefund() {
        Map<String, Object> hold = bookingService.holdSeats(10, showId, List.of(seatId));
        Map<String, Object> booking = bookingService.confirmBooking(10, (UUID) hold.get("holdToken"), null, "pay_test_2");

        Map<String, Object> cancelled = bookingService.cancelBooking(10, ((Number) booking.get("bookingId")).longValue());

        assertThat(cancelled.get("refundAmount").toString()).isEqualTo("180.00");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM show_seats WHERE show_id = ? AND seat_id = ?", String.class, showId, seatId))
                .isEqualTo("AVAILABLE");
    }

    @Test
    void concurrentSeatHoldsSerializeAndOnlyOneUserWins() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var latch = new CountDownLatch(1);
        Callable<Boolean> alice = () -> attemptHoldAfterLatch(latch, 10);
        Callable<Boolean> bob = () -> attemptHoldAfterLatch(latch, 11);

        var aliceResult = executor.submit(alice);
        var bobResult = executor.submit(bob);
        latch.countDown();

        boolean first = aliceResult.get(5, TimeUnit.SECONDS);
        boolean second = bobResult.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(List.of(first, second)).containsExactlyInAnyOrder(true, false);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM show_seats WHERE status = 'HELD'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void expiredHoldCannotBeConfirmed() {
        Map<String, Object> hold = bookingService.holdSeats(10, showId, List.of(seatId));
        UUID token = (UUID) hold.get("holdToken");
        jdbcTemplate.update("UPDATE seat_holds SET expires_at = now() - interval '1 second' WHERE hold_token = ?", token);
        jdbcTemplate.update("UPDATE show_seats SET hold_expires_at = now() - interval '1 second' WHERE hold_token = ?", token);

        assertThatThrownBy(() -> bookingService.confirmBooking(10, token, null, "pay_expired"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Hold is no longer active");
    }

    private boolean attemptHoldAfterLatch(CountDownLatch latch, long userId) throws Exception {
        latch.await();
        try {
            bookingService.holdSeats(userId, showId, List.of(seatId));
            return true;
        } catch (ApiException ex) {
            return false;
        }
    }
}
