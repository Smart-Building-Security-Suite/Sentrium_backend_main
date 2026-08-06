package com.securitysuite.backend.visitor;

import com.securitysuite.backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Visitor {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Column(length = 100)
    private String company;

    @Column(length = 500)
    private String purpose;

    @ManyToOne
    @JoinColumn(name = "host_user_id")
    private User host; // Employee being visited

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VisitorStatus status = VisitorStatus.PRE_REGISTERED;

    @Column
    private Instant expectedArrivalAt;

    @Column
    private Instant expectedDepartureAt;

    @Column
    private Instant checkedInAt;

    @Column
    private Instant checkedOutAt;

    @Column(length = 50)
    private String badgeNumber; // Temporary badge issued

    @Column(length = 500)
    private String photoUrl; // Visitor photo for badge

    @Column(length = 500)
    private String idDocumentUrl; // ID verification

    @Column(length = 100)
    private String vehiclePlateNumber;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private Boolean hostNotified = false;
}
