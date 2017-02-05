package p2p.dht;

import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.Number640;

import java.util.Collection;

public interface SearchableBuilder {

	Number640 from();

	Number640 to();

	Collection<Number160> contentKeys();

}
