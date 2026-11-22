package vn.edu.uit.nearby.nearby_backend.identity.application.admin;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AuditAction;
import vn.edu.uit.nearby.nearby_backend.identity.domain.AuditLogEntry;
import vn.edu.uit.nearby.nearby_backend.identity.infrastructure.AuditLogRepository;

@Service
public class AuditLogServiceImpl implements AuditLogService {

	private static final String REASON = "reason";

	private static final EnumSet<AuditAction> SANCTIONS = EnumSet.of(AuditAction.USER_SUSPENDED,
			AuditAction.USER_LOCKED);

	private final AuditLogRepository auditLog;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	public AuditLogServiceImpl(AuditLogRepository auditLog, JsonMapper jsonMapper, Clock clock) {
		this.auditLog = auditLog;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordUserAction(UUID actorId, AuditAction action, UUID targetUserId, Map<String, String> detail) {
		auditLog.save(AuditLogEntry.onUser(actorId, action, targetUserId, jsonMapper.writeValueAsString(detail),
				clock.instant()));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<String> latestSanctionReason(UUID userId) {
		return auditLog
			.findFirstByTargetTypeAndTargetIdAndActionInOrderByIdDesc(AuditLogEntry.USER_TARGET, userId, SANCTIONS)
			.map(AuditLogEntry::getDetail)
			.map(detail -> jsonMapper.readTree(detail).path(REASON))
			.filter(JsonNode::isString)
			.map(JsonNode::asString);
	}

}
