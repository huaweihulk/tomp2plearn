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
 * Use this interface to notify if a peer received a broadcast ping.
 * 
 * @author Thomas Bocek
 * 
 */
public interface PeerReceivedBroadcastPing {
	/**
	 * Call this method when we receive a broadcast ping. If multiple peers are
	 * on the same network, only one reply will be accepted. Thus, all peers
	 * that receive such a broadcast ping will call this method.
	 * 
	 * @param sender The sender that sent the broadcast ping
	 */
	void broadcastPingReceived(PeerAddress sender);
}
