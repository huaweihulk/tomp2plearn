package trunk.social.p2p.relay;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.connection.Dispatcher;
import trunk.social.p2p.connection.PeerConnection;
import trunk.social.p2p.connection.Responder;
import trunk.social.p2p.connection.SignatureFactory;
import trunk.social.p2p.futures.BaseFutureAdapter;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.futures.FuturePeerConnection;
import trunk.social.p2p.futures.FutureResponse;
import trunk.social.p2p.holep.HolePRPC;
import trunk.social.p2p.message.Buffer;
import trunk.social.p2p.message.Message;
import trunk.social.p2p.message.Message.Type;
import trunk.social.p2p.message.NeighborSet;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.peers.PeerSocketAddress;
import trunk.social.p2p.peers.PeerStatistic;
import trunk.social.p2p.rpc.DispatchHandler;
import trunk.social.p2p.rpc.RPC;
import trunk.social.p2p.rpc.RPC.Commands;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RelayRPC extends DispatchHandler {

	private static final Logger LOG = LoggerFactory.getLogger(RelayRPC.class);

	private final Peer peer;

	// holds the server for each client
	//private final Map<Number160, BaseRelayServer> servers;

	// holds the client for each server
	//private ConcurrentHashMap<Number160, BaseRelayClient> clients;
	

	/**
	 * This variable is needed, because a relay overwrites every RPC of an
	 * unreachable peer with another RPC called {@link RelayForwarderRPC}. This
	 * variable is forwarded to the {@link RelayForwarderRPC} in order to
	 * guarantee the existence of a {@link RconRPC}. Without this variable, no
	 * reverse connections would be possible.
	 * 
	 * @author jonaswagner
	 */
	private final RconRPC rconRPC;

	/**
	 * This variable is needed, because a relay overwrites every RPC of an
	 * unreachable peer with another RPC called {@link RelayForwarderRPC}. This
	 * variable is forwarded to the {@link RelayForwarderRPC} in order to
	 * guarantee the existence of a {@link HolePRPC}. Without this variable, no
	 * hole punch connections would be possible.
	 * 
	 * @author jonaswagner
	 */
	private final HolePRPC holePunchRPC;

	/**
	 * Register the RelayRPC. After the setup, the peer is ready to act as a
	 * relay if asked by an unreachable peer.
	 * 
	 * @param peer
	 *            The peer to register the RelayRPC
	 * @param rconRPC the reverse connection RPC
	 * @return
	 */
	
	final private int bufferTimeoutSeconds;
	final private int bufferSize;
	final private int heartBeatMillis;
	final private int idleTCP;
	
	public RelayRPC(Peer peer, RconRPC rconRPC, HolePRPC holePRPC, int bufferTimeoutSeconds, int bufferSize, int heartBeatMillis, int idleTCP) {
		super(peer.peerBean(), peer.connectionBean());
		this.peer = peer;
		//this.servers = new ConcurrentHashMap<Number160, BaseRelayServer>();
		//this.clients = new ConcurrentHashMap<Number160, BaseRelayClient>();
		this.rconRPC = rconRPC;
		this.holePunchRPC = holePRPC;
		this.bufferTimeoutSeconds = bufferTimeoutSeconds;
		this.bufferSize = bufferSize;
		this.heartBeatMillis = heartBeatMillis;
		this.idleTCP = idleTCP;

		// register this handler
		register(RPC.Commands.RELAY.getNr());
	}
	
	public FutureDone<PeerConnection> sendSetupMessage(final PeerAddress candidate) {
		final FutureDone<PeerConnection> futureDone = new FutureDone<PeerConnection>();

		final Message message = createMessage(candidate, RPC.Commands.RELAY.getNr(), Type.REQUEST_1);

		// depend on the relay type whether to keep the connection open or close it after the setup.
		message.keepAlive(true);

		LOG.debug("Setting up relay connection to peer {}, message {}", candidate, message);
		final FuturePeerConnection fpc = peer.createPeerConnection(candidate, heartBeatMillis, idleTCP);
		fpc.addListener(new BaseFutureAdapter<FuturePeerConnection>() {
			public void operationComplete(final FuturePeerConnection futurePeerConnection) throws Exception {
				if (futurePeerConnection.isSuccess()) {
					// successfully created a connection to the relay peer
					final PeerConnection peerConnection = futurePeerConnection.object();

					// send the message
					FutureResponse response = RelayUtils.send(peerConnection, peer.peerBean(), peer.connectionBean(), message);
					response.addListener(new BaseFutureAdapter<FutureResponse>() {
						public void operationComplete(FutureResponse future) throws Exception {
							if (future.isSuccess()) {
								LOG.debug("Peer {} accepted relay request", candidate);
								futureDone.done(peerConnection);
							} else {
								LOG.debug("Peer {} denied relay request", candidate);
								futureDone.failed(future);
							}
						}
					});
				} else {
					LOG.debug("Unable to setup a connection to relay peer {}", candidate);
					futureDone.failed(futurePeerConnection);
				}
			}
		});

		return futureDone;
	}
	
	public FutureResponse sendPeerMap(final PeerAddress relayPeer, PeerConnection peerConnection, 
			final List<Map<Number160, PeerStatistic>> map) {
		final Message message = createMessage(relayPeer, RPC.Commands.RELAY.getNr(), Type.REQUEST_3);

		NeighborSet ns = new NeighborSet(255, RelayUtils.flatten(map));
		message.neighborsSet(ns);
		LOG.debug("send neighbors " + ns);
		// append relay-type specific data (if necessary)
		//relayConfig.prepareMapUpdateMessage(message);
		message.keepAlive(true);
		FutureResponse response = RelayUtils.send(peerConnection, peer.peerBean(), peer.connectionBean(), message);
		return response;
	}

	/**
	 * Receive a message at the relay server and the relay client
	 */
	@Override
	public void handleResponse(final Message message, PeerConnection peerConnection, final boolean sign, Responder responder)
			throws Exception {
		LOG.debug("Received RPC message {}", message);
		if (message.type() == Type.REQUEST_1 && message.command() == RPC.Commands.RELAY.getNr()) {
			// The relay server received a setup request from an unreachable peer
			handleSetup(message, peerConnection, responder);
		} else if (message.type() == Type.REQUEST_2 && message.command() == RPC.Commands.RELAY.getNr()) {
			// The unreachable peer receives wrapped messages from the relay
			handlePiggyBackedMessage(message, responder);
		} else if (message.type() == Type.REQUEST_3 && message.command() == RPC.Commands.RELAY.getNr()) {
			// the relay server receives the update of the routing table regularly from the unreachable peer
			handleMap(message, responder);
		} else if (message.type() == Type.REQUEST_4 && message.command() == RPC.Commands.RELAY.getNr()) {
			// An unreachable peer requests the buffer at the relay peer
			// or a buffer is transmitted to the unreachable peer directly
			handleBuffer(message);
			responder.response(createResponseMessage(message, Type.OK));
		} else {
			throw new IllegalArgumentException("Message content is wrong");
		}
	}

	

	

	public Peer peer() {
		return this.peer;
	}

	/**
	 * Convenience method
	 * 
	 * @return the signature factory
	 */
	private SignatureFactory signatureFactory() {
		return connectionBean().channelServer().channelServerConfiguration().signatureFactory();
	}

	/**
	 * Convenience method
	 * 
	 * @return the dispatcher of this peer
	 */
	private Dispatcher dispatcher() {
		return peer().connectionBean().dispatcher();
	}

	/**
	 * @return all unreachable peers currently connected to this relay node
	 */
	/*public Set<PeerAddress> unreachablePeers() {
		Set<PeerAddress> unreachablePeers = new HashSet<PeerAddress>(servers.size());
		for (BaseRelayServer forwarder : servers.values()) {
			unreachablePeers.add(forwarder.unreachablePeerAddress());
		}
		return unreachablePeers;
	}*/

	/**
	 * Add a client to the list
	 */
	/*public void addClient(BaseRelayClient connection) {
		clients.put(connection.relayAddress().peerId(), connection);
	}*/

	/**
	 * Remove a client from the list
	 */
	/*public void removeClient(BaseRelayClient connection) {
		clients.remove(connection.relayAddress().peerId());
	}*/

	/**
	 * Handle the setup where an unreachable peer connects to this one
	 */
	private void handleSetup(Message message, final PeerConnection unreachablePeerConnectionOrig, final Responder responder) {
		final Number160 unreachablePeerId = unreachablePeerConnectionOrig.remotePeer().peerId();
		//add myself as relay
		Collection<PeerSocketAddress> psa = unreachablePeerConnectionOrig.remotePeer().relays();
		psa.addAll(peer().peerAddress().relays());
		
		final PeerConnection unreachablePeerConnectionCopy = unreachablePeerConnectionOrig.changeRemotePeer(
				unreachablePeerConnectionOrig.remotePeer().withRelays(psa));
		
		//now we can add this peer to the map, as we have now set the flag
		//its TCP, we have a connection to this peer, so mark it as first hand
		peerBean().notifyPeerFound(unreachablePeerConnectionCopy.remotePeer(), null, unreachablePeerConnectionCopy, null);
		final Forwarder forwarder = new Forwarder(peer, unreachablePeerConnectionCopy, message.sender().slow(), bufferTimeoutSeconds, bufferSize);
		for (Commands command : RPC.Commands.values()) {
			if (command == RPC.Commands.RCON) {
				// We must register the rconRPC for every unreachable peer that
				// we serve as a relay. Without this registration, no reverse
				// connection setup is possible.
				LOG.debug("register rcon for {}, {}",command.toString(), message);
				dispatcher().registerIoHandler(peer.peerID(), unreachablePeerId, rconRPC, command.getNr());
			} else if (command == RPC.Commands.HOLEP) {
				// We must register the holePunchRPC for every unreachable peer that
				// we serve as a relay. Without this registration, no reverse
				// connection setup is possible.
				LOG.debug("register holepunching for {}, {}",command.toString(), message);
				dispatcher().registerIoHandler(peer.peerID(), unreachablePeerId, holePunchRPC, command.getNr());
			} else if (command == RPC.Commands.RELAY) {
				// Register this class to handle all relay messages (currently used when a slow message
				// arrives)
				LOG.debug("register this for {}, {}",command.toString(), message);
				dispatcher().registerIoHandler(peer.peerID(), unreachablePeerId, this, command.getNr());
			} else {
				LOG.debug("register forwarder for {}, {}",command.toString(), message);
				dispatcher().registerIoHandler(peer.peerID(), unreachablePeerId, forwarder, command.getNr());
			}
		}
		responder.response(createResponseMessage(message, Type.OK));
	}

	/**
	 * The unreachable peer received an envelope message with another message inside (piggypacked)
	 */
	private void handlePiggyBackedMessage(Message message, final Responder responderToRelay) throws Exception {
		// TODO: check if we have right setup

		// this contains the real sender
		PeerSocketAddress peerSocketAddress  = message.peerSocket4Address(0);
		final InetSocketAddress sender;
		if (peerSocketAddress != null) {
			sender = peerSocketAddress.createTCPSocket();
		} else {
			sender = new InetSocketAddress(0);
		}

		Buffer requestBuffer = message.buffer(0);
		Message realMessage = RelayUtils.decodeRelayedMessage(requestBuffer.buffer(), message.recipientSocket(), sender,
				signatureFactory());
		realMessage.restoreContentReferences();

		LOG.debug("Received message from relay peer: {}", realMessage);

		final Message envelope = createResponseMessage(message, Type.OK);
		final Responder responder = new Responder() {
			// TODO: add reply leak handler
			@Override
			public FutureDone<Void> response(Message responseMessage) {
				final FutureDone<Void> futureDone = new FutureDone<Void>();
				LOG.debug("Send reply message to relay peer: {}", responseMessage);
				try {
					envelope.buffer(RelayUtils.encodeMessage(responseMessage, signatureFactory()));
				} catch (Exception e) {
					LOG.error("Cannot piggyback the response", e);
					futureDone.failed("Cannot piggyback the response");
					failed(Type.EXCEPTION, e.getMessage());
					return futureDone;
				}
				responderToRelay.response(envelope);
				futureDone.done();
				return futureDone;
			}

			@Override
			public void failed(Type type, String reason) {
				responderToRelay.failed(type, reason);
			}

			@Override
			public void responseFireAndForget() {
				responderToRelay.responseFireAndForget();
			}
		};

		DispatchHandler dispatchHandler = dispatcher().associatedHandler(realMessage);
		if (dispatchHandler == null) {
			responder.failed(Type.EXCEPTION, "handler not found, probably not relaying peer anymore");
		} else {
			dispatchHandler.handleResponse(realMessage, null, false, responder);
		}
	}

	/**
	 * Updates the peer map of an unreachable peer on the relay peer, so that
	 * the relay peer can respond to neighbor RPC on behalf of the unreachable
	 * peer
	 * 
	 * @param message
	 * @param responder
	 */
	private void handleMap(Message message, Responder responder) {
		LOG.debug("Handle foreign map update {}", message);
		
		final Forwarder forwarder = dispatcher().searchHandler(Forwarder.class, peer.peerAddress().peerId(), message.sender().peerId());		
		
		if (forwarder != null) {
			Collection<PeerAddress> map = message.neighborsSet(0).neighbors();
			Message response = createResponseMessage(message, Type.OK);
			List<Message> buffered = forwarder.buffered();
			if(buffered != null) {
				ByteBuf bb = RelayUtils.composeMessageBuffer(buffered, peer.connectionBean().sender().channelClientConfiguration().signatureFactory());
				response.buffer(new Buffer(bb));
			}
			forwarder.setPeerMap(RelayUtils.unflatten(map, message.sender()), message, response);
			responder.response(response);
		} else {
			LOG.error("No forwarder for peer {} found. Need to setup relay first");
			responder.response(createResponseMessage(message, Type.NOT_FOUND));
		}
	}
	
	public void handleBuffer(final Message message) throws InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, IOException {
		//the unreachable peer gets the buffered messages
		if(message.bufferList().isEmpty()) {
			return;
		}
		List<Message> buffered = RelayUtils.decomposeCompositeBuffer(
				message.buffer(0).buffer(), message.recipientSocket(), 
				message.senderSocket(), peer.connectionBean().sender().channelClientConfiguration().signatureFactory());
		LOG.debug("got {} messages", buffered.size());
		for(Message msg:buffered) {
			DispatchHandler dh = connectionBean().dispatcher().associatedHandler(msg);
			//TODO: add a custom responder and respond to the address found in the message
			dh.forwardMessage(msg, null, createResponder(peer, msg.sender().ipv4Socket()));
		}
		
	}

	private static Responder createResponder(final Peer peer, final PeerSocketAddress sender) {
		return new Responder() {
			
			@Override
			public void responseFireAndForget() {}
			
			@Override
			public FutureDone<Void> response(final Message responseMessage) {
				
				responseMessage.recipient(responseMessage.recipient().withIPSocket(sender));			

				LOG.debug("send late reply {}", responseMessage);
				final FutureResponse fr = RelayUtils.connectAndSend(peer, responseMessage);
				final FutureDone<Void> fd = new FutureDone<Void>();
				fr.addListener(new BaseFutureAdapter<FutureResponse>() {

					@Override
					public void operationComplete(FutureResponse future)
							throws Exception {
						fd.done();
					}
				});
				return fd;
			}
			
			@Override
			public void failed(Type type, String reason) {
				LOG.error("could not sent to peer. {}", reason);
			}
		};
	}
	
}
