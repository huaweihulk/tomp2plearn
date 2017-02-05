package p2p.storage;

import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.Number320;
import trunk.social.p2p.peers.Number640;
import trunk.social.p2p.rpc.DigestInfo;
import trunk.social.p2p.rpc.SimpleBloomFilter;

import java.util.Collection;

public interface DigestStorage {

	public abstract DigestInfo digest(Number640 from, Number640 to, int limit, boolean ascending);

	public abstract DigestInfo digest(Number320 locationAndDomainKey, SimpleBloomFilter<Number160> keyBloomFilter,
                                      SimpleBloomFilter<Number160> contentBloomFilter, int limit, boolean ascending, boolean isBloomFilterAnd);

	public abstract DigestInfo digest(Collection<Number640> number640s);

}