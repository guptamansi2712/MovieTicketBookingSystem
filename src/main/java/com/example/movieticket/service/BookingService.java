package com.example.movieticket.service;

import com.example.movieticket.web.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final int defaultHoldMinutes;

    public BookingService(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            ApplicationEventPublisher eventPublisher,
            @Value("${booking.default-hold-minutes}") int defaultHoldMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.eventPublisher = eventPublisher;
        this.defaultHoldMinutes = defaultHoldMinutes;
    }

    @Transactional
    public Map<String, Object> holdSeats(long userId, long showId, List<Long> seatIds) {
        List<Long> uniqueSeatIds = new ArrayList<>(new LinkedHashSet<>(seatIds));
        if (uniqueSeatIds.size() != seatIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate seat ids are not allowed");
        }
        releaseExpiredHolds();
        UUID holdToken = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(defaultHoldMinutes);
        List<Map<String, Object>> lockedSeats = lockShowSeats(showId, uniqueSeatIds);
        if (lockedSeats.size() != uniqueSeatIds.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "One or more seats do not exist for this show");
        }
        for (Map<String, Object> seat : lockedSeats) {
            String status = (String) seat.get("status");
            OffsetDateTime holdExpiresAt = asOffsetDateTime(seat.get("hold_expires_at"));
            boolean expiredHold = "HELD".equals(status) && holdExpiresAt != null && !holdExpiresAt.isAfter(OffsetDateTime.now());
            if ("BOOKED".equals(status) || ("HELD".equals(status) && !expiredHold)) {
                throw new ApiException(HttpStatus.CONFLICT, "Selected seats are no longer available");
            }
        }
        jdbcTemplate.update("""
                INSERT INTO seat_holds(hold_token, show_id, user_id, expires_at)
                VALUES (?, ?, ?, ?)
                """, holdToken, showId, userId, expiresAt);
        namedJdbcTemplate.update("""
                UPDATE show_seats
                SET status = 'HELD', hold_token = :holdToken, held_by_user_id = :userId,
                    hold_expires_at = :expiresAt, version = version + 1
                WHERE show_id = :showId AND seat_id IN (:seatIds)
                """, new MapSqlParameterSource()
                .addValue("showId", showId)
                .addValue("seatIds", uniqueSeatIds)
                .addValue("holdToken", holdToken)
                .addValue("userId", userId)
                .addValue("expiresAt", expiresAt));
        return Map.of("holdToken", holdToken, "expiresAt", expiresAt, "seatIds", uniqueSeatIds);
    }

    @Transactional
    public Map<String, Object> confirmBooking(long userId, UUID holdToken, String discountCode, String paymentReference) {
        releaseExpiredHolds();
        Map<String, Object> hold = jdbcTemplate.queryForMap("""
                SELECT id, show_id, expires_at, status
                FROM seat_holds
                WHERE hold_token = ? AND user_id = ?
                FOR UPDATE
                """, holdToken, userId);
        if (!"ACTIVE".equals(hold.get("status")) || asOffsetDateTime(hold.get("expires_at")).isBefore(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Hold is no longer active");
        }
        long showId = ((Number) hold.get("show_id")).longValue();
        List<Map<String, Object>> lockedSeats = jdbcTemplate.queryForList("""
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
        if (lockedSeats.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "No seats found for hold");
        }
        BigDecimal subtotal = lockedSeats.stream()
                .map(this::priceFor)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        Discount discount = resolveDiscount(discountCode, subtotal);
        BigDecimal total = subtotal.subtract(discount.amount()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        String reference = "MTB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Long bookingId = jdbcTemplate.queryForObject("""
                INSERT INTO bookings(booking_reference, show_id, user_id, status, subtotal, discount_amount, total_amount, discount_code_id)
                VALUES (?, ?, ?, 'CONFIRMED', ?, ?, ?, ?)
                RETURNING id
                """, Long.class, reference, showId, userId, subtotal, discount.amount(), total, discount.discountCodeId());
        for (Map<String, Object> seat : lockedSeats) {
            long showSeatId = ((Number) seat.get("show_seat_id")).longValue();
            jdbcTemplate.update("""
                    INSERT INTO booking_seats(booking_id, show_seat_id, price)
                    VALUES (?, ?, ?)
                    """, bookingId, showSeatId, priceFor(seat));
        }
        jdbcTemplate.update("""
                UPDATE show_seats
                SET status = 'BOOKED', booked_by_booking_id = ?, hold_token = NULL, held_by_user_id = NULL,
                    hold_expires_at = NULL, version = version + 1
                WHERE hold_token = ?
                """, bookingId, holdToken);
        jdbcTemplate.update("UPDATE seat_holds SET status = 'CONFIRMED' WHERE hold_token = ?", holdToken);
        jdbcTemplate.update("""
                INSERT INTO payments(booking_id, provider_reference, amount, status)
                VALUES (?, ?, ?, 'CAPTURED')
                """, bookingId, paymentReference, total);
        if (discount.discountCodeId() != null) {
            jdbcTemplate.update("UPDATE discount_codes SET uses_count = uses_count + 1 WHERE id = ?", discount.discountCodeId());
        }
        eventPublisher.publishEvent(new BookingNotificationEvent(userId, bookingId, "BOOKING_CONFIRMED", reference));
        return Map.of("bookingId", bookingId, "bookingReference", reference, "subtotal", subtotal, "discount", discount.amount(), "total", total);
    }

    @Transactional
    public Map<String, Object> cancelBooking(long userId, long bookingId) {
        Map<String, Object> booking = jdbcTemplate.queryForMap("""
                SELECT b.*, sh.starts_at, rp.cutoff_minutes_before_show, rp.refund_percent
                FROM bookings b
                JOIN shows sh ON sh.id = b.show_id
                JOIN refund_policies rp ON rp.id = sh.refund_policy_id
                WHERE b.id = ? AND b.user_id = ?
                FOR UPDATE
                """, bookingId, userId);
        if (!"CONFIRMED".equals(booking.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "Booking is not cancellable");
        }
        OffsetDateTime startsAt = asOffsetDateTime(booking.get("starts_at"));
        int cutoff = ((Number) booking.get("cutoff_minutes_before_show")).intValue();
        BigDecimal refundPercent = (BigDecimal) booking.get("refund_percent");
        boolean refundable = OffsetDateTime.now().isBefore(startsAt.minusMinutes(cutoff));
        BigDecimal total = (BigDecimal) booking.get("total_amount");
        BigDecimal refund = refundable
                ? total.multiply(refundPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        jdbcTemplate.update("""
                UPDATE bookings
                SET status = 'CANCELLED', cancelled_at = now(), refund_amount = ?
                WHERE id = ?
                """, refund, bookingId);
        jdbcTemplate.update("""
                UPDATE show_seats
                SET status = 'AVAILABLE', booked_by_booking_id = NULL, version = version + 1
                WHERE booked_by_booking_id = ?
                """, bookingId);
        jdbcTemplate.update("UPDATE payments SET status = 'REFUNDED' WHERE booking_id = ?", bookingId);
        eventPublisher.publishEvent(new BookingNotificationEvent(userId, bookingId, "BOOKING_CANCELLED", "Refund " + refund));
        return Map.of("bookingId", bookingId, "refundAmount", refund);
    }

    public List<Map<String, Object>> history(long userId) {
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

    @Transactional
    public int releaseExpiredHolds() {
        jdbcTemplate.update("""
                UPDATE seat_holds
                SET status = 'EXPIRED'
                WHERE status = 'ACTIVE' AND expires_at <= now()
                """);
        return jdbcTemplate.update("""
                UPDATE show_seats
                SET status = 'AVAILABLE', hold_token = NULL, held_by_user_id = NULL, hold_expires_at = NULL, version = version + 1
                WHERE status = 'HELD' AND hold_expires_at <= now()
                """);
    }

    private List<Map<String, Object>> lockShowSeats(long showId, List<Long> seatIds) {
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

    private BigDecimal priceFor(Map<String, Object> seat) {
        BigDecimal base = "PREMIUM".equals(seat.get("seat_tier"))
                ? (BigDecimal) seat.get("premium_price")
                : (BigDecimal) seat.get("regular_price");
        int isoDay = ((Number) seat.get("iso_day")).intValue();
        if (isoDay >= 6) {
            base = base.multiply((BigDecimal) seat.get("weekend_multiplier"));
        }
        return base.setScale(2, RoundingMode.HALF_UP);
    }

    private OffsetDateTime asOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        throw new IllegalArgumentException("Unsupported timestamp value: " + value.getClass().getName());
    }

    private Discount resolveDiscount(String discountCode, BigDecimal subtotal) {
        if (discountCode == null || discountCode.isBlank()) {
            return new Discount(null, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, percent_off
                FROM discount_codes
                WHERE code = ? AND active = true AND valid_from <= now() AND valid_until >= now()
                  AND (max_uses IS NULL OR uses_count < max_uses)
                FOR UPDATE
                """, discountCode.toUpperCase());
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Discount code is invalid or exhausted");
        }
        long id = ((Number) rows.get(0).get("id")).longValue();
        BigDecimal percent = (BigDecimal) rows.get(0).get("percent_off");
        BigDecimal amount = subtotal.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Discount(id, amount);
    }

    private record Discount(Long discountCodeId, BigDecimal amount) {
    }
}
