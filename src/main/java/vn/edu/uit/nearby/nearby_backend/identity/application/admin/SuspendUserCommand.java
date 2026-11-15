package vn.edu.uit.nearby.nearby_backend.identity.application.admin;

import java.util.UUID;

public record SuspendUserCommand(UUID adminId, UUID userId, int days, String reason) {

}
