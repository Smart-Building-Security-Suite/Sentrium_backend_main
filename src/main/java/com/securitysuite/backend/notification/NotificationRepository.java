package com.securitysuite.backend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Query("select n from Notification n left join fetch n.alert a left join fetch a.zone where n.user.phoneNumber = :phoneNumber order by case when n.readAt is null then 0 else 1 end, n.createdAt desc")
    List<Notification> findByUserPhoneNumberOrderByUnreadFirst(@Param("phoneNumber") String phoneNumber);

    List<Notification> findByUserId(UUID userId);

    /**
     * Bulk-marks all unread notifications as read in a single UPDATE statement,
     * replacing the in-memory loop that loaded all notifications per user.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.user.id = :userId AND n.readAt IS NULL")
    void markAllReadByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.phoneNumber = :phoneNumber AND n.readAt IS NULL")
    long countUnreadByPhoneNumber(@Param("phoneNumber") String phoneNumber);
}
