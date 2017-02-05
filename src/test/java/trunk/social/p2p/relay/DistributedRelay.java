package trunk.social.p2p.relay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import trunk.social.p2p.connection.PeerConnection;
import trunk.social.p2p.connection.PeerException;
import trunk.social.p2p.connection.PeerException.AbortCause;
import trunk.social.p2p.futures.BaseFutureAdapter;
import trunk.social.p2p.futures.FutureBootstrap;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.futures.FutureResponse;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.p2p.builder.BootstrapBuilder;
import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.peers.PeerMapChangeListener;
import trunk.social.p2p.peers.PeerSocketAddress;
import trunk.social.p2p.peers.PeerStatistic;
import trunk.social.p2p.utils.ConcurrentCacheSet;

/**
 * The relay manager is responsible for setting up and maintaining connections
 * to relay peers and contains all information about the relays.
 * 
 * @author Raphael Voellmy
 * @author Thomas Bocek
 * @author Nico Rutishauser
 * 
 */
public class DistributedRelay implements PeerMapChangeListener {

	private final static Logger LOG = LoggerFactory.getLogger(DistributedRelay.class);

	private final Peer peer;
	private final RelayRPC relayRPC;

	//private final List<BaseRelayClient> relayClients;
	private final Set<PeerAddress> failedRelays;
	private final Map<PeerAddress, PeerConnection> activeClients;
	
	private FutureDone<Void> shutdownFuture = new FutureDone<Void>();
	
	private volatile boolean shutdown = false;
	private boolean allRelays = false;
		
	final private ExecutorService executorService;
	final private BootstrapBuilder bootstrapBuilder;
	final private RelayCallback relayCallback;
	private volatile List<PeerAddress> relays;
	

	/**
	 * @param peer
	 *            the unreachable peer
	 * @param relayRPC
	 *            the relay RPC
	 * @param maxFail 
	 * @param relayConfig 
	 * @param bootstrapBuilder 
	 * @param maxRelays
	 *            maximum number of relay peers to set up
	 * @param relayType
	 *            the kind of the relay connection
	 */
	public DistributedRelay(final Peer peer, RelayRPC relayRPC, ExecutorService executorService, BootstrapBuilder bootstrapBuilder, RelayCallback relayCallback) {
		this.peer = peer;
		this.relayRPC = relayRPC;
		this.executorService = executorService;
		this.bootstrapBuilder = bootstrapBuilder;
		this.relayCallback = relayCallback;

		activeClients = Collections.synchronizedMap(new HashMap<PeerAddress, PeerConnection>());
		failedRelays = new ConcurrentCacheSet<PeerAddress>(60);
		peer.peerBean().peerMap().addPeerMapChangeListener(this);
	}



	/**
	 * Returns connections to current relay peers
	 * 
	 * @return List of PeerAddresses of the relay peers (copy)
	 */
	public Map<PeerAddress, PeerConnection> activeClients() {
		synchronized (activeClients) {
			// make a copy
			return Collections.unmodifiableMap(new HashMap<PeerAddress, PeerConnection>(activeClients));
		}
	}

	/*public void addRelayListener(RelayListener relayListener) {
		synchronized (relayListeners) {
			relayListeners.add(relayListener);
		}
	}*/

	public FutureDone<Void> shutdown() {
		shutdown = true;
		peer.peerBean().peerMap().removePeerMapChangeListener(this);
		synchronized (activeClients) {
			for (Map.Entry<PeerAddress, PeerConnection> entry: activeClients.entrySet()) {
				entry.getValue().close();
			}
		}
		executorService.shutdown();
		synchronized (peer) {
			peer.notify();
		}
		relayCallback.onShutdown();
		return shutdownFuture;
	}

	/**
	 * Sets up relay connections to other peers. The number of relays to set up
	 * is determined by {@link PeerAddress#MAX_RELAYS} or passed to the
	 * constructor of this class. It is important that we call this after we
	 * bootstrapped and have recent information in our peer map.
	 * 
	 * @return RelayFuture containing a {@link DistributedRelay} instance
	 */
	public DistributedRelay setupRelays(List<PeerAddress> relays) {
		this.relays = Collections.synchronizedList(relays);
		executorService.submit(new Runnable() {
			@Override
			public void run() {
				try {
					startConnectionsLoop();
				} catch (Exception e) {
					relayCallback.onFailure(e);
				}
			}
		});
		return this;
	}
	
	private List<PeerAddress> relayCandidates() {
		final List<PeerAddress> relayCandidates;
		if (relays.isEmpty()) {
			// Get the neighbors of this peer that could possibly act as relays. Relay
			// candidates are neighboring peers that are not relayed themselves and have
			// not recently failed as relay or denied acting as relay.
			relayCandidates = peer.distributedRouting().peerMap().all();
		} else {
			synchronized (relays) {
				relayCandidates = new ArrayList<PeerAddress>(relays);	
			}
		}
		relayCandidates.removeAll(failedRelays);

		//filterRelayCandidates
		for (Iterator<PeerAddress> iterator = relayCandidates.iterator(); iterator.hasNext();) {
			PeerAddress candidate = iterator.next();

			// filter peers that are relayed themselves
			if (candidate.relaySize() > 0) {
				iterator.remove();
				continue;
			}

			//  Remove recently failed relays, peers that are relayed themselves and
			// peers that are already relays
			if (activeClients.containsKey(candidate)) {
				iterator.remove();
			}
		}
		LOG.trace("Found {} additional relay candidates: {}, failed are {}", relayCandidates.size(), relayCandidates, failedRelays);
		
		return relayCandidates;
	}
	
	/**
	 * The relay setup is called sequentially until the number of max relays is reached. If a peerconnection goes down, it will search for other relays
	 * @param relayCallback 
	 * @throws InterruptedException 
	 */
	
	
	private void startConnectionsLoop() throws InterruptedException {

		if(shutdown && activeClients.isEmpty()) {
			shutdownFuture.done();
			LOG.debug("shutting down, don't restart relays");
			return;
		}
		
		if(shutdown) {
			return;
		}
		
		if(activeClients.size() >= 5) {
			LOG.debug("we have enough relays");
			allRelays = true;
			relayCallback.onFullRelays(activeClients.size());
			updatePeerMap();
			//wait at most x seconds for a restart of the loop
			executorService.submit(new Runnable() {
				@Override
				public void run() { 
					try {
						synchronized (peer) {
							peer.wait(60 * 1000);
						}
						startConnectionsLoop();
					} catch (Exception e) {
						relayCallback.onFailure(e);
					}
				}
			});
			return;
		}
		
		//get candidates
		final List<PeerAddress> relayCandidates = relayCandidates();
		if(relayCandidates.isEmpty()) {
			LOG.debug("no more relays");
			relayCallback.onNoMoreRelays(activeClients.size());
			updatePeerMap();
			executorService.submit(new Runnable() {
				@Override
				public void run() { 
					try {
						synchronized (peer) {
							peer.wait(60 * 1000);
						}
						startConnectionsLoop();
					} catch (Exception e) {
						relayCallback.onFailure(e);
					}
				}
			});
			return;
		}
		
		final PeerAddress candidate = relayCandidates.get(0);
		final FutureDone<PeerConnection> futureDone = relayRPC.sendSetupMessage(candidate);
		futureDone.addListener(new BaseFutureAdapter<FutureDone<PeerConnection>>() {
			@Override
			public void operationComplete(final FutureDone<PeerConnection> future)
					throws Exception {
				
				if(future.isSuccess()) {
					LOG.debug("found relay: {}", candidate);
					activeClients.put(candidate, future.object());
					updatePeerAddress();
					relayCallback.onRelayAdded(candidate, future.object());
					
					future.object().closeFuture().addListener(new BaseFutureAdapter<FutureDone<Void>>() {
						@Override
						public void operationComplete(final FutureDone<Void> futureClose)
								throws Exception {
							
							LOG.debug("lost/offline relay: {}", candidate);
							//we need to notify our map, since we know this peer is offline, TODO: make this generic for all PeerConnections
							peer.peerBean().peerMap().peerFailed(candidate, new PeerException(AbortCause.SHUTDOWN, "remote open peer connection was closed"));
							failedRelays.add(future.object().remotePeer());
							
							activeClients.remove(candidate);
							updatePeerAddress();
							relayCallback.onRelayRemoved(candidate, future.object());
							
							//notify to loop now - this may not do anything if we are shutting down
							synchronized (peer) {
								allRelays = false;
								peer.notify();
							}
							
							if(shutdown && activeClients.isEmpty()) {
								shutdownFuture.done();
							}
						}
					});
				} else {
					LOG.debug("bad relay: {}", candidate);
					failedRelays.add(candidate);
					activeClients.remove(candidate);
					updatePeerAddress();
					relayCallback.onRelayRemoved(candidate, future.object());
					
				}
				//loop again
				startConnectionsLoop();
			}
		});		
	}

	/**
	 * Updates the peer's PeerAddress: Adds the relay addresses to the peer
	 * address, updates the firewalled flags, and bootstraps to announce its new
	 * relay peers.
	 */
	private void updatePeerAddress() {
		final boolean hasRelays;
		final Collection<PeerSocketAddress> socketAddresses;
		synchronized (activeClients) {
			// add relay addresses to peer address
			hasRelays = !activeClients.isEmpty();
			socketAddresses = new ArrayList<PeerSocketAddress>(activeClients.size());
		
			//we can have more than the max relay count in our active client list.
			int max = 5;
			int i = 0;
			for (PeerAddress relay : activeClients.keySet()) {
				
				if(relay.ipv4Flag()) {
					socketAddresses.add(relay.ipv4Socket());	
				}
				if(relay.ipv6Flag()) {
					socketAddresses.add(relay.ipv6Socket());	
				}
				
				if(i++ >= max) {
					break;
				}
			}
		}

		// update firewalled and isRelayed flags
		PeerAddress newAddress = peer.peerAddress().withRelays(socketAddresses); 
			
		peer.peerBean().serverPeerAddress(newAddress);
		LOG.debug("Updated peer address {}, isrelay = {}", newAddress, hasRelays);
	}

	@Override
	public void peerInserted(PeerAddress peerAddress, boolean verified) {
		LOG.debug("new peer added, go again "+peerAddress+ " / "+verified);
		synchronized (peer) {
			if(!allRelays) {
				peer.notify();
			}
		}
	}

	@Override
	public void peerRemoved(PeerAddress peerAddress,
			PeerStatistic storedPeerAddress) {}

	@Override
	public void peerUpdated(PeerAddress peerAddress,
			PeerStatistic storedPeerAddress) {}


	private void updatePeerMap() {
		// bootstrap to get updated peer map and then push it to the relay peers
		bootstrapBuilder.start().addListener(new BaseFutureAdapter<FutureBootstrap>() {
			@Override
			public void operationComplete(FutureBootstrap future)
					throws Exception {
				// send the peer map to the relays
				List<Map<Number160, PeerStatistic>> peerMapVerified = relayRPC.peer().peerBean().peerMap().peerMapVerified();
				for (final Map.Entry<PeerAddress, PeerConnection> entry : activeClients().entrySet()) {
					FutureResponse fr = relayRPC.sendPeerMap(entry.getKey(), entry.getValue(), peerMapVerified);
					//if we have buffered messages, send reply
					fr.addListener(new BaseFutureAdapter<FutureResponse>() {
						@Override
						public void operationComplete(FutureResponse future) throws Exception {
							if(future.isSuccess()) {
								relayRPC.handleBuffer(future.responseMessage());
							}
						}
					});
					LOG.debug("send peermap to {}", entry.getKey());
				}
			}
		});
	}
	
	/*@Override
	public FutureDone<Void> sendBufferRequest(String relayPeerId) {
		for (BaseRelayClient relayConnection : relayClients()) {
			String peerId = relayConnection.relayAddress().peerId().toString();
			if (peerId.equals(relayPeerId) && relayConnection instanceof BufferedRelayClient) {
				return ((BufferedRelayClient) relayConnection).sendBufferRequest();
			}
		}

		LOG.warn("No connection to relay {} found. Ignoring the message.", relayPeerId);
		return new FutureDone<Void>().failed("No connection to relay " + relayPeerId + " found");
	}*/
}
