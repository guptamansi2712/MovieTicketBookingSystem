package com.example.movieticket.repository;

import com.example.movieticket.web.dto.AdminRequests.SeatSpec;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {
    private final JdbcTemplate jdbcTemplate;

    public AdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long createCity(String name) {
        return jdbcTemplate.queryForObject("INSERT INTO cities(name) VALUES (?) RETURNING id", Long.class, name);
    }

    public Long createTheater(long cityId, String name, String address) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO theaters(city_id, name, address)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, cityId, name, address);
    }

    public Long createScreen(long theaterId, String screenName) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO screens(theater_id, name)
                VALUES (?, ?)
                RETURNING id
                """, Long.class, theaterId, screenName);
    }

    public void createSeat(long screenId, SeatSpec seat, String tier) {
        jdbcTemplate.update("""
                INSERT INTO seats(screen_id, row_label, seat_number, seat_tier)
                VALUES (?, ?, ?, ?)
                """, screenId, seat.rowLabel(), seat.seatNumber(), tier);
    }

    public Long createMovie(String title, String language, int durationMinutes, String certificate) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO movies(title, language, duration_minutes, certificate)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, title, language, durationMinutes, certificate);
    }

    public Long createPricingTier(String name, BigDecimal regularPrice, BigDecimal premiumPrice, BigDecimal weekendMultiplier) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO pricing_tiers(name, regular_price, premium_price, weekend_multiplier)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, name, regularPrice, premiumPrice, weekendMultiplier);
    }

    public Long createRefundPolicy(String name, int cutoffMinutesBeforeShow, BigDecimal refundPercent) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO refund_policies(name, cutoff_minutes_before_show, refund_percent)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, name, cutoffMinutesBeforeShow, refundPercent);
    }

    public Long createDiscountCode(
            String code,
            BigDecimal percentOff,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil,
            Integer maxUses) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO discount_codes(code, percent_off, active, valid_from, valid_until, max_uses)
                VALUES (?, ?, true, ?, ?, ?)
                RETURNING id
                """, Long.class, code, percentOff, validFrom, validUntil, maxUses);
    }

    public Long createShow(long movieId, long screenId, OffsetDateTime startsAt, long pricingTierId, long refundPolicyId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO shows(movie_id, screen_id, starts_at, pricing_tier_id, refund_policy_id)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, movieId, screenId, startsAt, pricingTierId, refundPolicyId);
    }

    public int createShowSeatInventory(long showId, long screenId) {
        return jdbcTemplate.update("""
                INSERT INTO show_seats(show_id, seat_id)
                SELECT ?, id
                FROM seats
                WHERE screen_id = ?
                """, showId, screenId);
    }
}
