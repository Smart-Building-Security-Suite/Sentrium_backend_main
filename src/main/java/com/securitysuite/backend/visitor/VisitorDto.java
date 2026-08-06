package com.securitysuite.backend.visitor;

import java.time.Instant;
import java.util.UUID;

public record VisitorDto(
        UUID id,
        String name,
        String email,
        String phoneNumber,
        String company,
        String purpose,
        UUID hostId,
        String hostName,
        VisitorStatus status,
        Instant expectedArrivalAt,
        Instant expectedDepartureAt,
        Instant checkedInAt,
        Instant checkedOutAt,
        String badgeNumber,
        String vehiclePlateNumber,
        Boolean hostNotified,
        Instant createdAt
) {
    public static VisitorDto from(Visitor visitor) {
        return new VisitorDto(
                visitor.getId(),
                visitor.getName(),
                visitor.getEmail(),
                visitor.getPhoneNumber(),
                visitor.getCompany(),
                visitor.getPurpose(),
                visitor.getHost() != null ? visitor.getHost().getId() : null,
                visitor.getHost() != null ? visitor.getHost().getName() : null,
                visitor.getStatus(),
                visitor.getExpectedArrivalAt(),
                visitor.getExpectedDepartureAt(),
                visitor.getCheckedInAt(),
                visitor.getCheckedOutAt(),
                visitor.getBadgeNumber(),
                visitor.getVehiclePlateNumber(),
                visitor.getHostNotified(),
                visitor.getCreatedAt()
        );
    }
}
