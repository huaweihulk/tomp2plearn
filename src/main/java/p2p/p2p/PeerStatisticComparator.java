package p2p.p2p;

import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.PeerStatistic;

import java.util.Comparator;

/**
 * Interface used by the PeerMap. Must return a Comparator that compares
 * two PeerStatistic objects based on how close they are to a a given location.
 *
 * The Comparator must never return 0 for PeerStatistics with equal
 * PeerID. Breaking ties with the complete XOR distance ensures this property.
 *
 * Created by Sebastian Stephan on 26.12.14.
 */
public interface PeerStatisticComparator {
    public Comparator<PeerStatistic> getComparator(Number160 location);
}
