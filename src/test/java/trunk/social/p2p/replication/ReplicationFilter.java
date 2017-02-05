package trunk.social.p2p.replication;

import trunk.social.p2p.peers.PeerAddress;

/**
 * Allows to filter peers that should not be considered for the replication
 * @author Nico Rutishauser
 *
 */
public interface ReplicationFilter {

	boolean rejectReplication(PeerAddress targetAddress);
}
