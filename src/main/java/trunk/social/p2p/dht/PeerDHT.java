package trunk.social.p2p.dht;

import trunk.social.p2p.connection.PeerBean;
import trunk.social.p2p.futures.BaseFuture;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.PeerAddress;

public class PeerDHT {

	final private Peer peer;
	final private StorageRPC storageRPC;
	final private DistributedHashTable dht;
	final private StorageLayer storageLayer;

	PeerDHT(final Peer peer, final StorageLayer storageLayer, final DistributedHashTable dht, final StorageRPC storageRPC) {
		this.peer = peer;
		this.storageLayer = storageLayer;
		this.dht = dht;
		this.storageRPC = storageRPC;
		peer.addShutdownListener(new Shutdown() {
			@Override
			public BaseFuture shutdown() {
				storageLayer.close();
				return FutureDone.SUCCESS;
			}
		});
    }

	public Peer peer() {
		return peer;
	}

	public StorageRPC storeRPC() {
		return storageRPC;
	}

	public DistributedHashTable distributedHashTable() {
		return dht;
	}
	
	public StorageLayer storageLayer() {
		return storageLayer;
	}

	public AddBuilder add(Number160 locationKey) {
		return new AddBuilder(this, locationKey);
	}

	public PutBuilder put(Number160 locationKey) {
		return new PutBuilder(this, locationKey);
	}

	public GetBuilder get(Number160 locationKey) {
		return new GetBuilder(this, locationKey);
	}

	public DigestBuilder digest(Number160 locationKey) {
		return new DigestBuilder(this, locationKey);
	}

	public RemoveBuilder remove(Number160 locationKey) {
		return new RemoveBuilder(this, locationKey);
	}

	/**
	 * The send method works as follows:
	 * 
	 * <pre>
	 * 1. routing: find close peers to the content hash. 
	 *    You can control the routing behavior with 
	 *    setRoutingConfiguration() 
	 * 2. sending: send the data to the n closest peers. 
	 *    N is set via setRequestP2PConfiguration(). 
	 *    If you want to send it to the closest one, use 
	 *    setRequestP2PConfiguration(1, 5, 0)
	 * </pre>
	 * 
	 * @param locationKey
	 *            The target hash to search for during the routing process
	 * @return The send builder that allows to set options
	 */
	public SendBuilder send(Number160 locationKey) {
		return new SendBuilder(this, locationKey);
	}

	public ParallelRequestBuilder<?> parallelRequest(Number160 locationKey) {
		return new ParallelRequestBuilder<FutureDHT<?>>(this, locationKey);
	}

	// ----- convenience methods ------
	public BaseFuture shutdown() {
	    return peer.shutdown();
    }

	public PeerBean peerBean() {
	    return peer.peerBean();
    }

	public Number160 peerID() {
	    return peer.peerID();
    }

	public PeerAddress peerAddress() {
	    return peer.peerAddress();
    }
}
