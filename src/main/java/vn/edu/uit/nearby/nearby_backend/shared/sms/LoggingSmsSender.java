package vn.edu.uit.nearby.nearby_backend.shared.sms;

import lombok.extern.slf4j.Slf4j;

/**
 * Local development only: the content carries live one-time codes, so never select this
 * provider in production.
 */
@Slf4j
public class LoggingSmsSender implements SmsSender {

	@Override
	public void send(String phoneE164, String content) {
		log.info("[sms disabled] SMS to {}: {}", phoneE164, content);
	}

}
