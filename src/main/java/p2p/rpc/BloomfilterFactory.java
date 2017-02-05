package p2p.rpc;

import trunk.social.p2p.peers.Number160;

public interface BloomfilterFactory {

	SimpleBloomFilter<Number160> createContentKeyBloomFilter();
	
	SimpleBloomFilter<Number160> createVersionKeyBloomFilter();
    
    SimpleBloomFilter<Number160> createContentBloomFilter();

}
