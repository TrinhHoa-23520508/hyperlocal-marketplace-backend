package vn.edu.uit.nearby.nearby_backend.identity.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.uit.nearby.nearby_backend.identity.domain.AuditAction;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AuditLogEntry;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

	List<AuditLogEntry> findByTargetIdOrderByIdAsc(UUID targetId);

	Optional<AuditLogEntry> findFirstByTargetTypeAndTargetIdAndActionInOrderByIdDesc(String targetType, UUID targetId,
			Collection<AuditAction> actions);

}
