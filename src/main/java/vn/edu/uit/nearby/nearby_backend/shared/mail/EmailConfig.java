package vn.edu.uit.nearby.nearby_backend.shared.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class EmailConfig {

	@Bean
	EmailSender emailSender(EmailProperties properties, ObjectProvider<JavaMailSender> mailSender) {
		if (properties.enabled()) {
			return new SmtpEmailSender(mailSender.getObject(), properties.from());
		}
		log.warn("Mail is disabled (app.mail.enabled=false): emails are written to the log");
		return new LoggingEmailSender();
	}

}
