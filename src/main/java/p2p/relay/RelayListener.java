package p2p.relay;

import trunk.social.p2p.peers.PeerAddress;

public interface RelayListener {

	void relayFailed(PeerAddress relayAddress);

}
