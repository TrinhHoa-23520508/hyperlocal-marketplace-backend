package vn.edu.uit.nearby.nearby_backend.identity.infrastructure.mail;

import org.springframework.stereotype.Component;

import vn.edu.uit.nearby.nearby_backend.identity.application.port.AccountMailer;
import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.shared.mail.EmailMessage;
import vn.edu.uit.nearby.nearby_backend.shared.mail.EmailSender;

@Component
public class TemplatedAccountMailer implements AccountMailer {

	private final EmailSender emailSender;

	public TemplatedAccountMailer(EmailSender emailSender) {
		this.emailSender = emailSender;
	}

	@Override
	public void sendEmailVerification(EmailAddress recipient, String displayName, String verificationLink) {
		emailSender.send(new EmailMessage(recipient.value(), AccountEmailTemplates.VERIFICATION_SUBJECT,
				AccountEmailTemplates.verificationBody(displayName, verificationLink)));
	}

	@Override
	public void sendPasswordReset(EmailAddress recipient, String displayName, String resetLink) {
		emailSender.send(new EmailMessage(recipient.value(), AccountEmailTemplates.PASSWORD_RESET_SUBJECT,
				AccountEmailTemplates.passwordResetBody(displayName, resetLink)));
	}

}
