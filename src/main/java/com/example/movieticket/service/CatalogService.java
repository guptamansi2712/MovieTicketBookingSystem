package com.example.movieticket.service;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final JdbcTemplate jdbcTemplate;

    public CatalogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> cities() {
        return jdbcTemplate.queryForList("SELECT id, name FROM cities ORDER BY name");
    }

    public List<Map<String, Object>> shows(Long cityId) {
        return jdbcTemplate.queryForList("""
                SELECT sh.id, m.title, m.language, m.duration_minutes AS "durationMinutes",
                       c.name AS city, t.name AS theater, sc.name AS screen, sh.starts_at AS "startsAt",
                       COUNT(ss.id) FILTER (WHERE ss.status = 'AVAILABLE' OR (ss.status = 'HELD' AND ss.hold_expires_at <= now())) AS "availableSeats"
                FROM shows sh
                JOIN movies m ON m.id = sh.movie_id
                JOIN screens sc ON sc.id = sh.screen_id
                JOIN theaters t ON t.id = sc.theater_id
                JOIN cities c ON c.id = t.city_id
                JOIN show_seats ss ON ss.show_id = sh.id
                WHERE sh.status = 'SCHEDULED'
                  AND (? IS NULL OR c.id = ?)
                  AND sh.starts_at > now()
                GROUP BY sh.id, m.title, m.language, m.duration_minutes, c.name, t.name, sc.name, sh.starts_at
                ORDER BY sh.starts_at
                """, cityId, cityId);
    }

    public List<Map<String, Object>> availability(long showId) {
        return jdbcTemplate.queryForList("""
                SELECT ss.seat_id AS "seatId", s.row_label AS "rowLabel", s.seat_number AS "seatNumber",
                       s.seat_tier AS "seatTier",
                       CASE WHEN ss.status = 'HELD' AND ss.hold_expires_at <= now() THEN 'AVAILABLE' ELSE ss.status END AS status,
                       ss.hold_expires_at AS "holdExpiresAt"
                FROM show_seats ss
                JOIN seats s ON s.id = ss.seat_id
                WHERE ss.show_id = ?
                ORDER BY s.row_label, s.seat_number
                """, showId);
    }
}
