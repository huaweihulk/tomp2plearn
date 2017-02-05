/*
 * Copyright 2013 Thomas Bocek
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

package p2p.connection;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.message.TomP2PCumulationTCP;
import trunk.social.p2p.message.TomP2POutbound;
import trunk.social.p2p.message.TomP2PSinglePacketUDP;
import trunk.social.p2p.peers.PeerStatusListener;
import trunk.social.p2p.utils.Pair;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The "server" part that accepts connections.
 * 
 * @author Thomas Bocek
 * 
 */
public final class ChannelServer implements DiscoverNetworkListener{

	private static final Logger LOG = LoggerFactory.getLogger(ChannelServer.class);
	// private static final int BACKLOG = 128;

	private final EventLoopGroup bossGroup;
	private final EventLoopGroup workerGroup;
	
	private final Map<InetAddress, Channel> channelsTCP = Collections.synchronizedMap(new HashMap<InetAddress, Channel>());
	private final Map<InetAddress, Channel> channelsUDP = Collections.synchronizedMap(new HashMap<InetAddress, Channel>());

	private final FutureDone<Void> futureServerDone = new FutureDone<Void>();

	private final ChannelServerConfiguration channelServerConfiguration;
	private final Dispatcher dispatcher;
	private final List<PeerStatusListener> peerStatusListeners;
	
	private final DropConnectionInboundHandler tcpDropConnectionInboundHandler;
	private final DropConnectionInboundHandler udpDropConnectionInboundHandler;
	private final ChannelHandler udpDecoderHandler;
	private final DiscoverNetworks discoverNetworks;
	
	private boolean shutdown = false;
	private boolean broadcastAddressSupported = false;
	private boolean broadcastAddressTried = false;

    /**
     * Sets parameters and starts network device discovery.
     * 
     * @param bossGroup
     * 
     * @param workerGroup
     * 
     * @param channelServerConfiguration
	 *            The server configuration that contains e.g. the handlers
     * @param dispatcher
     *              The shared dispatcher
     * @param peerStatusListeners
	 *            The status listeners for offline peers
     * @throws IOException
     *               If device discovery failed.
     */
	public ChannelServer(final EventLoopGroup bossGroup, final EventLoopGroup workerGroup, final ChannelServerConfiguration channelServerConfiguration, final Dispatcher dispatcher,
	        final List<PeerStatusListener> peerStatusListeners, final ScheduledExecutorService timer) throws IOException {
		this.bossGroup = bossGroup;
		this.workerGroup = workerGroup;
		this.channelServerConfiguration = channelServerConfiguration;
		this.dispatcher = dispatcher;
		this.peerStatusListeners = peerStatusListeners;
		
		this.discoverNetworks = new DiscoverNetworks(5000, channelServerConfiguration.bindings(), timer);
		
		this.tcpDropConnectionInboundHandler = new DropConnectionInboundHandler(channelServerConfiguration.maxTCPIncomingConnections());
		this.udpDropConnectionInboundHandler = new DropConnectionInboundHandler(channelServerConfiguration.maxUDPIncomingConnections());
		this.udpDecoderHandler = new TomP2PSinglePacketUDP(channelServerConfiguration.signatureFactory());
		
		discoverNetworks.addDiscoverNetworkListener(this);
		if(timer!=null) {
			discoverNetworks.start().awaitUninterruptibly();
		}
	}
	
	public DiscoverNetworks discoverNetworks() {
		return discoverNetworks;
	}

	/**
	 * @return The channel server configuration.
	 */
	public ChannelServerConfiguration channelServerConfiguration() {
		return channelServerConfiguration;
	}
	
	@Override
    public void discoverNetwork(DiscoverResults discoverResults) {
		if (!channelServerConfiguration.isDisableBind()) {
			synchronized (ChannelServer.this) {
				if (shutdown) {
					return;
				}

				if(discoverResults.isListenAny()) {
					listenAny();
				} else {
					listenSpecificInetAddresses(discoverResults);
				}
			}
		}
	    
    }

	private void listenAny() {
		final InetSocketAddress tcpSocket = new InetSocketAddress(channelServerConfiguration.ports().tcpPort());
		final boolean tcpStart = startupTCP(tcpSocket, channelServerConfiguration);
    	if(!tcpStart) {
    		LOG.warn("cannot bind TCP on socket {}",tcpSocket);
    	} else {
    		LOG.info("Listening TCP on socket {}",tcpSocket);
    	}
    	
    	final InetSocketAddress udpSocket = new InetSocketAddress(channelServerConfiguration.ports().udpPort());
    	final boolean udpStart = startupUDP(udpSocket, channelServerConfiguration, true);
    	if(!udpStart) {
                final boolean udpStart2 = startupUDP(udpSocket, channelServerConfiguration, false);
                if(!udpStart2) {
                    LOG.warn("cannot bind UDP on socket at all {}", udpSocket);
                } else {
                    LOG.warn("can only bind to UDP without broadcast support {}", udpSocket);
                }
    	} else {
    		LOG.info("Listening UDP on socket {}",udpSocket);
    	}
    }

	//this method has blocking calls in it
	private void listenSpecificInetAddresses(DiscoverResults discoverResults) {
	    
            /**
             * Travis-ci has the same inet address as the broadcast adress, handle it properly.
             * 
             * eth0      Link encap:Ethernet  HWaddr 42:01:0a:f0:00:19  
             * inet addr:10.240.0.25  Bcast:10.240.0.25  Mask:255.255.255.255
             * UP BROADCAST RUNNING MULTICAST  MTU:1460  Metric:1
             * RX packets:849 errors:0 dropped:0 overruns:0 frame:0
             * TX packets:914 errors:0 dropped:0 overruns:0 carrier:0
             * collisions:0 txqueuelen:1000 
             * RX bytes:1080397 (1.0 MB)  TX bytes:123816 (123.8 KB)
             */
            final List<InetSocketAddress> broadcastAddresses = new ArrayList<InetSocketAddress>();
            
	    for (InetAddress inetAddress : discoverResults.newBroadcastAddresses()) {
	    	InetSocketAddress udpBroadcastSocket = new InetSocketAddress(inetAddress, channelServerConfiguration.ports()
	                .udpPort());
	    	broadcastAddressTried = true;
	    	boolean udpStartBroadcast = startupUDP(udpBroadcastSocket, channelServerConfiguration, false);
	    	
	    	if (udpStartBroadcast) {
	    		//if one broadcast address was found, then we don't need to bind to 0.0.0.0
	    		broadcastAddressSupported = true;
                        broadcastAddresses.add(udpBroadcastSocket);
	    		LOG.info("Listening on broadcast address: {} on port udp: {}"
		   		        , udpBroadcastSocket, channelServerConfiguration.ports().udpPort());
	    	} else {
	    		LOG.warn("cannot bind broadcast UDP {}", udpBroadcastSocket);
	    	}
	    }
	    
	    for (InetAddress inetAddress : discoverResults.removedFoundBroadcastAddresses()) {
	    	Channel channelUDP = channelsUDP.remove(inetAddress);
	    	if (channelUDP != null) {
	    		channelUDP.close().awaitUninterruptibly();
	    	}
	    }
	    
	    boolean udpStartBroadcast = false;
	    //if we tried but could not bind to a broadcast address. Happens on Windows, not on Mac/Linux
		if(!broadcastAddressSupported && broadcastAddressTried) {
			InetSocketAddress udpBroadcastSocket = new InetSocketAddress(channelServerConfiguration.ports().udpPort());
			LOG.info("Listening on wildcard broadcast address {}", udpBroadcastSocket);
			udpStartBroadcast = startupUDP(udpBroadcastSocket, channelServerConfiguration, true);
			if(!udpStartBroadcast) {
				LOG.warn("cannot bind wildcard broadcast UDP on socket {}", udpBroadcastSocket);
			}
		} 
		
		for (InetAddress inetAddress : discoverResults.newAddresses()) {
		   	InetSocketAddress tcpSocket = new InetSocketAddress(inetAddress, 
	    			channelServerConfiguration.ports().tcpPort());
	    	boolean tcpStart = startupTCP(tcpSocket, channelServerConfiguration);
	    	if(!tcpStart) {
	    		LOG.warn("cannot bind TCP on socket {}",tcpSocket);
	    	} else {
	    		LOG.info("Listening on address: {} on port tcp: {}"
		   		        , inetAddress, channelServerConfiguration.ports().tcpPort());
	    	}
	    	
	    	//as we are listening to anything on UDP, we don't need to listen to any other interfaces
	    	if(!udpStartBroadcast) {
                        InetSocketAddress udpSocket = new InetSocketAddress(inetAddress, 
	    				channelServerConfiguration.ports().udpPort());
                        //if we already bound to the inetaddress as bcast and inet are the same
                        if(broadcastAddresses.contains(udpSocket)) {
                                return;
                        }
	    		boolean udpStart = startupUDP(udpSocket, channelServerConfiguration, false); 
	    		if(!udpStart) {
	    			LOG.warn("cannot bind UDP on socket {}",udpSocket);
	    		} else {
		    		LOG.info("Listening on address: {} on port udp: {}"
			   		        , inetAddress, channelServerConfiguration.ports().udpPort());
		    	}
	    	}
	    }
		    
	    for (InetAddress inetAddress : discoverResults.removedFoundAddresses()) {
	    	Channel channelTCP = channelsTCP.remove(inetAddress);
	    	if (channelTCP != null) {
	    		channelTCP.close().awaitUninterruptibly();
	    	}
	    	Channel channelUDP = channelsUDP.remove(inetAddress);
	    	if (channelUDP != null) {
	    		channelUDP.close().awaitUninterruptibly();
	    	}
	    }
	}

	@Override
    public void exception(Throwable throwable) {
	    LOG.error("discovery problem", throwable);
    }

	/**
	 * Start to listen on a UPD port.
	 * 
	 * @param listenAddresses
	 *            The address to listen to
	 * @param config
	 *            Can create handlers to be attached to this port
	 * @param broadcastFlag 
	 * @return True if startup was successful
	 */
	boolean startupUDP(final InetSocketAddress listenAddresses, final ChannelServerConfiguration config, boolean broadcastFlag) {
		Bootstrap b = new Bootstrap();
		b.group(workerGroup);
		b.channel(NioDatagramChannel.class);
		//option broadcast only required as we not listen to the broadcast address directly
		if(broadcastFlag) {
			b.option(ChannelOption.SO_BROADCAST, true);
		}
		b.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(ConnectionBean.UDP_LIMIT));
		//default is on my machine 200K, testBroadcastUDP fails with this value, as UDP packets are dropped. Increase to 2MB
		b.option(ChannelOption.SO_RCVBUF, 2 * 1024 * 1024);
		b.option(ChannelOption.SO_SNDBUF, 2 * 1024 * 1024);
		
		b.handler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(final Channel ch) throws Exception {
				ch.config().setAllocator(channelServerConfiguration.byteBufAllocator());
				for (Map.Entry<String, Pair<EventExecutorGroup, ChannelHandler>> entry : handlers(false).entrySet()) {
					if (!entry.getValue().isEmpty()) {
						ch.pipeline().addLast(entry.getValue().element0(), entry.getKey(), entry.getValue().element1());
					} else if (entry.getValue().element1() != null) {
						ch.pipeline().addLast(entry.getKey(), entry.getValue().element1());
					}
				}
			}
		});

		ChannelFuture future = b.bind(listenAddresses);
		channelsUDP.put(listenAddresses.getAddress(), future.channel());
		return handleFuture(future);
	}

	/**
	 * Start to listen on a TCP port.
	 * 
	 * @param listenAddresses
	 *            The address to listen to
	 * @param config
	 *            Can create handlers to be attached to this port
	 * @return True if startup was successful
	 */
	boolean startupTCP(final InetSocketAddress listenAddresses, final ChannelServerConfiguration config) {
		ServerBootstrap b = new ServerBootstrap();
		b.group(bossGroup, workerGroup);
		b.channel(NioServerSocketChannel.class);
		//b.option(ChannelOption.SO_RCVBUF, 2 * 1024 * 1024);
		//b.option(ChannelOption.SO_SNDBUF, 2 * 1024 * 1024);
		b.childHandler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(final Channel ch) throws Exception {
				ch.config().setAllocator(channelServerConfiguration.byteBufAllocator());
				//bestEffortOptions(ch, ChannelOption.SO_BACKLOG, BACKLOG);
				bestEffortOptions(ch, ChannelOption.SO_LINGER, 0);
				bestEffortOptions(ch, ChannelOption.TCP_NODELAY, true);
				for (Map.Entry<String, Pair<EventExecutorGroup, ChannelHandler>> entry : handlers(true).entrySet()) {
					if (!entry.getValue().isEmpty()) {
						ch.pipeline().addLast(entry.getValue().element0(), entry.getKey(), entry.getValue().element1());
					} else if (entry.getValue().element1() != null) {
						ch.pipeline().addLast(entry.getKey(), entry.getValue().element1());
					}
				}
			}
		});
		ChannelFuture future = b.bind(listenAddresses);
		channelsTCP.put(listenAddresses.getAddress(), future.channel());
		return handleFuture(future);
	}

	private static <T> void bestEffortOptions(final Channel ch, ChannelOption<T> option, T value) {
		try {
			ch.config().setOption(option, value);
		} catch (ChannelException e) {
			// Ignore
		}
	}

	/**
	 * Creates the Netty handlers. After it sends it to the user, where the
	 * handlers can be modified. We add a couple or null handlers where the user
	 * can add its own handler.
	 * 
	 * @param tcp
	 *            Set to true if connection is TCP, false if UDP
	 * @return The channel handlers that may have been modified by the user
	 */
	private Map<String, Pair<EventExecutorGroup, ChannelHandler>> handlers(final boolean tcp) {
		TimeoutFactory timeoutFactory = new TimeoutFactory(null, channelServerConfiguration.idleTCPMillis(),
		        peerStatusListeners, "Server");
		final Map<String, Pair<EventExecutorGroup, ChannelHandler>> handlers;
		if (tcp) {
			final int nrTCPHandlers = 8; // 6 / 0.75 = 7;
			handlers = new LinkedHashMap<String, Pair<EventExecutorGroup, ChannelHandler>>(nrTCPHandlers);
			handlers.put("dropconnection", new Pair<EventExecutorGroup, ChannelHandler>(null, tcpDropConnectionInboundHandler));
			handlers.put("timeout0",
			        new Pair<EventExecutorGroup, ChannelHandler>(null, timeoutFactory.idleStateHandlerTomP2P()));
			handlers.put("timeout1", new Pair<EventExecutorGroup, ChannelHandler>(null, timeoutFactory.timeHandler()));
			handlers.put("decoder", new Pair<EventExecutorGroup, ChannelHandler>(null, new TomP2PCumulationTCP(
			        channelServerConfiguration.signatureFactory(), channelServerConfiguration.byteBufAllocator())));
		} else {
			// we don't need here a timeout since we receive a packet or
			// nothing. It is different than with TCP where we
			// may get a stream and in the middle of it, the other peer goes
			// offline. This cannot happen with UDP
			final int nrUDPHandlers = 6; // 4 = 0.75 = 4 
			handlers = new LinkedHashMap<String, Pair<EventExecutorGroup, ChannelHandler>>(nrUDPHandlers);
			handlers.put("dropconnection", new Pair<EventExecutorGroup, ChannelHandler>(null, udpDropConnectionInboundHandler));
			handlers.put("decoder", new Pair<EventExecutorGroup, ChannelHandler>(null, udpDecoderHandler));
		}
		handlers.put("encoder", new Pair<EventExecutorGroup, ChannelHandler>(null, new TomP2POutbound(
		        channelServerConfiguration.signatureFactory(), channelServerConfiguration.byteBufAllocator())));
		handlers.put("dispatcher", new Pair<EventExecutorGroup, ChannelHandler>(null, dispatcher));
		return channelServerConfiguration.pipelineFilter().filter(handlers, tcp, false);
	}

	/**
	 * Handles the waiting and returning the channel.
	 * 
	 * @param future
	 *            The future to wait for
	 * @return The channel or null if we failed to bind.
	 */
	private boolean handleFuture(final ChannelFuture future) {
		try {
			future.await();
		} catch (InterruptedException e) {
			if (LOG.isWarnEnabled()) {
				LOG.warn("could not start UPD server", e);
			}
			return false;
		}
		boolean success = future.isSuccess();
		if (success) {
			return true;
		} else {
			LOG.debug("binding not successful", future.cause());
			return false;
		}

	}

	/**
	 * Shuts down the server.
	 * 
	 * @return The future when the shutdown is complete. This includes the
	 *         worker and boss event loop
	 */
	public FutureDone<Void> shutdown() {
		synchronized (this) {
	        shutdown = true;
        }
		discoverNetworks.stop();
		final int maxListeners = channelsTCP.size() + channelsUDP.size();
		if(maxListeners == 0) {
			shutdownFuture().done();
		}
		// we have two things to shut down: UDP and TCP
		final AtomicInteger listenerCounter = new AtomicInteger(0);
		LOG.debug("shutdown servers");
		synchronized (channelsUDP) {
			for (Channel channelUDP : channelsUDP.values()) {
				channelUDP.close().addListener(new GenericFutureListener<ChannelFuture>() {
					@Override
					public void operationComplete(final ChannelFuture future) throws Exception {
						LOG.debug("shutdown TCP server");
						if (listenerCounter.incrementAndGet() == maxListeners) {
							futureServerDone.done();
						}
					}
				});
			}
		}
		synchronized (channelsTCP) {
			for (Channel channelTCP : channelsTCP.values()) {
				channelTCP.close().addListener(new GenericFutureListener<ChannelFuture>() {
					@Override
					public void operationComplete(final ChannelFuture future) throws Exception {
						LOG.debug("shutdown TCP channels");
						if (listenerCounter.incrementAndGet() == maxListeners) {
							futureServerDone.done();
						}
					}
				});
			}
		}
		return shutdownFuture();
	}

	/**
	 * @return The shutdown future that is used when calling {@link #shutdown()}
	 */
	public FutureDone<Void> shutdownFuture() {
		return futureServerDone;
	}
}
