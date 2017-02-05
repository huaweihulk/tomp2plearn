package p2p.holep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.connection.HolePInitiator;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.futures.FutureResponse;
import trunk.social.p2p.message.Message;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.peers.PeerAddress;

/**
 * @author Jonas Wagner
 * 
 *         This class is created if "new PeerNAT()" is called on a {@link Peer}.
 *         This class makes sure that hole punching is possible.
 */
public class HolePInitiatorImpl implements HolePInitiator {

	private static final Logger LOG = LoggerFactory.getLogger(HolePInitiatorImpl.class);
	private final NATTypeDetection natTypeDetection;
	private final Peer peer;
	private boolean testCase = false;
	private FutureDone<NATType> future;

	public HolePInitiatorImpl(final Peer peer) {
		this.peer = peer;
		this.natTypeDetection = null;
	}

	@Override
	public FutureDone<Message> handleHolePunch(final int idleUDPMillis, final FutureResponse futureResponse, final Message originalMessage) {
		//this is called from the sender, we start hole punching here.
		final FutureDone<Message> futureDone = new FutureDone<Message>();
		//final HolePStrategy holePuncher = natType().holePuncher(peer, peer.peerBean().holePNumberOfHoles(), idleUDPSeconds, originalMessage);
		//return holePuncher.initiateHolePunch(futureDone, futureResponse);
		return null;
	}

	/**
	 * CheckNatType will trigger the {@link NATTypeDetection} object to ping the
	 * given relay peer in order to find out the {@link NATType} of this
	 * {@link Peer}.
	 * 
	 * @param peerAddress
	 */
	public FutureDone<NATType> checkNatType(final PeerAddress peerAddress) {
		//future = natTypeDetection.checkNATType(peerAddress);
		return null;
		//return future;
	}


	public boolean isTestCase() {
		return testCase;
	}

	public void testCase(final boolean testCase) {
		this.testCase = testCase;
	}

}
