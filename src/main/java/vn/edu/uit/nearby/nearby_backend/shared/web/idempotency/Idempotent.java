package vn.edu.uit.nearby.nearby_backend.shared.web.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires an {@code Idempotency-Key} UUID header. Within {@code app.idempotency.ttl} a
 * repeat of the same key, user and endpoint with the same body receives the original
 * response (marked with {@code Idempotent-Replayed: true}); with another body it is
 * rejected with {@code 409 IDEMPOTENCY_CONFLICT}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

}
