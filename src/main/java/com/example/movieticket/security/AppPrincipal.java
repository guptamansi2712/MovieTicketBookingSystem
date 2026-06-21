package com.example.movieticket.security;

public record AppPrincipal(long id, String email, String role) {
}
