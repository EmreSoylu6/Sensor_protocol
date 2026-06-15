package exceptions;

import java.io.Serial;

public class BadChecksumException extends IWProtocolException {

	@Serial
    private static final long serialVersionUID = -6481772554012497583L;

	/** Optional expected checksum value (may be null if not provided) */
	private final Long expected;
	/** Optional actual checksum value (may be null if not provided) */
	private final Long actual;

	/** Default constructor with no extra information. */
	public BadChecksumException() {
		super();
		this.expected = null;
		this.actual = null;
	}

	/** Construct with a detail message. */
	public BadChecksumException(String message) {
		super(message);
		this.expected = null;
		this.actual = null;
	}

	/** Construct with a cause. */
	public BadChecksumException(Throwable cause) {
		super(cause);
		this.expected = null;
		this.actual = null;
	}

	/** Construct with message and cause. */
	public BadChecksumException(String message, Throwable cause) {
		super(message, cause);
		this.expected = null;
		this.actual = null;
	}

	/** Construct with expected and actual checksum values. A descriptive message is generated. */
	public BadChecksumException(long expected, long actual) {
		super();
		this.detailMessage = buildMessage(expected, actual);
		this.expected = expected;
		this.actual = actual;
	}

	/** Construct with message and checksum values. */
	public BadChecksumException(String message, long expected, long actual) {
		super(message);
		this.expected = expected;
		this.actual = actual;
	}

	/** Construct with message, cause and checksum values. */
	public BadChecksumException(String message, Throwable cause, long expected, long actual) {
		super(message, cause);
		this.expected = expected;
		this.actual = actual;
	}

	private static String buildMessage(long expected, long actual) {
		return "Bad checksum: expected=" + expected + " actual=" + actual;
	}

	public Long getExpected() {
		return expected;
	}

	public Long getActual() {
		return actual;
	}

	@Override
	public String getMessage() {
		String sup = super.getMessage();
		if (detailMessage != null) {
			if (sup != null && !sup.isEmpty()) {
				return detailMessage + " (" + sup + ")";
			}
			return detailMessage;
		}
		return sup;
	}

	@Override
	public String toString() {
		String base = super.toString();
		if (expected != null && actual != null) {
			base += " [expected=" + expected + ", actual=" + actual + "]";
		}
		return base;
	}
}
