package vn.edu.uit.nearby.nearby_backend.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailSenderTests {

	private static final String SENDER = "Nearby <no-reply@nearby.vn>";

	private static final EmailMessage MESSAGE = new EmailMessage("an@example.com", "Subject", "Body");

	@Mock
	JavaMailSender mailSender;

	@Mock
	ObjectProvider<JavaMailSender> mailSenderProvider;

	@Test
	void smtpSenderSendsFromTheConfiguredAddress() {
		new SmtpEmailSender(mailSender, SENDER).send(MESSAGE);

		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(sent.capture());
		assertThat(sent.getValue().getFrom()).isEqualTo(SENDER);
		assertThat(sent.getValue().getTo()).containsExactly("an@example.com");
		assertThat(sent.getValue().getSubject()).isEqualTo("Subject");
		assertThat(sent.getValue().getText()).isEqualTo("Body");
	}

	@Test
	void smtpFailureDoesNotReachTheCaller() {
		doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

		assertThatCode(() -> new SmtpEmailSender(mailSender, SENDER).send(MESSAGE)).doesNotThrowAnyException();
	}

	@Test
	void loggingSenderNeverFails() {
		assertThatCode(() -> new LoggingEmailSender().send(MESSAGE)).doesNotThrowAnyException();
	}

	@Test
	void smtpIsUsedOnlyWhenMailIsEnabled() {
		when(mailSenderProvider.getObject()).thenReturn(mailSender);
		EmailConfig config = new EmailConfig();

		assertThat(config.emailSender(new EmailProperties(true, SENDER), mailSenderProvider))
			.isInstanceOf(SmtpEmailSender.class);
		assertThat(config.emailSender(new EmailProperties(false, SENDER), mailSenderProvider))
			.isInstanceOf(LoggingEmailSender.class);
	}

}
