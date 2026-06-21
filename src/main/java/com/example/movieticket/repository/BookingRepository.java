package com.example.movieticket.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public BookingRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    public void createSeatHold(UUID holdToken, long showId, long userId, OffsetDateTime expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO seat_holds(hold_token, show_id, user_id, expires_at)
                VALUES (?, ?, ?, ?)
                """, holdToken, showId, userId, expiresAt);
    }

    public void markSeatsHeld(long showId, List<Long> seatIds, UUID holdToken, long userId, OffsetDateTime expiresAt) {
        namedJdbcTemplate.update("""
                UPDATE show_seats
                SET status = 'HELD', hold_token = :holdToken, held_by_user_id = :userId,
                    hold_expires_at = :expiresAt, version = version + 1
                WHERE show_id = :showId AND seat_id IN (:seatIds)
                """, new MapSqlParameterSource()
                .addValue("showId", showId)
                .addValue("seatIds", seatIds)
                .addValue("holdToken", holdToken)
                .addValue("userId", userId)
                .addValue("expiresAt", expiresAt));
    }

    public Map<String, Object> lockSeatHold(UUID holdToken, long userId) {
        return jdbcTemplate.queryForMap("""
                SELECT id, show_id, expires_at, status
                FROM seat_holds
                WHERE hold_token = ? AND user_id = ?
                FOR UPDATE
                """, holdToken, userId);
    }

    public List<Map<String, Object>> lockSeatsForHold(UUID holdToken) {
        return jdbcTemplate.queryForList("""
                SELECT ss.id AS show_seat_id, ss.seat_id, ss.status, ss.hold_token, s.seat_tier,
                       pt.regular_price, pt.premium_price, pt.weekend_multiplier,
                       EXTRACT(ISODOW FROM sh.starts_at) AS iso_day
                FROM show_seats ss
                JOIN seats s ON s.id = ss.seat_id
                JOIN shows sh ON sh.id = ss.show_id
                JOIN pricing_tiers pt ON pt.id = sh.pricing_tier_id
                WHERE ss.hold_token = ?
                ORDER BY ss.id
                FOR UPDATE
                """, holdToken);
    }

    public Long createBooking(
            String reference,
            long showId,
            long userId,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal total,
            Long discountCodeId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO bookings(booking_reference, show_id, user_id, status, subtotal, discount_amount, total_amount, discount_code_id)
                VALUES (?, ?, ?, 'CONFIRMED', ?, ?, ?, ?)
                RETURNING id
                """, Long.class, reference, showId, userId, subtotal, discountAmount, total, discountCodeId);
    }

    public void createBookingSeat(long bookingId, long showSeatId, BigDecimal price) {
        jdbcTemplate.update("""
                INSERT INTO booking_seats(booking_id, show_seat_id, price)
                VALUES (?, ?, ?)
                """, bookingId, showSeatId, price);
    }

    public void markHeldSeatsBooked(long bookingId, UUID holdToken) {
        jdbcTemplate.update("""
                UPDATE show_seats
                SET status = 'BOOKED', booked_by_booking_id = ?, hold_token = NULL, held_by_user_id = NULL,
                    hold_expires_at = NULL, version = version + 1
                WHERE hold_token = ?
                """, bookingId, holdToken);
    }

    public void markHoldConfirmed(UUID holdToken) {
        jdbcTemplate.update("UPDATE seat_holds SET status = 'CONFIRMED' WHERE hold_token = ?", holdToken);
    }

    public void createPayment(long bookingId, String paymentReference, BigDecimal total) {
        jdbcTemplate.update("""
                INSERT INTO payments(booking_id, provider_reference, amount, status)
                VALUES (?, ?, ?, 'CAPTURED')
                """, bookingId, paymentReference, total);
    }

    public void incrementDiscountUse(long discountCodeId) {
        jdbcTemplate.update("UPDATE discount_codes SET uses_count = uses_count + 1 WHERE id = ?", discountCodeId);
    }

    public Map<String, Object> lockBookingForCancellation(long bookingId, long userId) {
        return jdbcTemplate.queryForMap("""
                SELECT b.*, sh.starts_at, rp.cutoff_minutes_before_show, rp.refund_percent
                FROM bookings b
                JOIN shows sh ON sh.id = b.show_id
                JOIN refund_policies rp ON rp.id = sh.refund_policy_id
                WHERE b.id = ? AND b.user_id = ?
                FOR UPDATE
                """, bookingId, userId);
    }

    public void markBookingCancelled(long bookingId, BigDecimal refund) {
        jdbcTemplate.update("""
                UPDATE bookings
                SET status = 'CANCELLED', cancelled_at = now(), refund_amount = ?
                WHERE id = ?
                """, refund, bookingId);
    }

    public void releaseBookedSeats(long bookingId) {
        jdbcTemplate.update("""
                UPDATE show_seats
                SET status = 'AVAILABLE', booked_by_booking_id = NULL, version = version + 1
                WHERE booked_by_booking_id = ?
                """, bookingId);
    }

    public void markPaymentRefunded(long bookingId) {
        jdbcTemplate.update("UPDATE payments SET status = 'REFUNDED' WHERE booking_id = ?", bookingId);
    }

    public List<Map<String, Object>> findBookingHistory(long userId) {
        return jdbcTemplate.queryForList("""
                SELECT b.id, b.booking_reference AS "bookingReference", b.status, b.total_amount AS "totalAmount",
                       b.refund_amount AS "refundAmount", b.created_at AS "createdAt", m.title, sh.starts_at AS "startsAt"
                FROM bookings b
                JOIN shows sh ON sh.id = b.show_id
                JOIN movies m ON m.id = sh.movie_id
                WHERE b.user_id = ?
                ORDER BY b.created_at DESC
                """, userId);
    }

    public void markExpiredHolds() {
        jdbcTemplate.update("""
                UPDATE seat_holds
                SET status = 'EXPIRED'
                WHERE status = 'ACTIVE' AND expires_at <= now()
                """);
    }

    public int releaseExpiredHeldSeats() {
        return jdbcTemplate.update("""
                UPDATE show_seats
                SET status = 'AVAILABLE', hold_token = NULL, held_by_user_id = NULL, hold_expires_at = NULL, version = version + 1
                WHERE status = 'HELD' AND hold_expires_at <= now()
                """);
    }

    public List<Map<String, Object>> lockShowSeats(long showId, List<Long> seatIds) {
        return namedJdbcTemplate.queryForList("""
                SELECT id, seat_id, status, hold_expires_at
                FROM show_seats
                WHERE show_id = :showId AND seat_id IN (:seatIds)
                ORDER BY id
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("showId", showId)
                .addValue("seatIds", seatIds));
    }

    public List<Map<String, Object>> lockDiscountCode(String code) {
        return jdbcTemplate.queryForList("""
                SELECT id, percent_off
                FROM discount_codes
                WHERE code = ? AND active = true AND valid_from <= now() AND valid_until >= now()
                  AND (max_uses IS NULL OR uses_count < max_uses)
                FOR UPDATE
                """, code);
    }
}
