/*
 * Copyright 2012 Thomas Bocek
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

package trunk.social.p2p.peers;

import trunk.social.p2p.connection.PeerConnection;
import trunk.social.p2p.connection.PeerException;

/**
 * All classes that are interested if a new peer was discovered or a peer died (that means all classes that store peer
 * addresses) should implement this interface and add itself as a listener.
 * 
 * @author Thomas Bocek
 * 
 */
public interface PeerStatusListener {

    /**
     * Called if the peer does not send answer in time. The peer may be busy, so there is a chance of seeing this peer
     * again.
     * 
     * @param remotePeer
     *            The address of the peer that failed
     * @param exception
     *            The reason why the peer failed. This is important to understand if we can re-enable the peer.
     * @return False, if nothing happened. True, if there was a change.
     */
    boolean peerFailed(final PeerAddress remotePeer, final PeerException exception);

    /**
     * Called if the peer is online. Provides the referrer who reported it. This method may get called many times, for each successful
     * request.
     * 
     * @param remotePeer
     *            The address of the peer that is online.
     * @param referrer
     *            The peer that reported the availability of the peer address.
     * @param peerConnection 
     * @return False, if nothing happened. True, if there was a change.
     */
    boolean peerFound(final PeerAddress remotePeer, final PeerAddress referrer, PeerConnection peerConnection, RTT roundTripTime);
}
