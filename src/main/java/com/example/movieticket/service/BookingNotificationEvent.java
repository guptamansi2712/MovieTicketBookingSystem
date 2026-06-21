package com.example.movieticket.service;

public record BookingNotificationEvent(long userId, long bookingId, String type, String payload) {
}
