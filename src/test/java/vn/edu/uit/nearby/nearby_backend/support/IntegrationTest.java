package vn.edu.uit.nearby.nearby_backend.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import vn.edu.uit.nearby.nearby_backend.TestcontainersConfiguration;

/**
 * Every integration test uses this exact configuration so they share one cached Spring
 * context and one set of containers. They use the in-memory STOMP broker; the broker
 * relay has its own test with a RabbitMQ container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "app.websocket.relay.enabled=false")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ProbeController.class, RecordingAccountMailer.class,
		RecordingSmsSender.class, InMemoryImageStorage.class })
public @interface IntegrationTest {

}
