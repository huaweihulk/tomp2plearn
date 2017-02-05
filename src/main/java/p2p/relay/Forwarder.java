package p2p.relay;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.connection.PeerConnection;
import trunk.social.p2p.connection.Responder;
import trunk.social.p2p.futures.BaseFutureAdapter;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.futures.FutureResponse;
import trunk.social.p2p.message.Buffer;
import trunk.social.p2p.message.Message;
import trunk.social.p2p.message.Message.Type;
import trunk.social.p2p.message.NeighborSet;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.peers.PeerMap;
import trunk.social.p2p.peers.PeerStatistic;
import trunk.social.p2p.rpc.DispatchHandler;
import trunk.social.p2p.rpc.NeighborRPC;
import trunk.social.p2p.rpc.RPC;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class Forwarder extends DispatchHandler {
	
	private final static Logger LOG = LoggerFactory.getLogger(Forwarder.class);
	private final static AtomicLong messageCounter = new AtomicLong();
	
	private final PeerConnection unreachablePeerConnection;
	private List<Map<Number160, PeerStatistic>> peerMap;
	private final boolean isSlow;
	
	private final List<Message> buffer = Collections.synchronizedList(new ArrayList<Message>());
	
	final private int bufferSize;
	final private int bufferTimeoutSeconds;
	private long lastAccess = System.currentTimeMillis();

	public Forwarder(Peer peer, PeerConnection unreachablePeerConnection, boolean isSlow, int bufferTimeoutSeconds, int bufferSize) {
		super(peer.peerBean(), peer.connectionBean());
		this.unreachablePeerConnection = unreachablePeerConnection;
		this.isSlow = isSlow;
		this.bufferTimeoutSeconds = bufferTimeoutSeconds;
		this.bufferSize = bufferSize;
	}
	
	private FutureDone<Message> forwardOrBuffer(final Message requestMessage) {
		if(isSlow) {
			final FutureDone<Message> futureDone = new FutureDone<Message>();
			Message fastReply = createResponseMessage(requestMessage, Type.PARTIALLY_OK);
			addToBuffer(requestMessage);
			return futureDone.done(fastReply);
			
		} else {
			return forwardToUnreachable(requestMessage);
		}
	}
	
	

	public FutureDone<Message> forwardToUnreachable(final Message message) {
		// Send message via direct message through the open connection to the unreachable peer
		LOG.debug("Sending {} to unreachable peer {}", message, unreachablePeerConnection.remotePeer());
		final Message envelope = createMessage(unreachablePeerConnection.remotePeer(), RPC.Commands.RELAY.getNr(), Type.REQUEST_2);
		try {
			message.restoreContentReferences();
			// add the message into the payload
			envelope.buffer(RelayUtils.encodeMessage(message, connectionBean().channelServer().channelServerConfiguration()
					.signatureFactory()));
		} catch (Exception e) {
			LOG.error("Cannot encode the message", e);
			return new FutureDone<Message>().failed(e);
		}

		// always keep the connection open
		envelope.keepAlive(true);

		// this will be read RelayRPC.handlePiggyBackMessage
		if(envelope.sender().ipv4Flag()) {
			envelope.peerSocket4Address(envelope.sender().ipv4Socket());
		} 
		if(envelope.sender().ipv6Flag()) {
			envelope.peerSocket6Address(envelope.sender().ipv6Socket());
		}

		// holds the message that will be returned to he requester
		final FutureDone<Message> futureDone = new FutureDone<Message>();

		// Forward a message through the open peer connection to the unreachable peer.
		FutureResponse fr = RelayUtils.send(unreachablePeerConnection, peerBean(), connectionBean(), envelope);
		fr.addListener(new BaseFutureAdapter<FutureResponse>() {
			public void operationComplete(FutureResponse future) throws Exception {
				if (future.isSuccess()) {
					InetSocketAddress senderSocket = message.recipientSocket();
					if (senderSocket == null) {
						senderSocket = unreachablePeerConnection.remotePeer().ipv4Socket().createTCPSocket();
					}
					InetSocketAddress recipientSocket = message.senderSocket();
					if (recipientSocket == null) {
						recipientSocket = message.sender().ipv4Socket().createTCPSocket();
					}

					Buffer buffer = future.responseMessage().buffer(0);
					Message responseFromUnreachablePeer = RelayUtils.decodeMessage(buffer.buffer(), recipientSocket,
							senderSocket, connectionBean().channelServer().channelServerConfiguration().signatureFactory());
					responseFromUnreachablePeer.restoreContentReferences();
					futureDone.done(responseFromUnreachablePeer);
				} else {
					futureDone.failed("Could not forward message over TCP channel");
				}
			}
		});

		return futureDone;
	}

	@Override
	public void handleResponse(Message message, PeerConnection peerConnection,
			boolean sign, final Responder responder) throws Exception {
		// special treatment for ping and neighbor
				if (message.command() == RPC.Commands.PING.getNr()) {
					LOG.debug("Received message {} to handle ping for unreachable peer {}", message, unreachablePeerConnection.remotePeer());
					handlePing(message, responder);
				} else if (message.command() == RPC.Commands.NEIGHBOR.getNr()) {
					LOG.debug("Received message {} to handle neighbor request for unreachable peer {}", message, unreachablePeerConnection.remotePeer());
					handleNeigbhor(message, responder);
				} else {
					messageCounter.incrementAndGet();
					LOG.debug("Received message {} to forward to unreachable peer 1 {}", message, unreachablePeerConnection.remotePeer());
					FutureDone<Message> response = forwardOrBuffer(message);
					response.addListener(new BaseFutureAdapter<FutureDone<Message>>() {
						@Override
						public void operationComplete(FutureDone<Message> future) throws Exception {
							if (future.isSuccess()) {
								Message answerMessage = future.object();
								LOG.debug("Returning from relay to requester: 1 {}", answerMessage);
								responder.response(answerMessage);
							} else {
								responder.failed(Type.DENIED, "Relaying message failed: 1 " + future.failedReason());
							}
						}
					});
				}
		
	}
	
	public void handleForward(final Message forwardMessage, final Message message, final Responder responder) {
		messageCounter.incrementAndGet();
		LOG.debug("Received message {} to forward to unreachable peer  {}, orig: {}", forwardMessage, unreachablePeerConnection.remotePeer(), message);
		final FutureDone<Message> response = forwardOrBuffer(forwardMessage);
		response.addListener(new BaseFutureAdapter<FutureDone<Message>>() {
			@Override
			public void operationComplete(FutureDone<Message> future) throws Exception {
				if (future.isSuccess()) {
					final Message answerMessage = createResponseMessage(message, Type.OK);
					LOG.debug("Returning from relay to requester: 2 {}", answerMessage);
					responder.response(answerMessage);
				} else {
					responder.failed(Type.DENIED, "Relaying message failed: 2 " + future.failedReason());
				}
			}
		});
		
	}
	
	/**
	 * When a ping message is received
	 * 
	 * @param message
	 * @param responder
	 */
	private void handlePing(Message message, Responder responder) {
		Message response = createResponseMessage(message, unreachablePeerConnection.isOpen() ? Type.OK : Type.DENIED, unreachablePeerConnection.remotePeer());
		responder.response(response);
	}
	
	/**
	 * When a neighbor message is received
	 * 
	 * @param message
	 * @param responder
	 */
	private void handleNeigbhor(final Message message, Responder responder) {
		if (message.keyList().size() < 2) {
			throw new IllegalArgumentException("We need the location and domain key at least");
		}
		if (!(message.type() == Type.REQUEST_1 || message.type() == Type.REQUEST_2 || message.type() == Type.REQUEST_3 || message
				.type() == Type.REQUEST_4) && (message.command() == RPC.Commands.NEIGHBOR.getNr())) {
			throw new IllegalArgumentException("Message content is wrong");
		}
		Number160 locationKey = message.key(0);

		Collection<PeerAddress> neighbors = getNeighbors(locationKey, NeighborRPC.NEIGHBOR_SIZE);
		if (neighbors == null) {
			// return empty neighbor set
			Message response = createResponseMessage(message, Type.NOT_FOUND, unreachablePeerConnection.remotePeer());
			response.neighborsSet(new NeighborSet(-1, Collections.<PeerAddress> emptyList()));
			responder.response(response);
			return;
		}

		// Create response message and set neighbors
		final Message responseMessage = createResponseMessage(message, Type.OK, unreachablePeerConnection.remotePeer());

		// TODO: the relayed peer must be up-to-date here
		// neighbors.add(peerConnection.remotePeer());

		LOG.debug("found the following neighbors {}", neighbors);

		NeighborSet neighborSet = new NeighborSet(NeighborRPC.NEIGHBOR_LIMIT, neighbors);
		responseMessage.neighborsSet(neighborSet);

		// we can't do fast get here, as we only send over the neighbors and not the keys stored
		responder.response(responseMessage);
	}
	
	private SortedSet<PeerAddress> getNeighbors(Number160 id, int atLeast) {
		LOG.trace("Answering routing request on behalf of unreachable peer {}, neighbors of {}", unreachablePeerConnection.remotePeer(),
				id);
		if (peerMap == null) {
			return null;
		} else {
			SortedSet<PeerStatistic> closePeers = PeerMap.closePeers(unreachablePeerConnection.remotePeer().peerId(), id, NeighborRPC.NEIGHBOR_SIZE,
					peerMap, null);
			SortedSet<PeerAddress> result = new TreeSet<PeerAddress>(PeerMap.createXORAddressComparator(id));
			for (PeerStatistic p : closePeers) {
				result.add(p.peerAddress());
			}
			return result;
		}
	}

	/**
	 * Returns the current peer map from the mobile device
	 */
	public final Collection<PeerAddress> getPeerMap() {
		Collection<PeerAddress> peerAddresses = new ArrayList<PeerAddress>();
		if (peerMap == null || peerMap.isEmpty()) {
			return peerAddresses;
		}

		Collection<PeerStatistic> statistics = new ArrayList<PeerStatistic>();
		for (Map<Number160, PeerStatistic> map : peerMap) {
			statistics.addAll(map.values());
		}
		for (PeerStatistic peerStatistic : statistics) {
			peerAddresses.add(peerStatistic.peerAddress());
		}
		return peerAddresses;
	}
	
	/**
	 * Update the peerMap of the unreachable peer
	 * 
	 * @param peerMap the extracted peer map
	 * @param requestMessage the original message that contained the extracted peer map
	 * @param preparedResponse the response that will be sent to the unreachable peer
	 */
	public final void setPeerMap(List<Map<Number160, PeerStatistic>> peerMap, Message requestMessage,
			Message preparedResponse) {
		this.peerMap = peerMap;
		checkSend();
	}
	
	private void addToBuffer(Message requestMessage) {
		LOG.debug("add msg on peer {}, {}", peerBean().serverPeerAddress(), requestMessage);
		buffer.add(requestMessage);
		checkSend();
	}
	
	private void checkSend() {
		LOG.debug("check buffer on peer {}", peerBean().serverPeerAddress());
		if(buffer.size() > 0 && (buffer.size() > bufferSize || lastAccess + (bufferTimeoutSeconds * 1000) < System.currentTimeMillis())) {
			forwardMessages(buffer);
			lastAccess = System.currentTimeMillis();
		}
	}
	
	private void forwardMessages(List<Message> buffer2) {
		LOG.debug("empty buffer on peer {}", peerBean().serverPeerAddress());
		final Message envelope = createMessage(unreachablePeerConnection.remotePeer(), RPC.Commands.RELAY.getNr(), Type.REQUEST_4);
		
		// always keep the connection open
		envelope.keepAlive(true);
		ByteBuf bb = RelayUtils.composeMessageBuffer(buffered(), connectionBean().sender().channelClientConfiguration().signatureFactory());
		envelope.buffer(new Buffer(bb));
		
		// this will be read RelayRPC.handlePiggyBackMessage
		if(envelope.sender().ipv4Flag()) {
			envelope.peerSocket4Address(envelope.sender().ipv4Socket());
		} 
		if(envelope.sender().ipv6Flag()) {
			envelope.peerSocket6Address(envelope.sender().ipv6Socket());
		}

		// Forward a message through the open peer connection to the unreachable peer.
		RelayUtils.send(unreachablePeerConnection, peerBean(), connectionBean(), envelope);
	}

	public List<Message> buffered() {
		List<Message> retVal;
		synchronized (buffer) {
			retVal = new ArrayList<Message>(buffer);
		}
		buffer.clear();
		return retVal;
	}

	public PeerAddress unreachablePeerAddress() {
		return unreachablePeerConnection.remotePeer();
	}	
}
