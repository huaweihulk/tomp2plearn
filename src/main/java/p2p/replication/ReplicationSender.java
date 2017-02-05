package p2p.replication;

import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.Number640;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.storage.Data;

import java.util.NavigableMap;

public interface ReplicationSender {
	FutureDone<?> sendDirect(final PeerAddress other, final Number160 locationKey, final NavigableMap<Number640, Data> dataMap);
}
