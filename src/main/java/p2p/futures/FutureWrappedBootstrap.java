/*
 * Copyright 2009 Thomas Bocek
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
package p2p.futures;

import trunk.social.p2p.peers.PeerAddress;

import java.util.Collection;

/**
 * The bootstrap will be a wrapped future, because we need to ping a server first.
 * If this ping is successful, we can bootstrap.
 * 
 * @author Thomas Bocek
 * @param <K>
 */
public class FutureWrappedBootstrap<K extends BaseFuture> extends FutureWrapper<K> implements FutureBootstrap {

	/**
	 * Set failed that returns this class, not null.
	 * 
	 * @param failed
	 *            The failure string
	 * @return this class (never null)
	 */
	public FutureWrappedBootstrap<K> failed0(String failed) {
		failed(failed);
		return this;
	}

	private Collection<PeerAddress> bootstrapTo;

	/**
	 * The addresses we boostrap to. If we broadcast, we don't know the
	 * addresses in advance.
	 * 
	 * @param bootstrapTo
	 *            A collection of peers that were involved in the bootstrapping
	 */
	public void bootstrapTo(final Collection<PeerAddress> bootstrapTo) {
		synchronized (lock) {
			this.bootstrapTo = bootstrapTo;
		}
	}

	/**
	 * Returns a collection of of peers that were involved in the bootstrapping.
	 */
	public Collection<PeerAddress> bootstrapTo() {
		synchronized (lock) {
			return bootstrapTo;
		}
	}
}
