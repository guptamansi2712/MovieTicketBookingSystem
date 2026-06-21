package com.example.movieticket.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class AdminRequests {
    private AdminRequests() {
    }

    public record CreateCityRequest(@NotBlank String name) {
    }

    public record CreateTheaterRequest(
            @NotNull Long cityId,
            @NotBlank String name,
            @NotBlank String address,
            @NotBlank String screenName,
            @NotEmpty @Valid List<SeatSpec> seats) {
    }

    public record SeatSpec(@NotBlank @Size(max = 8) String rowLabel, @Min(1) int seatNumber, @NotBlank String seatTier) {
    }

    public record CreateMovieRequest(
            @NotBlank String title,
            @NotBlank String language,
            @Min(1) int durationMinutes,
            @NotBlank String certificate) {
    }

    public record CreatePricingTierRequest(
            @NotBlank String name,
            @NotNull @DecimalMin("0.00") BigDecimal regularPrice,
            @NotNull @DecimalMin("0.00") BigDecimal premiumPrice,
            @NotNull @DecimalMin("1.00") BigDecimal weekendMultiplier) {
    }

    public record CreateRefundPolicyRequest(
            @NotBlank String name,
            @Min(0) int cutoffMinutesBeforeShow,
            @NotNull @DecimalMin("0.00") BigDecimal refundPercent) {
    }

    public record CreateDiscountCodeRequest(
            @NotBlank String code,
            @NotNull @DecimalMin("0.01") BigDecimal percentOff,
            @NotNull OffsetDateTime validFrom,
            @NotNull OffsetDateTime validUntil,
            Integer maxUses) {
    }

    public record CreateShowRequest(
            @NotNull Long movieId,
            @NotNull Long screenId,
            @NotNull @Future OffsetDateTime startsAt,
            @NotNull Long pricingTierId,
            @NotNull Long refundPolicyId) {
    }
}
