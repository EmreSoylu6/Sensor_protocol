package exceptions;

import java.io.Serial;

public abstract class IWProtocolException extends Exception {

	@Serial
    private static final long serialVersionUID = -7002466204521265834L;

    protected String detailMessage;

    /** Default constructor with no extra information. */
    public IWProtocolException() {
        super();
        this.detailMessage = null;
    }

    /** Construct with a detail message. */
    public IWProtocolException(String message) {
        super();
        this.detailMessage = message;
    }

    /** Construct with a cause. */
    public IWProtocolException(Throwable cause) {
        super();
        this.detailMessage = null;
        if (cause != null) {
            initCause(cause);
        }
    }

    /** Construct with message and cause. */
    public IWProtocolException(String message, Throwable cause) {
        super();
        this.detailMessage = message;
        if (cause != null) {
            initCause(cause);
        }
    }

}
