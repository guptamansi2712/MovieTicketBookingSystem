package com.example.movieticket.repository;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findById(long userId) {
        return jdbcTemplate.queryForList("""
                SELECT id, email, role
                FROM app_users
                WHERE id = ?
                """, userId);
    }
}
