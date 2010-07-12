package org.taverna.server.localworker.remote;

/**
 * Exception that indicates where a change of a workflow run's status is
 * illegal.
 * 
 * @author Donal Fellows
 * @see RemoteSingleRun#setStatus(RemoteStatus)
 */
public class IllegalStateTransitionException extends Exception {
	private static final long serialVersionUID = 159673249162345L;

	public IllegalStateTransitionException() {
		this("illegal state transition");
	}

	public IllegalStateTransitionException(String message) {
		super(message);
	}

	public IllegalStateTransitionException(Throwable cause) {
		this("illegal state transition", cause);
	}

	public IllegalStateTransitionException(String message, Throwable cause) {
		super(message, cause);
	}
}