package vn.edu.uit.nearby.nearby_backend.chat.notification.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The dedup key of a notification (FR-NT-04, plan DEC-CH-5): a name-based UUID (version
 * 5) of the event type and the fields that identify the event, such as
 * {@code AreaExpiring:{areaId}:{expiresAt}}. Today's events carry no id of their own, and
 * the same event always gives the same id, so a replay from the outbox creates nothing.
 */
public final class SourceEventId {

	private static final UUID NAMESPACE = UUID.fromString("6f1c2d9e-3a54-4b6f-9c0e-2d7a8b1e5f40");

	private static final int VERSION_5 = 0x50;

	private static final int VARIANT_RFC_4122 = 0x80;

	private SourceEventId() {
	}

	public static UUID of(String eventType, Object... identifyingFields) {
		String name = eventType
				+ Arrays.stream(identifyingFields).map(field -> ":" + field).collect(Collectors.joining());
		return nameBased(NAMESPACE, name);
	}

	/**
	 * RFC 4122 section 4.3.
	 */
	static UUID nameBased(UUID namespace, String name) {
		MessageDigest sha1 = sha1();
		sha1.update(toBytes(namespace));
		byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
		hash[6] = (byte) ((hash[6] & 0x0F) | VERSION_5);
		hash[8] = (byte) ((hash[8] & 0x3F) | VARIANT_RFC_4122);
		ByteBuffer buffer = ByteBuffer.wrap(hash);
		return new UUID(buffer.getLong(), buffer.getLong());
	}

	private static byte[] toBytes(UUID uuid) {
		return ByteBuffer.allocate(16)
			.putLong(uuid.getMostSignificantBits())
			.putLong(uuid.getLeastSignificantBits())
			.array();
	}

	private static MessageDigest sha1() {
		try {
			return MessageDigest.getInstance("SHA-1");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("Every Java runtime has SHA-1.", ex);
		}
	}

}
