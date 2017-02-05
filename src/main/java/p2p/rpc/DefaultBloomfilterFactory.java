package p2p.rpc;

import trunk.social.p2p.peers.Number160;

public class DefaultBloomfilterFactory  implements BloomfilterFactory {

    @Override
    public SimpleBloomFilter<Number160> createContentKeyBloomFilter() {
        return new SimpleBloomFilter<Number160>(0.01d, 1000);
    }

    @Override
    public SimpleBloomFilter<Number160> createVersionKeyBloomFilter() {
        return new SimpleBloomFilter<Number160>(0.01d, 1000);
    }
    
    @Override
    public SimpleBloomFilter<Number160> createContentBloomFilter() {
        return new SimpleBloomFilter<Number160>(0.01d, 1000);
    }

}
