package com.example.movieticket.web;

import com.example.movieticket.security.CurrentUser;
import com.example.movieticket.service.BookingService;
import com.example.movieticket.web.dto.CustomerRequests.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customer")
public class CustomerController {
    private final BookingService bookingService;
    private final CurrentUser currentUser;

    public CustomerController(BookingService bookingService, CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.currentUser = currentUser;
    }

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> holdSeats(@Valid @RequestBody HoldSeatsRequest request) {
        return bookingService.holdSeats(currentUser.id(), request.showId(), request.seatIds());
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> confirmBooking(@Valid @RequestBody ConfirmBookingRequest request) {
        return bookingService.confirmBooking(currentUser.id(), request.holdToken(), request.discountCode(), request.paymentReference());
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    Map<String, Object> cancelBooking(@PathVariable long bookingId) {
        return bookingService.cancelBooking(currentUser.id(), bookingId);
    }

    @GetMapping("/bookings")
    List<Map<String, Object>> history() {
        return bookingService.history(currentUser.id());
    }
}
