package vn.edu.uit.nearby.nearby_backend.shared.mail;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;

	private final String sender;

	public SmtpEmailSender(JavaMailSender mailSender, String sender) {
		this.mailSender = mailSender;
		this.sender = sender;
	}

	@Async
	@Override
	public void send(EmailMessage message) {
		SimpleMailMessage mail = new SimpleMailMessage();
		mail.setFrom(sender);
		mail.setTo(message.recipient());
		mail.setSubject(message.subject());
		mail.setText(message.body());
		try {
			mailSender.send(mail);
		}
		catch (MailException ex) {
			log.error("Failed to send '{}' email: {}", message.subject(), ex.getMessage());
		}
	}

}
