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

package p2p.peers;

import java.util.Collection;

/**
 * Filter peers if the IP is the same. Being too strict does not mean to harm
 * the network. Other peers will have the information about the peer even if you
 * excluded it.
 * 
 * @author Thomas Bocek
 * 
 */
public class PeerIPFilter implements PeerMapFilter {

	final int mask4;
	final int mask6;

	public PeerIPFilter(int mask4, int mask6) {
		this.mask4 = mask4;
		this.mask6 = mask6;
	}

	@Override
	public boolean rejectPeerMap(final PeerAddress peerAddress, final PeerMap peerMap) {
		return rejectPreRouting(peerAddress, peerMap.all());
	}

	@Override
	public boolean rejectPreRouting(final PeerAddress peerAddress, final Collection<PeerAddress> all) {

		if(peerAddress.ipv4Socket() != null) {
			final IP.IPv4 ipv4 = peerAddress.ipv4Socket().ipv4();
			for (final PeerAddress inMap : all) {
				final PeerSocketAddress.PeerSocket4Address ps4a = inMap.ipv4Socket();
				if(ps4a == null) {
					continue;
				}
				if (ipv4.maskWithNetworkMask(mask4).equals(ps4a.ipv4().maskWithNetworkMask(mask4))) {
					return true;
				}
			}
		}

		if(peerAddress.ipv6Socket() != null) {
			final IP.IPv6 ipv6 = peerAddress.ipv6Socket().ipv6();
			for (final PeerAddress inMap : all) {
				final PeerSocketAddress.PeerSocket6Address ps6a = inMap.ipv6Socket();
				if(ps6a == null) {
					continue;
				}
				if (ipv6.maskWithNetworkMask(mask6).equals(ps6a.ipv6().maskWithNetworkMask(mask6))) {
					return true;
				}
			}
		}

		return false;
	}

}
