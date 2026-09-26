package com.thirdeye.backend.repository;

import com.thirdeye.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** All notifications newest-first (broadcast view — no user filter). */
    List<Notification> findAllByOrderByCreatedAtDesc();

    /** Count of unread notifications (all users). */
    long countByReadFalse();

    /** Bulk-mark all unread as read (all users). */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.read = false")
    void markAllRead();

    // ── User-scoped queries ───────────────────────────────────────────────────

    /** Notifications for a specific user OR broadcast (userId=null). */
    @Query("SELECT n FROM Notification n WHERE (n.userId = :userId OR n.userId IS NULL) ORDER BY n.createdAt DESC")
    List<Notification> findByUserIdOrBroadcast(@Param("userId") Long userId);

    /** Unread count for a specific user OR broadcast. */
    @Query("SELECT COUNT(n) FROM Notification n WHERE (n.userId = :userId OR n.userId IS NULL) AND n.read = false")
    long countUnreadByUserIdOrBroadcast(@Param("userId") Long userId);

    /** Bulk-mark all as read for a specific user OR broadcast. */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE (n.userId = :userId OR n.userId IS NULL) AND n.read = false")
    void markAllReadByUserIdOrBroadcast(@Param("userId") Long userId);
}
