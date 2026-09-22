package com.opencrm.jev.error;

/**
 * Base of the Jev failure hierarchy. These never escape {@code JevClient}; they exist so the transport
 * and retry policy can tell failures apart.
 */
public class JevException extends RuntimeException {

    private final int status;

    public JevException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** HTTP status, or 0 when the failure was not an HTTP response. */
    public int status() {
        return status;
    }

    public boolean retryable() {
        return false;
    }

    public static final class Authentication extends JevException {
        public Authentication(String message, int status) {
            super(message, status, null);
        }
    }

    public static final class BadRequest extends JevException {
        public BadRequest(String message, int status) {
            super(message, status, null);
        }
    }

    public static final class RateLimit extends JevException {
        private final Long retryAfterSeconds;

        public RateLimit(String message, Long retryAfterSeconds) {
            super(message, 429, null);
            this.retryAfterSeconds = retryAfterSeconds;
        }

        /** Value of the Retry-After header in seconds, or null when absent or unparseable. */
        public Long retryAfterSeconds() {
            return retryAfterSeconds;
        }

        @Override
        public boolean retryable() {
            return true;
        }
    }

    public static final class Server extends JevException {
        public Server(String message, int status) {
            super(message, status, null);
        }

        @Override
        public boolean retryable() {
            return true;
        }
    }

    public static final class Timeout extends JevException {
        public Timeout(String message, Throwable cause) {
            super(message, 0, cause);
        }

        @Override
        public boolean retryable() {
            return true;
        }
    }

    public static final class Connection extends JevException {
        public Connection(String message, Throwable cause) {
            super(message, 0, cause);
        }

        @Override
        public boolean retryable() {
            return true;
        }
    }

    /** The response arrived but does not satisfy the answer contract. Never retried. */
    public static final class InvalidAnswer extends JevException {
        public InvalidAnswer(String message) {
            super(message, 0, null);
        }
    }
}
