package p2p.tracker;

import trunk.social.p2p.message.TrackerData;
import trunk.social.p2p.peers.Number320;
import trunk.social.p2p.peers.PeerAddress;

public interface PeerExchangeHandler {

	boolean put(Number320 key, TrackerData trackerData, PeerAddress referrer);

	TrackerTriple get();

	TrackerStorage trackerStorage();

}
