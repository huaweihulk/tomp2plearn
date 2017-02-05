package p2p.dht;

import trunk.social.p2p.peers.Number160;

public interface ReplicationListener {

	void dataInserted(Number160 locationKey);
	void dataRemoved(Number160 locationKey);

}
