package vn.edu.uit.nearby.nearby_backend.shared.mail;

/**
 * Sends plain text emails. Implementations send asynchronously and never fail the caller:
 * every email the platform sends can be requested again.
 */
public interface EmailSender {

	void send(EmailMessage message);

}
