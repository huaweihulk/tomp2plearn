package trunk.social.p2p.storage;

import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.rpc.DigestInfo;

public interface DigestTracker {

	DigestInfo digest(Number160 locationKey, Number160 domainKey, Number160 contentKey);

}