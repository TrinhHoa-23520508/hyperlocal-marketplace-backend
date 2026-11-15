package vn.edu.uit.nearby.nearby_backend.shared.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * Local development only: account emails carry live links, so never disable mail in
 * production.
 */
@Slf4j
public class LoggingEmailSender implements EmailSender {

	@Override
	public void send(EmailMessage message) {
		log.info("[mail disabled] '{}' to {}:\n{}", message.subject(), message.recipient(), message.body());
	}

}
