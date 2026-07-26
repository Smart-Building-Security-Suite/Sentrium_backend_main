package com.securitysuite.backend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Query("select n from Notification n where n.user.email = :email order by case when n.readAt is null then 0 else 1 end, n.createdAt desc")
    List<Notification> findByUserEmailOrderByUnreadFirst(@Param("email") String email);

    List<Notification> findByUserId(UUID userId);
}
