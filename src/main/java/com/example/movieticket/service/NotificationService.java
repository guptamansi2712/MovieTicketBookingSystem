package com.example.movieticket.service;

import com.example.movieticket.repository.NotificationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Async
    @EventListener
    public void onBookingEvent(BookingNotificationEvent event) {
        notificationRepository.markBookingNotificationSent(
                event.userId(),
                event.bookingId(),
                event.type(),
                event.payload());
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void queueUpcomingShowReminders() {
        notificationRepository.queueUpcomingShowReminders();
    }
}
