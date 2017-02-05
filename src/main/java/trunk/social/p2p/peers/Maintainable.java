package trunk.social.p2p.peers;

import java.util.Collection;

public interface Maintainable {
	public PeerStatistic nextForMaintenance(Collection<PeerAddress> notInterestedAddresses);
}
