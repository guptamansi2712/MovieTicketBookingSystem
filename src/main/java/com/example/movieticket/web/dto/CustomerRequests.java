package com.example.movieticket.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class CustomerRequests {
    private CustomerRequests() {
    }

    public record HoldSeatsRequest(@NotNull Long showId, @NotEmpty @Size(max = 10) List<Long> seatIds) {
    }

    public record ConfirmBookingRequest(
            @NotNull UUID holdToken,
            String discountCode,
            @NotNull String paymentReference) {
    }
}
