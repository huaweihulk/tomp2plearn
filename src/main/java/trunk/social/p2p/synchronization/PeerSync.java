/*
 * Copyright 2013 Maxat Pernebayev, Thomas Bocek
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

package trunk.social.p2p.synchronization;

import trunk.social.p2p.dht.PeerDHT;
import trunk.social.p2p.dht.ReplicationListener;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.message.DataMap;
import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.Number640;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.replication.ReplicationSender;
import trunk.social.p2p.storage.Data;

import java.util.NavigableMap;

public class PeerSync implements ReplicationSender {

	private final SyncRPC syncRPC;
	private final PeerDHT peer;
	private final int blockSize;
	
	public PeerSync(final PeerDHT peer) {
		this(peer, null, 700);
	}
	
	public PeerSync(final PeerDHT peer, final int blockSize) {
		this(peer, null, blockSize);
	}
	

	/**
	 * Create a PeerSync class and register the RPC. Be aware that if you use
	 * {@link ReplicationSync}, than use the PeerSync that was created in that
	 * class.
	 * 
	 * @param peer
	 *            The peer
	 * @param blockSize
	 *            The block size as the basis for the checksums, RSync uses a
	 *            default of 700
	 */
	public PeerSync(final PeerDHT peer, final ReplicationListener replicationListener, final int blockSize) {
		this.peer = peer;
		this.syncRPC = new SyncRPC(peer.peerBean(), peer.peer().connectionBean(), blockSize, peer.storageLayer(), replicationListener);
		this.blockSize = blockSize;
	}

	public PeerDHT peerDHT() {
		return peer;
	}

	public SyncRPC syncRPC() {
		return syncRPC;
	}

	public SyncBuilder synchronize(PeerAddress other) {
		return new SyncBuilder(this, other, blockSize);
	}
	
	@Override
    public FutureDone<SyncStat> sendDirect(PeerAddress other, Number160 locationKey, NavigableMap<Number640, Data> dataMap) {
        FutureDone<SyncStat> future = synchronize(other)
                .dataMap(new DataMap(dataMap)).start();
        peer.peer().notifyAutomaticFutures(future);
        return future;
    }
}
