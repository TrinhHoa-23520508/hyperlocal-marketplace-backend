package vn.edu.uit.nearby.nearby_backend.support;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import vn.edu.uit.nearby.nearby_backend.shared.sms.SmsSender;

/**
 * Replaces the SMS gateway in integration tests and keeps the last message sent to each
 * number.
 */
@Primary
@TestComponent
public class RecordingSmsSender implements SmsSender {

	private static final Pattern SIX_DIGIT_CODE = Pattern.compile("\\b(\\d{6})\\b");

	private final Map<String, String> lastMessages = new ConcurrentHashMap<>();

	@Override
	public void send(String phoneE164, String content) {
		lastMessages.put(phoneE164, content);
	}

	public Optional<String> lastCodeSentTo(String phoneE164) {
		return Optional.ofNullable(lastMessages.get(phoneE164))
			.map(SIX_DIGIT_CODE::matcher)
			.filter(Matcher::find)
			.map(matcher -> matcher.group(1));
	}

	public boolean hasTexted(String phoneE164) {
		return lastMessages.containsKey(phoneE164);
	}

}
