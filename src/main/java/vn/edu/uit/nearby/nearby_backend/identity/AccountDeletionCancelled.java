package vn.edu.uit.nearby.nearby_backend.identity;

import java.time.Instant;
import java.util.UUID;

public record AccountDeletionCancelled(UUID userId, Instant cancelledAt) {

}
