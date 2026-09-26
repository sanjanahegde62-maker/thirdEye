package com.thirdeye.backend.controller;

import com.thirdeye.backend.entity.Notification;
import com.thirdeye.backend.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** GET /api/notifications — all notifications, newest first */
    @GetMapping
    public List<Notification> getAll() {
        return service.getAll();
    }

    /** GET /api/notifications/summary — { total, unread } */
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        return service.getSummary();
    }

    /** PATCH /api/notifications/{id}/read — mark one notification as read */
    @PatchMapping("/{id}/read")
    public Notification markRead(@PathVariable Long id) {
        return service.markRead(id);
    }

    /** PATCH /api/notifications/read-all — mark every notification as read */
    @PatchMapping("/read-all")
    public Map<String, Object> markAllRead() {
        service.markAllRead();
        return service.getSummary();
    }
}
