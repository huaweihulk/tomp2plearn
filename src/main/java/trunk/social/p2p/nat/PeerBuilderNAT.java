package trunk.social.p2p.nat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.connection.PeerConnection;
import trunk.social.p2p.futures.BaseFuture;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.holep.HolePInitiatorImpl;
import trunk.social.p2p.holep.HolePRPC;
import trunk.social.p2p.holep.HolePScheduler;
import trunk.social.p2p.holep.strategy.HolePStrategy;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.p2p.builder.BootstrapBuilder;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.relay.DistributedRelay;
import trunk.social.p2p.relay.RconRPC;
import trunk.social.p2p.relay.RelayCallback;
import trunk.social.p2p.relay.RelayRPC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerBuilderNAT {

	private static final Logger LOG = LoggerFactory.getLogger(PeerBuilderNAT.class);
	private static final RelayCallback DEFAULT_RELAY_CALLBACK = new RelayCallback() {
		
		@Override
		public void onRelayRemoved(PeerAddress candidate, PeerConnection object) {}
		
		@Override
		public void onRelayAdded(PeerAddress candidate, PeerConnection object) {}

		@Override
		public void onFailure(Exception e) {}

		@Override
		public void onFullRelays(int activeRelays) {}

		@Override
		public void onNoMoreRelays(int activeRelays) {}

		@Override
		public void onShutdown() {}
	};
	
	final private Peer peer;

	private boolean manualPorts = false;
	
	private Boolean relayMaintenance;
	
	private RelayCallback relayCallback;
	
	private BootstrapBuilder bootstrapBuilder;
	
	private Integer peerMapUpdateIntervalSeconds;
	private Integer bufferTimeoutSeconds;
	private Integer bufferSize;
	private Integer heartBeatMillis;
	private Integer idleTCP;

	private static final int DEFAULT_NUMBER_OF_HOLEP_HOLES = 3;
	private int holePNumberOfHoles = DEFAULT_NUMBER_OF_HOLEP_HOLES;
	private static final int DEFAULT_NUMBER_OF_HOLE_PUNCHES = 3;
	private int holePNumberOfPunches = DEFAULT_NUMBER_OF_HOLE_PUNCHES;
	
	private ExecutorService executorService;

	public PeerBuilderNAT(Peer peer) {
		this.peer = peer;
	}

	public boolean isManualPorts() {
		return manualPorts;
	}

	public PeerBuilderNAT manualPorts() {
		return manualPorts(true);
	}

	public PeerBuilderNAT manualPorts(boolean manualPorts) {
		this.manualPorts = manualPorts;
		return this;
	}

	/**
	 * This method specifies the amount of holes, which shall be punched by the
	 * {@link HolePStrategy}.
	 * 
	 * @param holePNumberOfHoles
	 * @return this instance
	 */
	public PeerBuilderNAT holePNumberOfHoles(final int holePNumberOfHoles) {
		if (holePNumberOfHoles < 1 || holePNumberOfHoles > 100) {
			LOG.error("The specified number of holes is invalid. holePNumberOfHoles must be between 1 and 100. The value is changed back to the default value (which is currently "
					+ DEFAULT_NUMBER_OF_HOLEP_HOLES + ")");
			return this;
		} else {
			this.holePNumberOfHoles = holePNumberOfHoles;
			return this;
		}
	}

	public int holePNumberOfHoles() {
		return this.holePNumberOfHoles;
	}

	/**
	 * specifies how many times the hole will be punched (e.g. if
	 * holePNumberOfPunches = 3, then then all holes will be punched with dummy
	 * messages 3 times in a row with 1 second delay (see {@link HolePScheduler}
	 * ).
	 * 
	 * @param holePNumberOfPunches
	 * @return this instance
	 */
	public PeerBuilderNAT holePNumberOfHolePunches(final int holePNumberOfPunches) {
		this.holePNumberOfPunches = holePNumberOfPunches;
		return this;
	}

	public int holePNumberOfPunches() {
		return holePNumberOfPunches;
	}
	
	public RelayCallback relayCallback() {
		return relayCallback;
	}
	
	public PeerBuilderNAT relayCallback(RelayCallback relayCallback) {
		this.relayCallback = relayCallback;
		return this;
	}
	
	public ExecutorService executorService() {
		return executorService;
	}
	
	public PeerBuilderNAT executorService(ExecutorService executorService) {
		this.executorService = executorService;
		return this;
	}
	
	public Integer peerMapUpdateIntervalSeconds() {
		return peerMapUpdateIntervalSeconds;
	}
	
	public PeerBuilderNAT peerMapUpdateIntervalSeconds(Integer peerMapUpdateIntervalSeconds) {
		this.peerMapUpdateIntervalSeconds = peerMapUpdateIntervalSeconds;
		return this;
	}
	
	public Integer bufferTimeoutSeconds() {
		return bufferTimeoutSeconds;
	}
	
	public PeerBuilderNAT bufferTimeoutSeconds(Integer bufferTimeoutSeconds) {
		this.bufferTimeoutSeconds = bufferTimeoutSeconds;
		return this;
	}
	
	public Integer bufferSize() {
		return bufferSize;
	}
	
	public PeerBuilderNAT bufferSize(Integer bufferSize) {
		this.bufferSize = bufferSize;
		return this;
	}
	
	public Integer heartBeatMillis() {
		return heartBeatMillis;
	}
	
	public PeerBuilderNAT heartBeatMillis(Integer heartBeatMillis) {
		this.heartBeatMillis = heartBeatMillis;
		return this;
	}
	
	public Integer idleTCP() {
		return idleTCP;
	}
	
	public PeerBuilderNAT idleTCP(Integer idleTCP) {
		this.idleTCP = idleTCP;
		return this;
	}

	public PeerNAT start() {
		
		if(relayCallback == null) {
			relayCallback = DEFAULT_RELAY_CALLBACK;
		}
		
		if(relayMaintenance == null) {
			relayMaintenance = true;
		}
		
		if(bootstrapBuilder == null) {
			bootstrapBuilder = peer.bootstrap();
		}
		
		if(peerMapUpdateIntervalSeconds == null) {
			peerMapUpdateIntervalSeconds = 60;
		}
		
		if(bufferTimeoutSeconds == null) {
			bufferTimeoutSeconds = 60;
		}
		
		if(bufferSize == null) {
			bufferSize = 16;
		}
		
		if(heartBeatMillis == null) {
			heartBeatMillis = 2000;
		}
		
		if(idleTCP == null) {
			idleTCP = 5000;
		}
		
		
		final NATUtils natUtils = new NATUtils();
		final RconRPC rconRPC = new RconRPC(peer);
		final HolePRPC holePunchRPC = new HolePRPC(peer);
		
		peer.peerBean().holePunchInitiator(new HolePInitiatorImpl(peer));
		peer.peerBean().holePNumberOfHoles(holePNumberOfHoles);
		peer.peerBean().holePNumberOfPunches(holePNumberOfPunches);

		final RelayRPC relayRPC = new RelayRPC(peer, rconRPC, holePunchRPC, bufferTimeoutSeconds, bufferSize, heartBeatMillis, idleTCP);
		
		if(executorService == null) {
			executorService = Executors.newSingleThreadExecutor();
		}
		
		DistributedRelay distributedRelay = new DistributedRelay(peer, relayRPC, executorService, bootstrapBuilder, relayCallback);

		peer.addShutdownListener(new Shutdown() {
			@Override
			public BaseFuture shutdown() {
				natUtils.shutdown();
				return new FutureDone<Void>().done();
			}
		});

		return new PeerNAT(peer, natUtils, relayRPC, manualPorts, distributedRelay, relayMaintenance, bootstrapBuilder, peerMapUpdateIntervalSeconds);
	}
}
