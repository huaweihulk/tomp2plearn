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

package trunk.social.p2p.connection;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.futures.FutureResponse;
import trunk.social.p2p.message.Message;
import trunk.social.p2p.utils.Pair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.rpc.RPC;

/**
 * Creates the channels. This class is created by {@link Reservation}
 * and should never be called directly. With this class one can create TCP or
 * UDP channels up to a certain extent. Thus it must be know beforehand how much
 * connections will be created.
 * 
 * @author Thomas Bocek
 */
public class ChannelCreator {
	private static final Logger LOG = LoggerFactory.getLogger(ChannelCreator.class);

	private final EventLoopGroup workerGroup;
	private final ChannelGroup recipients = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

	private final int maxPermitsUDP;
	private final int maxPermitsTCP;

	private final Semaphore semaphoreUPD;
	private final Semaphore semaphoreTCP;

	// we should be fair, otherwise we see connection timeouts due to unfairness
	// if busy
	private final ReadWriteLock readWriteLockUDP = new ReentrantReadWriteLock(true);
	private final Lock readUDP = readWriteLockUDP.readLock();
	private final Lock writeUDP = readWriteLockUDP.writeLock();

	private final ReadWriteLock readWriteLockTCP = new ReentrantReadWriteLock(true);
	private final Lock readTCP = readWriteLockTCP.readLock();
	private final Lock writeTCP = readWriteLockTCP.writeLock();

	private final FutureDone<Void> futureChannelCreationDone;

	private final ChannelClientConfiguration channelClientConfiguration;
	
	private final InetAddress sendFromAddress;

	private EventExecutorGroup handlerExecutor;

	private boolean shutdownUDP = false;
	private boolean shutdownTCP = false;

	/**
	 * Package private constructor, since this is created by
	 * {@link Reservation} and should never be called directly.
	 * 
	 * @param workerGroup
	 *            The worker group for netty that is shared between TCP and UDP.
	 *            This workergroup is not shutdown if this class is shutdown
	 * @param futureChannelCreationDone
	 *            We need to set this from the outside as we want to attach
	 *            listeners to it
	 * @param maxPermitsUDP
	 *            The number of max. parallel UDP connections.
	 * @param maxPermitsTCP
	 *            The number of max. parallel TCP connections.
	 * @param channelClientConfiguration
	 *            The configuration that contains the pipeline filter
	 */
	ChannelCreator(final EventLoopGroup workerGroup, final FutureDone<Void> futureChannelCreationDone,
			int maxPermitsUDP, int maxPermitsTCP,
			final ChannelClientConfiguration channelClientConfiguration, InetAddress sendFromAddress) {
		this.workerGroup = workerGroup;
		this.futureChannelCreationDone = futureChannelCreationDone;
		this.maxPermitsUDP = maxPermitsUDP;
		this.maxPermitsTCP = maxPermitsTCP;
		this.semaphoreUPD = new Semaphore(maxPermitsUDP);
		this.semaphoreTCP = new Semaphore(maxPermitsTCP);
		this.channelClientConfiguration = channelClientConfiguration;
		this.sendFromAddress = sendFromAddress;
	}

	/**
	 * Creates a "channel" to the given address. This won't send any message
	 * unlike TCP.
	 * 
	 * @param broadcast
	 *            Sets this channel to be able to broadcast
	 * @param channelHandlers
	 *            The handlers to filter and set
	 * @return The channel future object or null if we are shut down
	 */
	public ChannelFuture createUDP(final boolean broadcast, final Map<String, Pair<EventExecutorGroup, ChannelHandler>> channelHandlers,
			FutureResponse futureResponse, boolean fireandforget) {
		readUDP.lock();
		try {
			if (shutdownUDP) {
				return null;
			}
			if (!semaphoreUPD.tryAcquire()) {
				final String errorMsg = "Tried to acquire more resources (UDP) than announced.";
				LOG.error(errorMsg);
				throw new RuntimeException(errorMsg);
			}
			final Bootstrap b = new Bootstrap();
			b.group(workerGroup);
			b.channel(NioDatagramChannel.class);
			b.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(ConnectionBean.UDP_LIMIT));
			
			//we don't need to increase the buffers as we limit the connections in tomp2p
			b.option(ChannelOption.SO_RCVBUF, 2 * 1024 * 1024);
			b.option(ChannelOption.SO_SNDBUF, 2 * 1024 * 1024);
			if (broadcast) {
				b.option(ChannelOption.SO_BROADCAST, true);
			}
			Map<String, Pair<EventExecutorGroup, ChannelHandler>> channelHandlers2 = channelClientConfiguration.pipelineFilter().filter(
					channelHandlers, false, true);
			addHandlers(b, channelHandlers2);
			// Here we need to bind, as opposed to the TCP, were we connect if
			// we do a connect, we cannot receive
			// broadcast messages
			final ChannelFuture channelFuture;
			
			LOG.debug("Create UDP, use from address: {}", sendFromAddress);
			if(fireandforget) {
				channelFuture = b.connect(futureResponse.request().recipient().ipv4Socket().createUDPSocket());
			} else {
				channelFuture = b.bind(new InetSocketAddress(sendFromAddress, 0));
			}
			recipients.add(channelFuture.channel());
			setupCloseListener(channelFuture, semaphoreUPD, futureResponse);
			return channelFuture;
		} finally {
			readUDP.unlock();
		}
	}

	/**
	 * Creates a channel to the given address. This will setup the TCP
	 * connection
	 * 
	 * @param socketAddress
	 *            The address to send future messages
	 * @param connectionTimeoutMillis
	 *            The timeout for establishing a TCP connection
	 * @param channelHandlers
	 *            The handlers to filter and set
	 * @param futureResponse
	 *            the futureResponse
	 * @return The channel future object or null if we are shut down.
	 */
	public ChannelFuture createTCP(final SocketAddress socketAddress, final int connectionTimeoutMillis,
			final Map<String, Pair<EventExecutorGroup, ChannelHandler>> channelHandlers, final FutureResponse futureResponse) {
		readTCP.lock();
		try {
			if (shutdownTCP) {
				return null;
			}
			if (!semaphoreTCP.tryAcquire()) {
				final String errorMsg = "Tried to acquire more resources (TCP) than announced.";
				LOG.error(errorMsg);
				throw new RuntimeException(errorMsg);
			}
			Bootstrap b = new Bootstrap();
			b.group(workerGroup);
			b.channel(NioSocketChannel.class);
			b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectionTimeoutMillis);
			b.option(ChannelOption.TCP_NODELAY, true);
			b.option(ChannelOption.SO_LINGER, 0);
			b.option(ChannelOption.SO_REUSEADDR, true);
			//b.option(ChannelOption.SO_RCVBUF, 2 * 1024 * 1024);
			//b.option(ChannelOption.SO_SNDBUF, 2 * 1024 * 1024);
			Map<String, Pair<EventExecutorGroup, ChannelHandler>> channelHandlers2 = channelClientConfiguration.pipelineFilter().filter(
					channelHandlers, true, true);
			addHandlers(b, channelHandlers2);
			
			LOG.debug("Create TCP, use from address: {}", sendFromAddress);

			ChannelFuture channelFuture = b.connect(socketAddress, new InetSocketAddress(sendFromAddress, 0));

			recipients.add(channelFuture.channel());
			setupCloseListener(channelFuture, semaphoreTCP, futureResponse);
			return channelFuture;
		} finally {
			readTCP.unlock();
		}
	}

	/**
	 * Since we want to add multiple handlers, we need to do this with the
	 * pipeline.
	 * 
	 * @param bootstrap
	 *            The bootstrap
	 * @param channelHandlers
	 *            The handlers to be added.
	 */
	private void addHandlers(final Bootstrap bootstrap, final Map<String, Pair<EventExecutorGroup, ChannelHandler>> channelHandlers) {
		bootstrap.handler(new ChannelInitializer<Channel>() {
			@Override
			protected void initChannel(final Channel ch) throws Exception {
				ch.config().setAllocator(channelClientConfiguration.byteBufAllocator());
				for (Map.Entry<String, Pair<EventExecutorGroup, ChannelHandler>> entry : channelHandlers.entrySet()) {
					if (entry.getKey().equals("handler")) {
						handlerExecutor = entry.getValue().element0();
					}
					if (entry.getValue().element0() != null) {
						ch.pipeline().addLast(entry.getValue().element0(), entry.getKey(), entry.getValue().element1());
					} else {
						ch.pipeline().addLast(entry.getKey(), entry.getValue().element1());
					}
				}
			}
		});
	}

	/**
	 * When a channel is closed, the semaphore is released and another channel can
	 * be created. Also, the lock for the channel creating is being released.
	 * This means that the ChannelCreator can be shut down.
	 * 
	 * @param channelFuture
	 *            The channel future
	 * @param semaphore
	 *            The semaphore to release
	 * @return The same future that was passed as an argument
	 */
	private ChannelFuture setupCloseListener(final ChannelFuture channelFuture, final Semaphore semaphore,
			final FutureResponse futureResponse) {
		channelFuture.channel().closeFuture().addListener(new GenericFutureListener<ChannelFuture>() {
			@Override
			public void operationComplete(final ChannelFuture future) throws Exception {
				// it is important that the release of the semaphore and the set
				// of the future happen sequentially. If this is run in this
				// thread it will be a netty thread, and this is not what the
				// user may have wanted. The future responses should be executed
				// in the thread of the handler.
				Runnable runner = new Runnable() {
					@Override
					public void run() {
						semaphore.release();

						Message request = futureResponse.request();
						if (request != null && futureResponse.responseMessage() == null && request.recipient().slow()
								&& request.command() != RPC.Commands.PING.getNr() && request.command() != RPC.Commands.NEIGHBOR.getNr()) {
							// If the request goes to a slow peer, the channel
							// can be closed until the response arrives
							LOG.debug("Ignoring channel close event because recipient is slow peer");
						} else {
							futureResponse.responseNow();
						}
					}
				};
				if (handlerExecutor == null) {
					runner.run();
				} else {
					handlerExecutor.submit(runner);
				}
			}
		});
		return channelFuture;
	}

	/**
	 * Setup the close listener for a channel that was already created
	 * 
	 * @param channelFuture
	 *            The channel future
	 * @param futureResponse
	 *            The future response
	 * @return The same future that was passed as an argument
	 */
	public ChannelFuture setupCloseListener(final ChannelFuture channelFuture, final FutureResponse futureResponse) {
		channelFuture.channel().closeFuture().addListener(new GenericFutureListener<ChannelFuture>() {
			@Override
			public void operationComplete(final ChannelFuture future) throws Exception {
				futureResponse.responseNow();
			}
		});
		return channelFuture;
	}

	public boolean isShutdown() {
		return shutdownTCP || shutdownUDP;
	}

	/**
	 * Shuts down this channel creator. This means that no more TCP or UDP connections
	 * can be established.
	 * 
	 * @return The shutdown future.
	 */
	public FutureDone<Void> shutdown() {
		// set shutdown flag for UDP and TCP
        // if we acquire a write lock, all read locks are blocked as well
		writeUDP.lock();
		writeTCP.lock();
		try {
			if (shutdownTCP || shutdownUDP) {
				shutdownFuture().failed("already shutting down");
				return shutdownFuture();
			}
			shutdownUDP = true;
			shutdownTCP = true;
		} finally {
			writeTCP.unlock();
			writeUDP.unlock();
		}

		recipients.close().addListener(new GenericFutureListener<ChannelGroupFuture>() {
			@Override
			public void operationComplete(final ChannelGroupFuture future) throws Exception {
				// we can block here as we block in GlobalEventExecutor.INSTANCE
				semaphoreUPD.acquireUninterruptibly(maxPermitsUDP);
				semaphoreTCP.acquireUninterruptibly(maxPermitsTCP);
				shutdownFuture().done();
			}
		});

		return shutdownFuture();
	}

	/**
	 * @return The shutdown future that is used when calling {@link #shutdown()}
	 */
	public FutureDone<Void> shutdownFuture() {
		return futureChannelCreationDone;
	}

	public int availableUDPPermits() {
		return semaphoreUPD.availablePermits();
	}

	public int availableTCPPermits() {
		return semaphoreTCP.availablePermits();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("sem-udp:");
		sb.append(semaphoreUPD.availablePermits());
		sb.append(",sem-tcp:");
		sb.append(semaphoreTCP.availablePermits());
		sb.append(",addrUDP:");
		sb.append(semaphoreUPD);
		return sb.toString();
	}
}