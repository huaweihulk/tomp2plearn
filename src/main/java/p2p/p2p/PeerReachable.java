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
package p2p.p2p;

import trunk.social.p2p.peers.PeerAddress;

/**
 * Use this interface to notify if a peer is reachable from the outside.
 * 
 * @author Thomas Bocek
 * 
 */
public interface PeerReachable {

	/**
	 * Call this method when other peers can reach our peer from outside.
	 * 
	 * @param peerAddress
	 *            How we can be reached from outside
	 * @param reporter
	 *            The reporter that told us we are reachable
	 * @param tcp
	 *            True if we are reachable over TCP, false if we are reachable
	 *            over UDP
	 */
	void peerWellConnected(PeerAddress peerAddress, PeerAddress reporter, boolean tcp);
}
