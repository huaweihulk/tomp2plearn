/*
 * Copyright 2011 Thomas Bocek
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


import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import trunk.social.p2p.futures.FutureDone;

/**
 * A class to search for addresses to bind the sockets to. The user first
 * creates a {@link Bindings} instance, provides all the necessary information
 * and then calls {@link #discoverInterfaces(Bindings)}. The results are stored
 * in the {@link Bindings} instance as well.
 * 
 * @author Thomas Bocek
 */
public final class DiscoverNetworks {

	private final Collection<DiscoverNetworkListener> listeners = new ArrayList<DiscoverNetworkListener>(1);
	
	private final int checkIntervalMillis;
	private final Bindings bindings;
	private final ScheduledExecutorService timer;
	
	private ScheduledFuture<?> future;
	private boolean stopped = false;
	private volatile DiscoverResults discoverResults = null;
	
	public DiscoverNetworks(final int checkIntervalMillis, Bindings bindings, ScheduledExecutorService timer) {
		this.checkIntervalMillis = checkIntervalMillis;
		this.bindings = bindings;
		this.timer = timer;
	}

	public FutureDone<Void> start() {
		final FutureDone<Void> futureDone = new FutureDone<Void>();
		// the thread could be executed faster than the initialization of the future variable, which would result in an NPE
		final CountDownLatch futureExistsLatch = new CountDownLatch(1);
		future = timer.scheduleWithFixedDelay(new Runnable() {
			
			private boolean firstRun = true;
			@Override
			public void run() {
				try {
					discoverResults = discoverInterfaces(bindings, discoverResults);
					if(discoverResults.isListenAny()) {
						futureExistsLatch.await();
						future.cancel(false);
						notifyDiscoverNetwork(discoverResults);
					} else if(!discoverResults.isEmpty()) {
						notifyDiscoverNetwork(discoverResults);
					}
					if(firstRun) {
						futureDone.done();
						firstRun = false;
					}
				} catch (Throwable t) {
					futureDone.done();
					notifyFailure(t);
				}
			}
		}, 0, checkIntervalMillis, TimeUnit.MILLISECONDS);
		futureExistsLatch.countDown();
		return futureDone;
	}
	
	public DiscoverResults currentDiscoverResults() {
		return discoverResults;
	}
	
	public void stop () {
		synchronized (this) {
			stopped = true;
			if(future!=null) {
				future.cancel(false);
			}
        }
	}

	public DiscoverNetworks addDiscoverNetworkListener(DiscoverNetworkListener listener) {
		listeners.add(listener);
		return this;
	}

	public DiscoverNetworks removeDiscoverNetworkListener(DiscoverNetworkListener listener) {
		listeners.remove(listener);
		return this;
	}

	private void notifyDiscoverNetwork(DiscoverResults discoverResults) {
		synchronized (this) {
			if(stopped) {
				return;
			}
			for (DiscoverNetworkListener listener : listeners) {
				listener.discoverNetwork(discoverResults);
			}
		}
	}

	private void notifyFailure(Throwable throwable) {
		synchronized (this) {
			if(stopped) {
				return;
			}
			for (DiscoverNetworkListener listener : listeners) {
				listener.exception(throwable);
			}
		}
	}
	
	public static DiscoverResults discoverInterfaces(final Bindings bindings) throws IOException {
		return discoverInterfaces(bindings, null);
	}

    /**
     * Search for local interfaces. Hints how to search for those interfaces are
     * provided by the user through the {@link Bindings} class. The results of
     * that search (InetAddress) are stored in {@link Bindings} as well.
     * 
     * @param bindings
     *              The hints for the search and also the results are stored there
     * @param oldDiscoverResults
     * 
     * @return The status of the search
     * @throws IOException
     *              If anything goes wrong, such as reflection.
     */
	public static DiscoverResults discoverInterfaces(final Bindings bindings, DiscoverResults oldDiscoverResults) throws IOException {
		
		final Collection<InetAddress> existingAddresses = new ArrayList<InetAddress>();
		final Collection<InetAddress> existingBroadcastAddresses= new ArrayList<InetAddress>();
		
		final Collection<InetAddress> existingAddressesOld = new ArrayList<InetAddress>();
		final Collection<InetAddress> existingBroadcastAddressesOld= new ArrayList<InetAddress>();
		
		if(oldDiscoverResults!=null) {
			existingAddresses.addAll(oldDiscoverResults.existingAddresses());
			existingBroadcastAddresses.addAll(oldDiscoverResults.existingBroadcastAddresses());
			existingAddressesOld.addAll(oldDiscoverResults.existingAddresses());
			existingBroadcastAddressesOld.addAll(oldDiscoverResults.existingBroadcastAddresses());
		}
		
		StringBuilder sb = new StringBuilder("Discover status: ");
		Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
		while (e.hasMoreElements()) {
			NetworkInterface networkInterface = e.nextElement();
			if (bindings.anyInterfaces()) {
				sb.append(" ++").append(networkInterface.getName());
				DiscoverResults discoverResults = discoverNetwork(networkInterface, existingAddressesOld, 
						existingBroadcastAddressesOld, bindings.isIPv4(), bindings.isIPv6(), bindings.addresses());
				sb.append(discoverResults.status()).append(",");
				addIfNotPresent(existingAddresses, discoverResults.existingAddresses());
				addIfNotPresent(existingBroadcastAddresses, discoverResults.existingBroadcastAddresses());
			} else {
				if (bindings.containsInterface(networkInterface.getName())) {
					sb.append(" +").append(networkInterface.getName());
					DiscoverResults discoverResults = discoverNetwork(networkInterface, existingAddressesOld, 
							existingBroadcastAddressesOld, bindings.isIPv4(), bindings.isIPv6(), bindings.addresses()); 
					sb.append(discoverResults.status()).append(",");
					addIfNotPresent(existingAddresses, discoverResults.existingAddresses());
					addIfNotPresent(existingBroadcastAddresses, discoverResults.existingBroadcastAddresses());
				} else {
					sb.append(" -").append(networkInterface.getName()).append(",");
				}
			}
		}
		// remove the last comma or space
		sb.deleteCharAt(sb.length() - 1);
		sb.append(".");
		
		//find new results left-intersect
		final Collection<InetAddress> newAddresses = new ArrayList<InetAddress>();
		for(InetAddress existingAddress:existingAddresses) {
			if(!existingAddressesOld.contains(existingAddress)) {
				newAddresses.add(existingAddress);
			}
		}
		final Collection<InetAddress> newBroadcastAddresses= new ArrayList<InetAddress>();
		for(InetAddress existingBroadcastAddress:existingBroadcastAddresses) {
			if(!existingBroadcastAddressesOld.contains(existingBroadcastAddress)) {
				newBroadcastAddresses.add(existingBroadcastAddress);
			}
		}
		
		//find removed results right-intersect
		final Collection<InetAddress> removedFoundAddresses= new ArrayList<InetAddress>();
		for(InetAddress existingAddressOld:existingAddressesOld) {
			if(!existingAddresses.contains(existingAddressOld)) {
				removedFoundAddresses.add(existingAddressOld);
			}
		}
		final Collection<InetAddress> removedFoundBroadcastAddresses= new ArrayList<InetAddress>();
		for(InetAddress existingBroadcastAddressOld:existingBroadcastAddressesOld) {
			if(!existingBroadcastAddresses.contains(existingBroadcastAddressOld)) {
				removedFoundBroadcastAddresses.add(existingBroadcastAddressOld);
			}
		}
		
		DiscoverResults discoverResults = new DiscoverResults(newAddresses, newBroadcastAddresses, 
				removedFoundAddresses, removedFoundBroadcastAddresses, existingAddresses, existingBroadcastAddresses, bindings.isListenAny(), sb.toString());
		return discoverResults;
	}

	private static void addIfNotPresent(Collection<InetAddress> existingAddresses,
            Collection<InetAddress> existingAddresses2) {
		for(InetAddress inet:existingAddresses2) {
			if(!existingAddresses.contains(inet)) {
				existingAddresses.add(inet);
			}
		}
    }

    /**
     * Discovers network interfaces and addresses.
     * 
     * @param networkInterface
     *                  The networkInterface to search for addresses to listen to
     * @param foundAddresses
     * 
     * @param foundBroadcastAddresses
     * 
     * @param isIPv4
     * 
     * @param isIPv6
     * 
     * @return  The status of the discovery
     */
	public static DiscoverResults discoverNetwork(final NetworkInterface networkInterface,
	        final Collection<InetAddress> foundAddresses, Collection<InetAddress> foundBroadcastAddresses,
	        boolean isIPv4, boolean isIPv6, List<InetAddress> requestedInetAddress) {
		final Collection<InetAddress> foundAddresses2 = new ArrayList<InetAddress>(foundAddresses);
		final Collection<InetAddress> foundBroadcastAddresses2 = new ArrayList<InetAddress>(
		        foundBroadcastAddresses);

		//final Collection<InetAddress> newAddresses = new ArrayList<InetAddress>();
		//final Collection<InetAddress> newBroadcastAddresses = new ArrayList<InetAddress>();

		StringBuilder sb = new StringBuilder("( ");
		for (InterfaceAddress iface : networkInterface.getInterfaceAddresses()) {
			// reported by Vasiliy:
			// iface == null happens when connecting to the Internet through
			// my mobile operator. In this case additional dial-up connection
			// is created in Network Connections (on Windows)
			if (iface == null) {
				continue;
			}
			InetAddress inet = iface.getAddress();
			InetAddress broadcast = iface.getBroadcast();
			// could be a bug, but on travis-ci I get an any address as a broadcast address, maybe reletade to this:
			// http://bugs.java.com/bugdatabase/view_bug.do?bug_id=7158636
			if (broadcast != null && !broadcast.isAnyLocalAddress()) {
				if(!foundBroadcastAddresses2.contains(broadcast)) {
					foundBroadcastAddresses2.add(broadcast);
				}
			}

			if (inet instanceof Inet4Address && isIPv4) {
				sb.append(inet).append(",");
				if(!foundAddresses2.contains(inet)) {
					if(requestedInetAddress.isEmpty() || requestedInetAddress.contains(inet)) {
						foundAddresses2.add(inet);
					}
				}
			} else if (inet instanceof Inet6Address && isIPv6) {
				sb.append(inet).append(",");
				if(!foundAddresses2.contains(inet)) {
					if(requestedInetAddress.isEmpty() || requestedInetAddress.contains(inet)) {
						foundAddresses2.add(inet);
					}
				}
			}
		}
		sb.deleteCharAt(sb.length() - 1);
		sb.append(")");
		DiscoverResults discoverResults = new DiscoverResults(null, null,
				null, null, foundAddresses2, foundBroadcastAddresses2, false, sb.toString());

		return discoverResults;
	}
}
