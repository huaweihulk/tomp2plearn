/*
 * Copyright 2009 Thomas Bocek
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package p2p.connection;

import trunk.social.p2p.futures.FutureResponse;

/**
 * This exception is used internally and passed over to the method
 * exceptionCaught. A PeerException always has a cause
 * 
 * @author Thomas Bocek
 */
public class PeerException extends Exception {
    private static final long serialVersionUID = 3710790196087629945L;

    /**
     * USER_ABORT means that this peer aborts the communication. PEER_ERROR
     * means that the other peer did not react as expected (e.g., no reply).
     * PEER_ABORT means that the other peer found an error on our side (e.g., if
     * this peer thinks the other peer is someone else).
     * 
     * @author Thomas Bocek
     */
    public enum AbortCause {
        USER_ABORT, PEER_ERROR, PEER_ABORT, TIMEOUT, SHUTDOWN, PROBABLY_OFFLINE
    }

    private final AbortCause abortCause;

    /**
     * Specified error with custom message.
     * 
     * @param abortCause
     *            Either USER_ABORT, PEER_ERROR, PEER_ABORT, or TIMEOUT.
     * @param message
     *            Custom message.
     */
    public PeerException(final AbortCause abortCause, final String message) {
        super(message);
        this.abortCause = abortCause;
    }

    public PeerException(FutureResponse future) {
    	super(future.failedReason());
    	this.abortCause = AbortCause.PEER_ERROR;
    }

	public PeerException(Throwable cause) {
		super(cause);
		this.abortCause = AbortCause.PEER_ERROR;
    }

	/**
     * @return The cause of the exception.
     */
    public AbortCause abortCause() {
        return abortCause;
    }

    @Override
    public String toString() {
        return "PeerException (" + abortCause.toString() + "): " + getMessage();
    }
}
