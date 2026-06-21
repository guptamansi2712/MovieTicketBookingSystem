package com.example.movieticket.service;

import com.example.movieticket.web.ApiException;
import com.example.movieticket.web.dto.AdminRequests.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final JdbcTemplate jdbcTemplate;

    public AdminService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> createCity(CreateCityRequest request) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO cities(name) VALUES (?) RETURNING id", Long.class, request.name());
        return Map.of("id", id, "name", request.name());
    }

    @Transactional
    public Map<String, Object> createTheater(CreateTheaterRequest request) {
        Long theaterId = jdbcTemplate.queryForObject("""
                INSERT INTO theaters(city_id, name, address)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, request.cityId(), request.name(), request.address());
        Long screenId = jdbcTemplate.queryForObject("""
                INSERT INTO screens(theater_id, name)
                VALUES (?, ?)
                RETURNING id
                """, Long.class, theaterId, request.screenName());
        for (SeatSpec seat : request.seats()) {
            String tier = seat.seatTier().toUpperCase();
            if (!List.of("REGULAR", "PREMIUM").contains(tier)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "seatTier must be REGULAR or PREMIUM");
            }
            jdbcTemplate.update("""
                    INSERT INTO seats(screen_id, row_label, seat_number, seat_tier)
                    VALUES (?, ?, ?, ?)
                    """, screenId, seat.rowLabel(), seat.seatNumber(), tier);
        }
        return Map.of("theaterId", theaterId, "screenId", screenId, "seatCount", request.seats().size());
    }

    public Map<String, Object> createMovie(CreateMovieRequest request) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO movies(title, language, duration_minutes, certificate)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, request.title(), request.language(), request.durationMinutes(), request.certificate());
        return Map.of("id", id, "title", request.title());
    }

    public Map<String, Object> createPricingTier(CreatePricingTierRequest request) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO pricing_tiers(name, regular_price, premium_price, weekend_multiplier)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, request.name(), request.regularPrice(), request.premiumPrice(), request.weekendMultiplier());
        return Map.of("id", id, "name", request.name());
    }

    public Map<String, Object> createRefundPolicy(CreateRefundPolicyRequest request) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO refund_policies(name, cutoff_minutes_before_show, refund_percent)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, request.name(), request.cutoffMinutesBeforeShow(), request.refundPercent());
        return Map.of("id", id, "name", request.name());
    }

    public Map<String, Object> createDiscountCode(CreateDiscountCodeRequest request) {
        if (!request.validUntil().isAfter(request.validFrom())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validUntil must be after validFrom");
        }
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO discount_codes(code, percent_off, active, valid_from, valid_until, max_uses)
                VALUES (?, ?, true, ?, ?, ?)
                RETURNING id
                """, Long.class, request.code().toUpperCase(), request.percentOff(), request.validFrom(), request.validUntil(), request.maxUses());
        return Map.of("id", id, "code", request.code().toUpperCase());
    }

    @Transactional
    public Map<String, Object> createShow(CreateShowRequest request) {
        Long showId = jdbcTemplate.queryForObject("""
                INSERT INTO shows(movie_id, screen_id, starts_at, pricing_tier_id, refund_policy_id)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, request.movieId(), request.screenId(), request.startsAt(), request.pricingTierId(), request.refundPolicyId());
        int seatsCreated = jdbcTemplate.update("""
                INSERT INTO show_seats(show_id, seat_id)
                SELECT ?, id
                FROM seats
                WHERE screen_id = ?
                """, showId, request.screenId());
        return Map.of("id", showId, "seatInventoryCreated", seatsCreated);
    }
}
