package p2p.holep.strategy;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.util.concurrent.EventExecutorGroup;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.message.Message;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.utils.Pair;
import trunk.social.p2p.utils.Utils;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PortPreservingStrategy extends AbstractHolePStrategy {

	public PortPreservingStrategy(final Peer peer, final int numberOfHoles, final int idleUDPSeconds, final Message originalMessage) {
		super(peer, numberOfHoles, idleUDPSeconds, originalMessage);
	}

	@Override
	protected void doPortGuessingInitiatingPeer(final Message holePMessage, final FutureDone<Message> initMessageFutureDone,
			final List<ChannelFuture> channelFutures) throws Exception {
		final List<Integer> portList = new ArrayList<Integer>(channelFutures.size());
		for (int i = 0; i < channelFutures.size(); i++) {
			final InetSocketAddress inetSocketAddress = (InetSocketAddress) channelFutures.get(i).channel().localAddress();
			portList.add(inetSocketAddress.getPort());
		}
		holePMessage.intValue(portList.size());
		
		// send all ports via Buffer
		holePMessage.buffer(encodePortList(portList));

		initMessageFutureDone.done(holePMessage);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void doPortGuessingTargetPeer(final Message replyMessage, final FutureDone<Message> replyMessageFuture2) throws Exception {
		final List<Integer> remotePorts = (List<Integer>) Utils.decodeJavaObject(originalMessage.buffer(0).buffer());
		final List<Integer> replyPorts = new ArrayList<Integer>(channelFutures.size()*2);
		for (int i = 0; i < channelFutures.size(); i++) {
			final InetSocketAddress socket = (InetSocketAddress) channelFutures.get(i).channel().localAddress();
			portMappings.add(new Pair<Integer, Integer>(remotePorts.get(i), socket.getPort()));
			replyPorts.add(remotePorts.get(i));
			replyPorts.add(socket.getPort());
		}
		replyMessage.intValue(replyPorts.size());
		
		// send all ports via Buffer
		replyMessage.buffer(encodePortList(replyPorts));
		
		replyMessageFuture2.done(replyMessage);
	}

	/**
	 * This method is just for testing causes.
	 */
	@Override
	protected FutureDone<List<ChannelFuture>> createChannelFutures(
			final List<Map<String, Pair<EventExecutorGroup, ChannelHandler>>> handlersList, final FutureDone<Message> mainFutureDone,
			final int numberOfHoles) {
		return super.createChannelFutures(handlersList, mainFutureDone, numberOfHoles);
	}

	/**
	 * This method is just for testing causes.
	 */
	@Override
	protected List<Map<String, Pair<EventExecutorGroup, ChannelHandler>>> prepareHandlers(final boolean initiator, final FutureDone<Message> futureDone) {
		return super.prepareHandlers(initiator, futureDone);
	}

}
