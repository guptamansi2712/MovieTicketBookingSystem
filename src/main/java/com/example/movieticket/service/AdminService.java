package com.example.movieticket.service;

import com.example.movieticket.repository.AdminRepository;
import com.example.movieticket.web.ApiException;
import com.example.movieticket.web.dto.AdminRequests.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final AdminRepository adminRepository;

    public AdminService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public Map<String, Object> createCity(CreateCityRequest request) {
        Long id = adminRepository.createCity(request.name());
        return Map.of("id", id, "name", request.name());
    }

    @Transactional
    public Map<String, Object> createTheater(CreateTheaterRequest request) {
        Long theaterId = adminRepository.createTheater(request.cityId(), request.name(), request.address());
        Long screenId = adminRepository.createScreen(theaterId, request.screenName());
        for (SeatSpec seat : request.seats()) {
            String tier = seat.seatTier().toUpperCase();
            if (!List.of("REGULAR", "PREMIUM").contains(tier)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "seatTier must be REGULAR or PREMIUM");
            }
            adminRepository.createSeat(screenId, seat, tier);
        }
        return Map.of("theaterId", theaterId, "screenId", screenId, "seatCount", request.seats().size());
    }

    public Map<String, Object> createMovie(CreateMovieRequest request) {
        Long id = adminRepository.createMovie(
                request.title(),
                request.language(),
                request.durationMinutes(),
                request.certificate());
        return Map.of("id", id, "title", request.title());
    }

    public Map<String, Object> createPricingTier(CreatePricingTierRequest request) {
        Long id = adminRepository.createPricingTier(
                request.name(),
                request.regularPrice(),
                request.premiumPrice(),
                request.weekendMultiplier());
        return Map.of("id", id, "name", request.name());
    }

    public Map<String, Object> createRefundPolicy(CreateRefundPolicyRequest request) {
        Long id = adminRepository.createRefundPolicy(
                request.name(),
                request.cutoffMinutesBeforeShow(),
                request.refundPercent());
        return Map.of("id", id, "name", request.name());
    }

    public Map<String, Object> createDiscountCode(CreateDiscountCodeRequest request) {
        if (!request.validUntil().isAfter(request.validFrom())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validUntil must be after validFrom");
        }
        Long id = adminRepository.createDiscountCode(
                request.code().toUpperCase(),
                request.percentOff(),
                request.validFrom(),
                request.validUntil(),
                request.maxUses());
        return Map.of("id", id, "code", request.code().toUpperCase());
    }

    @Transactional
    public Map<String, Object> createShow(CreateShowRequest request) {
        Long showId = adminRepository.createShow(
                request.movieId(),
                request.screenId(),
                request.startsAt(),
                request.pricingTierId(),
                request.refundPolicyId());
        int seatsCreated = adminRepository.createShowSeatInventory(showId, request.screenId());
        return Map.of("id", showId, "seatInventoryCreated", seatsCreated);
    }
}
