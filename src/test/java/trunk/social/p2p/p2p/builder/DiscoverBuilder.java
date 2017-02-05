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

package trunk.social.p2p.p2p.builder;

import java.net.InetAddress;
import java.util.Collection;

import trunk.social.p2p.connection.Bindings;
import trunk.social.p2p.connection.ChannelCreator;
import trunk.social.p2p.connection.ConnectionConfiguration;
import trunk.social.p2p.connection.DefaultConnectionConfiguration;
import trunk.social.p2p.connection.DiscoverNetworks;
import trunk.social.p2p.connection.DiscoverResults;
import trunk.social.p2p.connection.Ports;
import trunk.social.p2p.futures.BaseFutureAdapter;
import trunk.social.p2p.futures.FutureChannelCreator;
import trunk.social.p2p.futures.FutureDiscover;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.futures.FutureResponse;
import trunk.social.p2p.futures.Futures;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.p2p.PeerReachable;
import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.utils.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.message.Message;
import trunk.social.p2p.peers.IP;
import trunk.social.p2p.peers.PeerSocketAddress;

public class DiscoverBuilder {
    final private static Logger LOG = LoggerFactory.getLogger(DiscoverBuilder.class);

    final private static FutureDiscover FUTURE_DISCOVER_SHUTDOWN = new FutureDiscover()
            .failed("Peer is shutting down.");

    final private Peer peer;

    private InetAddress inetAddress;

    private int portUDP = Ports.DEFAULT_PORT;
    private int portTCP = Ports.DEFAULT_PORT;
    //private int portUDT = Ports.DEFAULT_PORT + 1;

    private PeerAddress peerAddress;

    private int discoverTimeoutSec = 5;

    private ConnectionConfiguration configuration;
    
    private FutureDiscover futureDiscover;
    
    private boolean expectManualForwarding;

    public DiscoverBuilder(Peer peer) {
        this.peer = peer;
    }

    public InetAddress inetAddress() {
        return inetAddress;
    }

    public DiscoverBuilder inetAddress(InetAddress inetAddress) {
        this.inetAddress = inetAddress;
        return this;
    }
    
    public DiscoverBuilder inetSocketAddress(InetAddress inetAddress, int port) {
        this.inetAddress = inetAddress;
        this.portTCP = port;
        this.portUDP = port;
        return this;
    }
    
    public DiscoverBuilder inetSocketAddress(InetAddress inetAddress, int portTCP, int portUDP) {
        this.inetAddress = inetAddress;
        this.portTCP = portTCP;
        this.portUDP = portUDP;
        return this;
    }
    
    public DiscoverBuilder peerSocketAddress(PeerSocketAddress.PeerSocket4Address peerSocketAddress) {
    	 this.inetAddress = peerSocketAddress.ipv4().toInetAddress();
         this.portTCP = peerSocketAddress.tcpPort();
         this.portUDP = peerSocketAddress.udpPort();
         return this;
	}

    public int portUDP() {
        return portUDP;
    }

    public DiscoverBuilder portUDP(int portUDP) {
        this.portUDP = portUDP;
        return this;
    }

    public int portTCP() {
        return portTCP;
    }

    public DiscoverBuilder portTCP(int portTCP) {
        this.portTCP = portTCP;
        return this;
    }

    public DiscoverBuilder ports(int port) {
        this.portTCP = port;
        this.portUDP = port;
        return this;
    }

    public PeerAddress peerAddress() {
        return peerAddress;
    }

    public DiscoverBuilder peerAddress(PeerAddress peerAddress) {
        this.peerAddress = peerAddress;
        return this;
    }

    public int discoverTimeoutSec() {
        return discoverTimeoutSec;
    }

    public DiscoverBuilder discoverTimeoutSec(int discoverTimeoutSec) {
        this.discoverTimeoutSec = discoverTimeoutSec;
        return this;
    }
    
    public FutureDiscover futureDiscover() {
        return futureDiscover;
    }

    public DiscoverBuilder futureDiscover(FutureDiscover futureDiscover) {
        this.futureDiscover = futureDiscover;
        return this;
    }
    
    public boolean isExpectManualForwarding() {
        return expectManualForwarding;
    }

    public DiscoverBuilder expectManualForwarding() {
        return setExpectManualForwarding(true);
    }
    
    public DiscoverBuilder setExpectManualForwarding(boolean expectManualForwarding) {
        this.expectManualForwarding = expectManualForwarding;
        return this;
    }

    public FutureDiscover start() {
        if (peer.isShutdown()) {
            return FUTURE_DISCOVER_SHUTDOWN;
        }

        if (peerAddress == null && inetAddress != null) {
        	PeerSocketAddress.PeerSocket4Address psa = PeerSocketAddress.PeerSocket4Address.builder().ipv4(IP.IPv4.fromInet4Address(inetAddress)).tcpPort(portTCP).udpPort(portUDP).build();
        	peerAddress = PeerAddress.builder().ipv4Socket(psa).peerId(Number160.ZERO).build();
        }
        if (peerAddress == null) {
            throw new IllegalArgumentException("Peer address or inet address required.");
        }
        if (configuration == null) {
            configuration = new DefaultConnectionConfiguration();
        }
        if (futureDiscover == null) {
        	futureDiscover = new FutureDiscover();
        }
        return discover(peerAddress, configuration, futureDiscover);
    }

    /**
     * Discover attempts to find the external IP address of this peer. This is done by first trying to set UPNP with
     * port forwarding (gives us the external address), query UPNP for the external address, and pinging a well known
     * peer. The fallback is NAT-PMP.
     * 
     * @param peerAddress
     *            The peer address. Since pings are used the peer ID can be Number160.ZERO
     * @return The future discover. This future holds also the real ID of the peer we send the discover request
     */
    private FutureDiscover discover(final PeerAddress peerAddress, final ConnectionConfiguration configuration, 
    		final FutureDiscover futureDiscover) {
        FutureChannelCreator fcc = peer.connectionBean().reservation().create(1, 2);
        Utils.addReleaseListener(fcc, futureDiscover);
        fcc.addListener(new BaseFutureAdapter<FutureChannelCreator>() {
            @Override
            public void operationComplete(final FutureChannelCreator future) throws Exception {
                if (future.isSuccess()) {
                    discover(futureDiscover, peerAddress, future.channelCreator(), configuration);
                } else {
                    futureDiscover.failed(future);
                }
            }
        });
        return futureDiscover;
    }

    /**
     * Needs 3 connections. Cleans up ChannelCreator, which means they will be released.
     * 
     * @param peerAddress
     * @param cc
     * @return
     */
    private void discover(final FutureDiscover futureDiscover, final PeerAddress peerAddress,
                          final ChannelCreator cc, final ConnectionConfiguration configuration) {
    	LOG.debug("starting discover to {}",peerAddress);
    	final FutureDone<Void> pingDone = new FutureDone<Void>();

        peer.pingRPC().addPeerReachableListener(new PeerReachable() {
            private volatile boolean changedUDP = false;
            private volatile boolean changedTCP = false;

            @Override
            public void peerWellConnected(final PeerAddress peerAddress, final PeerAddress reporter, final boolean tcp) {
            	pingDone.addListener(new BaseFutureAdapter<FutureDone<Void>>() {
					@Override
					public void operationComplete(FutureDone<Void> future) throws Exception {
						if (tcp) {
		            		futureDiscover.discoveredTCP();
		            		changedTCP = true;
		            		LOG.debug("TCP discovered");
		            	} else {
		            		futureDiscover.discoveredUDP();
		            		changedUDP = true;
		            		LOG.debug("UDP discovered");
		            	}
						if (changedTCP && changedUDP) {
		                    futureDiscover.done(peerAddress, reporter);
		                }
					}
				});
                
            }
        });

        final FutureResponse futureResponseTCP = peer.pingRPC().pingTCPDiscover(peerAddress, cc,
                configuration);
        
        futureResponseTCP.addListener(new BaseFutureAdapter<FutureResponse>() {
            @Override
            public void operationComplete(FutureResponse future) throws Exception {
                PeerAddress serverAddress = peer.peerBean().serverPeerAddress();
                if (futureResponseTCP.isSuccess() && futureResponseTCP.responseMessage().type() == Message.Type.NOT_FOUND) {
                	//this was a ping to myself. This is pointless
                	futureDiscover.failed("FutureDiscover to yourself",
                            futureResponseTCP);
                    return;
                }
                else if (futureResponseTCP.isSuccess()) {
                	//now we know our internal address, set it as it could be a wrong one, e.g. 127.0.0.1
                	serverAddress = serverAddress.withIpv4Socket(futureResponseTCP.responseMessage().recipient().ipv4Socket());
                	
                    Collection<PeerAddress> tmp = futureResponseTCP.responseMessage().neighborsSet(0)
                            .neighbors();
                    futureDiscover.reporter(futureResponseTCP.responseMessage().sender());
                    if (tmp.size() == 1) {
                        PeerAddress seenAs = tmp.iterator().next();
                        LOG.info("This peer is seen as {} by peer {}. This peer sees itself as {}.",
                                seenAs, peerAddress, peer.peerAddress());
                        if (!peer.peerAddress().ipv4Socket().equalsWithoutPorts(seenAs.ipv4Socket())) {
                            // check if we have this interface in that we can
                            // listen to
                            Bindings bindings2 = new Bindings().addAddress(seenAs.ipv4Socket().ipv4().toInetAddress());
                          
                            DiscoverResults discoverResults = DiscoverNetworks.discoverInterfaces(bindings2);
                            String status = discoverResults.status();
                            LOG.info("2nd interface discovery: {}", status);
                            if (discoverResults.newAddresses().size() > 0
                                    && discoverResults.newAddresses().contains(seenAs.ipv4Socket().ipv4().toInetAddress())) {
                                serverAddress = serverAddress.withIpv4Socket(seenAs.ipv4Socket());
                                peer.peerBean().serverPeerAddress(serverAddress);
                                LOG.info("This peer had the wrong interface. Changed it to {}.", serverAddress);
                            } else {
                                // now we know our internal IP, where we receive
                                // packets
                                final Ports ports = peer.connectionBean().channelServer().channelServerConfiguration().portsForwarding();
                                if (ports.isManualPort()) {
                                	final PeerAddress serverAddressOrig = serverAddress;
                                	PeerSocketAddress.PeerSocket4Address serverSocket = serverAddress.ipv4Socket();
                                	serverSocket = serverSocket.withTcpPort(ports.tcpPort()).withUdpPort(ports.udpPort());
                                	serverSocket = serverSocket.withIpv4(seenAs.ipv4Socket().ipv4());
                                    //manual port forwarding detected, set flag
                                    peer.peerBean().serverPeerAddress(serverAddress.withIpv4Socket(serverSocket).withIpInternalSocket(serverAddressOrig.ipv4Socket()));
                                    LOG.info("manual ports, change it to: {}", serverAddress);
                                } else if(expectManualForwarding) {
                                	final PeerAddress serverAddressOrig = serverAddress;
                                	PeerSocketAddress.PeerSocket4Address serverSocket = serverAddress.ipv4Socket();
                                	serverSocket = serverSocket.withIpv4(seenAs.ipv4Socket().ipv4());
                                	peer.peerBean().serverPeerAddress(serverAddress.withIpv4Socket(serverSocket).withIpInternalSocket(serverAddressOrig.ipv4Socket()));
                                    LOG.info("we were manually forwarding, change it to: {}", serverAddress);
                                }
                                else {
                                    // we need to find a relay, because there is a NAT in the way.
                                	// we cannot use futureResponseTCP.responseMessage().recipient() as this may return also IPv6 addresses
                                	LOG.info("We are most likely behind NAT, try to UPNP, NATPMP or relay. PeerAddress: {}, ServerAddress: {}, Seen as: {}" + peerAddress, serverAddress, seenAs);
                                    futureDiscover.externalHost("We are most likely behind NAT, try to UPNP, NATPMP or relay. Using peerAddress " + peerAddress, serverAddress.ipv4Socket(), seenAs.ipv4Socket());
                                    return;
                                }
                            }
                        }
                        // else -> we announce exactly how the other peer sees
                        // us
                        FutureResponse fr1 = peer.pingRPC().pingTCPProbe(peerAddress, cc,
                                configuration);
                        fr1.addListener(new BaseFutureAdapter<FutureResponse>() {
							@Override
                            public void operationComplete(FutureResponse future) throws Exception {
	                            if(future.isFailed() ) {
	                            	LOG.warn("FutureDiscover (2): We need at least the TCP connection {} - {}", future, futureDiscover.failedReason());
	                            	futureDiscover.failed("FutureDiscover (2): We need at least the TCP connection", future);
	                            }
                            }
						});
                        FutureResponse fr2 = peer.pingRPC().pingUDPProbe(peerAddress, cc,
                                configuration);
                        fr2.addListener(new BaseFutureAdapter<FutureResponse>() {
							@Override
                            public void operationComplete(FutureResponse future) throws Exception {
	                            if(future.isFailed() ) {
	                            	LOG.warn("FutureDiscover (2): UDP failed connection {} - {}", future, futureDiscover.failedReason());
	                            }
                            }
						});
                        Futures.whenAll(fr1, fr2).addListener(new BaseFutureAdapter<FutureDone<FutureResponse[]>>() {
							@Override
							public void operationComplete(FutureDone<FutureResponse[]> future) throws Exception {
								pingDone.done();
							}
						});
                        // from here we probe, set the timeout here
                        futureDiscover.timeout(serverAddress, peer.connectionBean().timer(), discoverTimeoutSec);
                        return;
                    } else {
                        futureDiscover.failed("Peer " + peerAddress + " did not report our IP address.");
                        return;
                    }
                } else {
                    futureDiscover.failed("FutureDiscover (1): We need at least the TCP connection",
                            futureResponseTCP);
                    return;
                }
            }
        });
    }
}
