package p2p.tracker;

import trunk.social.p2p.connection.ChannelCreator;
import trunk.social.p2p.connection.ConnectionConfiguration;
import trunk.social.p2p.futures.BaseFutureAdapter;
import trunk.social.p2p.futures.FutureChannelCreator;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.futures.FutureResponse;
import trunk.social.p2p.message.TrackerData;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.peers.Number320;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.utils.Utils;

public class PeerExchange {

	private final Peer peer;
	private final PeerExchangeRPC peerExchangeRPC;
	private final ConnectionConfiguration connectionConfiguration;

	public PeerExchange(final Peer peer, final PeerExchangeRPC peerExchangeRPC,
	        ConnectionConfiguration connectionConfiguration) {
		this.peer = peer;
		this.peerExchangeRPC = peerExchangeRPC;
		this.connectionConfiguration = connectionConfiguration;
	}

	public FutureDone<Void> peerExchange(final PeerAddress remotePeer, final Number320 key, final TrackerData data) {
		return peerExchange(remotePeer, key, data, connectionConfiguration);
	}

	public FutureDone<Void> peerExchange(final PeerAddress remotePeer, final Number320 key, final TrackerData data,
	        final ConnectionConfiguration connectionConfiguration) {
		final FutureDone<Void> futureDone = new FutureDone<Void>();
		FutureChannelCreator futureChannelCreator = peer.connectionBean().reservation().create(1, 0);
		Utils.addReleaseListener(futureChannelCreator, futureDone);
		futureChannelCreator.addListener(new BaseFutureAdapter<FutureChannelCreator>() {
			@Override
			public void operationComplete(FutureChannelCreator future) throws Exception {
				if (future.isSuccess()) {
					final ChannelCreator channelCreator = future.channelCreator();
					FutureResponse futureResponse = peerExchangeRPC.peerExchange(remotePeer, key, channelCreator, data,
					        connectionConfiguration);
					futureResponse.addListener(new BaseFutureAdapter<FutureResponse>() {
						@Override
						public void operationComplete(FutureResponse future) throws Exception {
							if (future.isSuccess()) {
								futureDone.done();
							} else {
								futureDone.failed(future);
							}
							channelCreator.shutdown();
						}
					});
				} else {
					futureDone.failed(future);
				}
			}
		});
		return futureDone;
	}
	
	public PeerExchangeRPC peerExchangeRPC() {
		return peerExchangeRPC;
	}
	
	
}
