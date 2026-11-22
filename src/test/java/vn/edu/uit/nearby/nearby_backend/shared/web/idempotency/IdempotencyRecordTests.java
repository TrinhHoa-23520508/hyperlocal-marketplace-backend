package vn.edu.uit.nearby.nearby_backend.shared.web.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class IdempotencyRecordTests {

	private static final String FINGERPRINT = IdempotencyRecord
		.fingerprintOf("{\"a\":1}".getBytes(StandardCharsets.UTF_8));

	@Test
	void roundTripsARequestStillInProgress() {
		IdempotencyRecord decoded = IdempotencyRecord.decode(IdempotencyRecord.inProgress(FINGERPRINT).encode());

		assertThat(decoded.isCompleted()).isFalse();
		assertThat(decoded.matches(FINGERPRINT)).isTrue();
	}

	@Test
	void roundTripsACompletedResponseWithItsExactBytes() {
		byte[] body = "{\"detail\":\"a|b\",\"name\":\"Nhà\"}".getBytes(StandardCharsets.UTF_8);

		IdempotencyRecord decoded = IdempotencyRecord
			.decode(IdempotencyRecord.completed(FINGERPRINT, 422, "application/problem+json", body).encode());

		assertThat(decoded.isCompleted()).isTrue();
		assertThat(decoded.status()).isEqualTo(422);
		assertThat(decoded.contentType()).isEqualTo("application/problem+json");
		assertThat(decoded.body()).isEqualTo(body);
	}

	@Test
	void roundTripsAnEmptyResponseWithoutContentType() {
		IdempotencyRecord decoded = IdempotencyRecord
			.decode(IdempotencyRecord.completed(FINGERPRINT, 204, null, new byte[0]).encode());

		assertThat(decoded.status()).isEqualTo(204);
		assertThat(decoded.contentType()).isEmpty();
		assertThat(decoded.body()).isEmpty();
	}

	@Test
	void fingerprintIsTheSameForTheSameBody() {
		byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

		assertThat(IdempotencyRecord.fingerprintOf(body)).isEqualTo(FINGERPRINT);
	}

	@Test
	void fingerprintDiffersForAnotherBody() {
		byte[] otherBody = "{\"a\":2}".getBytes(StandardCharsets.UTF_8);

		assertThat(IdempotencyRecord.inProgress(FINGERPRINT).matches(IdempotencyRecord.fingerprintOf(otherBody)))
			.isFalse();
	}

}
