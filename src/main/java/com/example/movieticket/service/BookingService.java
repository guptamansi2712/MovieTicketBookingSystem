package com.example.movieticket.service;

import com.example.movieticket.repository.BookingRepository;
import com.example.movieticket.web.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int defaultHoldMinutes;

    public BookingService(
            BookingRepository bookingRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${booking.default-hold-minutes}") int defaultHoldMinutes) {
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
        this.defaultHoldMinutes = defaultHoldMinutes;
    }

    @Transactional
    public Map<String, Object> holdSeats(long userId, long showId, List<Long> seatIds) {
        List<Long> uniqueSeatIds = new ArrayList<>(new LinkedHashSet<>(seatIds));
        if (uniqueSeatIds.size() != seatIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate seat ids are not allowed");
        }
        releaseExpiredHolds();
        UUID holdToken = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(defaultHoldMinutes);
        List<Map<String, Object>> lockedSeats = bookingRepository.lockShowSeats(showId, uniqueSeatIds);
        if (lockedSeats.size() != uniqueSeatIds.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "One or more seats do not exist for this show");
        }
        for (Map<String, Object> seat : lockedSeats) {
            String status = (String) seat.get("status");
            OffsetDateTime holdExpiresAt = asOffsetDateTime(seat.get("hold_expires_at"));
            boolean expiredHold = "HELD".equals(status) && holdExpiresAt != null && !holdExpiresAt.isAfter(OffsetDateTime.now());
            if ("BOOKED".equals(status) || ("HELD".equals(status) && !expiredHold)) {
                throw new ApiException(HttpStatus.CONFLICT, "Selected seats are no longer available");
            }
        }
        bookingRepository.createSeatHold(holdToken, showId, userId, expiresAt);
        bookingRepository.markSeatsHeld(showId, uniqueSeatIds, holdToken, userId, expiresAt);
        return Map.of("holdToken", holdToken, "expiresAt", expiresAt, "seatIds", uniqueSeatIds);
    }

    @Transactional
    public Map<String, Object> confirmBooking(long userId, UUID holdToken, String discountCode, String paymentReference) {
        releaseExpiredHolds();
        Map<String, Object> hold = bookingRepository.lockSeatHold(holdToken, userId);
        if (!"ACTIVE".equals(hold.get("status")) || asOffsetDateTime(hold.get("expires_at")).isBefore(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "Hold is no longer active");
        }
        long showId = ((Number) hold.get("show_id")).longValue();
        List<Map<String, Object>> lockedSeats = bookingRepository.lockSeatsForHold(holdToken);
        if (lockedSeats.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "No seats found for hold");
        }
        BigDecimal subtotal = lockedSeats.stream()
                .map(this::priceFor)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        Discount discount = resolveDiscount(discountCode, subtotal);
        BigDecimal total = subtotal.subtract(discount.amount()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        String reference = "MTB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Long bookingId = bookingRepository.createBooking(
                reference,
                showId,
                userId,
                subtotal,
                discount.amount(),
                total,
                discount.discountCodeId());
        for (Map<String, Object> seat : lockedSeats) {
            long showSeatId = ((Number) seat.get("show_seat_id")).longValue();
            bookingRepository.createBookingSeat(bookingId, showSeatId, priceFor(seat));
        }
        bookingRepository.markHeldSeatsBooked(bookingId, holdToken);
        bookingRepository.markHoldConfirmed(holdToken);
        bookingRepository.createPayment(bookingId, paymentReference, total);
        if (discount.discountCodeId() != null) {
            bookingRepository.incrementDiscountUse(discount.discountCodeId());
        }
        eventPublisher.publishEvent(new BookingNotificationEvent(userId, bookingId, "BOOKING_CONFIRMED", reference));
        return Map.of("bookingId", bookingId, "bookingReference", reference, "subtotal", subtotal, "discount", discount.amount(), "total", total);
    }

    @Transactional
    public Map<String, Object> cancelBooking(long userId, long bookingId) {
        Map<String, Object> booking = bookingRepository.lockBookingForCancellation(bookingId, userId);
        if (!"CONFIRMED".equals(booking.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "Booking is not cancellable");
        }
        OffsetDateTime startsAt = asOffsetDateTime(booking.get("starts_at"));
        int cutoff = ((Number) booking.get("cutoff_minutes_before_show")).intValue();
        BigDecimal refundPercent = (BigDecimal) booking.get("refund_percent");
        boolean refundable = OffsetDateTime.now().isBefore(startsAt.minusMinutes(cutoff));
        BigDecimal total = (BigDecimal) booking.get("total_amount");
        BigDecimal refund = refundable
                ? total.multiply(refundPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        bookingRepository.markBookingCancelled(bookingId, refund);
        bookingRepository.releaseBookedSeats(bookingId);
        bookingRepository.markPaymentRefunded(bookingId);
        eventPublisher.publishEvent(new BookingNotificationEvent(userId, bookingId, "BOOKING_CANCELLED", "Refund " + refund));
        return Map.of("bookingId", bookingId, "refundAmount", refund);
    }

    public List<Map<String, Object>> history(long userId) {
        return bookingRepository.findBookingHistory(userId);
    }

    @Transactional
    public int releaseExpiredHolds() {
        bookingRepository.markExpiredHolds();
        return bookingRepository.releaseExpiredHeldSeats();
    }

    private BigDecimal priceFor(Map<String, Object> seat) {
        BigDecimal base = "PREMIUM".equals(seat.get("seat_tier"))
                ? (BigDecimal) seat.get("premium_price")
                : (BigDecimal) seat.get("regular_price");
        int isoDay = ((Number) seat.get("iso_day")).intValue();
        if (isoDay >= 6) {
            base = base.multiply((BigDecimal) seat.get("weekend_multiplier"));
        }
        return base.setScale(2, RoundingMode.HALF_UP);
    }

    private OffsetDateTime asOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        throw new IllegalArgumentException("Unsupported timestamp value: " + value.getClass().getName());
    }

    private Discount resolveDiscount(String discountCode, BigDecimal subtotal) {
        if (discountCode == null || discountCode.isBlank()) {
            return new Discount(null, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        List<Map<String, Object>> rows = bookingRepository.lockDiscountCode(discountCode.toUpperCase());
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Discount code is invalid or exhausted");
        }
        long id = ((Number) rows.get(0).get("id")).longValue();
        BigDecimal percent = (BigDecimal) rows.get(0).get("percent_off");
        BigDecimal amount = subtotal.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new Discount(id, amount);
    }

    private record Discount(Long discountCodeId, BigDecimal amount) {
    }
}
