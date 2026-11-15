package vn.edu.uit.nearby.nearby_backend.shared.security;

/**
 * Source of numeric one-time codes; tests replace it to get a known code.
 */
public interface OneTimeCodeGenerator {

	/**
	 * @return {@code digits} decimal digits, leading zeros kept
	 */
	String numericCode(int digits);

}
