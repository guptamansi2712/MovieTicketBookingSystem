package com.example.movieticket.web;

import com.example.movieticket.service.AdminService;
import com.example.movieticket.web.dto.AdminRequests.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createCity(@Valid @RequestBody CreateCityRequest request) {
        return adminService.createCity(request);
    }

    @PostMapping("/theaters")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createTheater(@Valid @RequestBody CreateTheaterRequest request) {
        return adminService.createTheater(request);
    }

    @PostMapping("/movies")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createMovie(@Valid @RequestBody CreateMovieRequest request) {
        return adminService.createMovie(request);
    }

    @PostMapping("/pricing-tiers")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createPricingTier(@Valid @RequestBody CreatePricingTierRequest request) {
        return adminService.createPricingTier(request);
    }

    @PostMapping("/refund-policies")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createRefundPolicy(@Valid @RequestBody CreateRefundPolicyRequest request) {
        return adminService.createRefundPolicy(request);
    }

    @PostMapping("/discount-codes")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createDiscountCode(@Valid @RequestBody CreateDiscountCodeRequest request) {
        return adminService.createDiscountCode(request);
    }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> createShow(@Valid @RequestBody CreateShowRequest request) {
        return adminService.createShow(request);
    }
}
