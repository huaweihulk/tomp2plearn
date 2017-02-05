/*
 * Copyright 2012 Thomas Bocek
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package p2p.p2p.builder;

import trunk.social.p2p.connection.*;
import trunk.social.p2p.futures.*;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.peers.IP;
import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.peers.PeerSocketAddress;
import trunk.social.p2p.utils.Utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class PingBuilder {
    private static final FuturePing FUTURE_PING_SHUTDOWN = new FuturePing().failed("Peer is shutting down.");

    private final Peer peer;

    private PeerAddress peerAddress;

    private InetAddress inetAddress;

    private int port = Ports.DEFAULT_PORT;

    private boolean tcpPing = false;
    
    private boolean broadcast = false;
    
    private PeerConnection peerConnection;

    private ConnectionConfiguration connectionConfiguration;

    public PingBuilder(Peer peer) {
        this.peer = peer;
    }
    
    public PingBuilder notifyAutomaticFutures(BaseFuture future) {
        this.peer.notifyAutomaticFutures(future);
        return this;
    }

    public PeerAddress getPeerAddress() {
        return peerAddress;
    }

    public PingBuilder peerAddress(PeerAddress peerAddress) {
        this.peerAddress = peerAddress;
        return this;
    }

    public InetAddress inetAddress() {
        return inetAddress;
    }

    public PingBuilder inetAddress(InetAddress inetAddress) {
        this.inetAddress = inetAddress;
        return this;
    }
    
    public PingBuilder inetSocketAddress(InetSocketAddress socket) {
    	this.inetAddress = socket.getAddress();
    	this.port = socket.getPort();
	    return this;
    }

    public int port() {
        return port;
    }

    public PingBuilder port(int port) {
        this.port = port;
        return this;
    }
    
    public boolean isBroadcast() {
        return broadcast;
    }

    public PingBuilder broadcast() {
        this.broadcast = true;
        return this;
    }

    public PingBuilder broadcast(boolean broadcast) {
        this.broadcast = broadcast;
        return this;
    }

    public boolean isTcpPing() {
        return tcpPing;
    }

    public PingBuilder tcpPing() {
        this.tcpPing = true;
        return this;
    }

    public PingBuilder tcpPing(boolean tcpPing) {
        this.tcpPing = tcpPing;
        return this;
    }
    
    public PingBuilder peerConnection(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
        return this;
    }
    
    public PeerConnection peerConnection() {
        return peerConnection;
    }

    public FuturePing start() {
        if (peer.isShutdown()) {
            return FUTURE_PING_SHUTDOWN;
        }

        if (connectionConfiguration == null) {
            connectionConfiguration = new DefaultConnectionConfiguration();
        }

        if (broadcast) {
            return pingBroadcast(port);
        }
        
		if (peerAddress != null) {
			if (tcpPing) {
				return ping(peerAddress, false);
			} else {
				return ping(peerAddress, true);
			}
		} else if (inetAddress != null) {
			if (tcpPing) {
				return ping(inetAddress, port, Number160.ZERO, false);
			} else {
				return ping(inetAddress, port, Number160.ZERO, true);
			}
		} else if (peerConnection != null) {
			return pingPeerConnection(peerConnection);
		} else {
                throw new IllegalArgumentException("Cannot ping. Peer address or inet address required.");
		}
    }

    

    /**
     * Pings a peer.
     * 
     * @param address
     *            The address of the remote peer.
     * @param isUDP
     *            Set to true if UDP should be used, false for TCP.
     * @return The future response
     */
    private FuturePing ping(final InetAddress address, int port, final Number160 peerId, final boolean isUDP) {
    	PeerSocketAddress.PeerSocket4Address psa = PeerSocketAddress.PeerSocket4Address.builder().ipv4(IP.IPv4.fromInet4Address(address)).tcpPort(port).udpPort(port).build();
    	PeerAddress peerAddress = PeerAddress.builder().ipv4Socket(psa).peerId(peerId).build();
        return ping(peerAddress, isUDP);
    }
    
    /**
     * Pings a peer.
     * 
     * @param peerAddress
     *            The peer address of the remote peer.
     * @param isUDP
     *            Set to true if UDP should be used, false for TCP.
     * @return The future response
     */
    private FuturePing ping(PeerAddress peerAddress, final boolean isUDP) {
    	final FuturePing futurePing = new FuturePing();
        final RequestHandler<FutureResponse> request = peer.pingRPC().ping(peerAddress, connectionConfiguration);
        if (isUDP) {
            FutureChannelCreator fcc = peer.connectionBean().reservation().create(1, 0);
            Utils.addReleaseListener(fcc, futurePing);
            fcc.addListener(new BaseFutureAdapter<FutureChannelCreator>() {
                @Override
                public void operationComplete(final FutureChannelCreator future) throws Exception {
                    if (future.isSuccess()) {
                        FutureResponse futureResponse = request.sendUDP(future.channelCreator());
                        addPingListener(futurePing, futureResponse);
                    } else {
                    	futurePing.failed(future);
                    }
                }

				
            });
        } else {
            FutureChannelCreator fcc = peer.connectionBean().reservation().create(0, 1);
            Utils.addReleaseListener(fcc, futurePing);
            fcc.addListener(new BaseFutureAdapter<FutureChannelCreator>() {
                @Override
                public void operationComplete(final FutureChannelCreator future) throws Exception {
                    if (future.isSuccess()) {
                        FutureResponse futureResponse = request.sendTCP(future.channelCreator());
                        addPingListener(futurePing, futureResponse);
                    } else {
                    	futurePing.failed(future);
                    }
                }
            });
        }
        return futurePing;
    }
    
    private FuturePing pingBroadcast(final int port) {
    	final FuturePing futurePing = new FuturePing();
        final DiscoverResults discoverResults = peer.connectionBean().channelServer().discoverNetworks().currentDiscoverResults();
        final int size = discoverResults.existingBroadcastAddresses().size();
        final FutureLateJoin<FutureResponse> futureLateJoin = new FutureLateJoin<FutureResponse>(size, 1);
        if (size > 0) {
            FutureChannelCreator fcc = peer.connectionBean().reservation().create(size, 0);
            Utils.addReleaseListener(fcc, futurePing);
            fcc.addListener(new BaseFutureAdapter<FutureChannelCreator>() {
                @Override
                public void operationComplete(FutureChannelCreator future) throws Exception {
                    if (future.isSuccess()) {
                        addPingListener(futurePing, futureLateJoin);
                        for (InetAddress broadcastAddress: discoverResults.existingBroadcastAddresses()) {
                        	
                        	PeerSocketAddress.PeerSocket4Address psa = PeerSocketAddress.PeerSocket4Address.builder().ipv4(IP.IPv4.fromInet4Address(broadcastAddress)).tcpPort(port).udpPort(port).build();
                        	PeerAddress peerAddress = PeerAddress.builder().ipv4Socket(psa).peerId(Number160.ZERO).build();
                        	FutureResponse validBroadcast = peer.pingRPC().pingBroadcastUDP(
                                    peerAddress, future.channelCreator(), connectionConfiguration);
                            if (!futureLateJoin.add(validBroadcast)) {
                                // the latejoin future is finished if the add returns false
                                break;
                            }
                        }
                    } else {
                    	futurePing.failed(future);
                    }
                }		
            });
        } else {
        	futurePing.failed("No broadcast address found. Cannot ping nothing");
        }
        return futurePing;
    }
    
    private FuturePing pingPeerConnection(final PeerConnection peerConnection) {
    	final FuturePing futurePing = new FuturePing();
    	
        final RequestHandler<FutureResponse> request = peer.pingRPC().ping(
                peerConnection.remotePeer(), connectionConfiguration);
        FutureChannelCreator futureChannelCreator = peerConnection.acquire(request.futureResponse());
        futureChannelCreator.addListener(new BaseFutureAdapter<FutureChannelCreator>() {

            @Override
            public void operationComplete(FutureChannelCreator future) throws Exception {
                if(future.isSuccess()) {
                    request.futureResponse().request().keepAlive(true);
                    FutureResponse futureResponse = request.sendTCP(peerConnection);
                    addPingListener(futurePing, futureResponse);
                } else {
                	futurePing.failed(future);
                }
            }
        });
        return futurePing;
    }
    
    
    
    private void addPingListener(final FuturePing futurePing, final BaseFuture baseFuture) {
    	baseFuture.addListener(new BaseFutureAdapter<FutureResponse>() {
        	@Override
            public void operationComplete(FutureResponse future) throws Exception {
                if(future.isSuccess()) {
                	futurePing.done(future.responseMessage().sender());
                } else {
                	futurePing.failed(future);
                }
                
            }
        });
    }

	
}
