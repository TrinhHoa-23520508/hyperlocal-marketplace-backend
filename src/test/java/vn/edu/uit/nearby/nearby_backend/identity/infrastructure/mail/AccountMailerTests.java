package vn.edu.uit.nearby.nearby_backend.identity.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.uit.nearby.nearby_backend.identity.domain.EmailAddress;
import vn.edu.uit.nearby.nearby_backend.shared.mail.EmailMessage;
import vn.edu.uit.nearby.nearby_backend.shared.mail.EmailSender;

@ExtendWith(MockitoExtension.class)
class AccountMailerTests {

	private static final EmailAddress RECIPIENT = EmailAddress.of("an@example.com");

	@Mock
	EmailSender emailSender;

	@Test
	void verificationEmailContainsTheNameAndTheLink() {
		new TemplatedAccountMailer(emailSender).sendEmailVerification(RECIPIENT, "An", "nearby://verify?token=t1");

		EmailMessage message = sentMessage();
		assertThat(message.recipient()).isEqualTo("an@example.com");
		assertThat(message.subject()).isEqualTo(AccountEmailTemplates.VERIFICATION_SUBJECT);
		assertThat(message.body()).contains("An", "nearby://verify?token=t1");
	}

	@Test
	void passwordResetEmailContainsTheLink() {
		new TemplatedAccountMailer(emailSender).sendPasswordReset(RECIPIENT, "An", "nearby://reset?token=t2");

		EmailMessage message = sentMessage();
		assertThat(message.subject()).isEqualTo(AccountEmailTemplates.PASSWORD_RESET_SUBJECT);
		assertThat(message.body()).contains("nearby://reset?token=t2");
	}

	private EmailMessage sentMessage() {
		ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
		verify(emailSender).send(message.capture());
		return message.getValue();
	}

}
