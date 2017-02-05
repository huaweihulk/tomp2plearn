package trunk.social.p2p.p2p;

import trunk.social.p2p.peers.PeerAddress;

/**
 * Filter the slow peers.<br>
 * 
 * @author Nico Rutishauser
 *
 */
public class SlowPeerFilter implements PostRoutingFilter {

	@Override
	public boolean rejectPotentialHit(PeerAddress peerAddress) {
		return peerAddress.slow();
	}

	@Override
	public boolean rejectDirectHit(PeerAddress peerAddress) {
		return peerAddress.slow();
	}

}
