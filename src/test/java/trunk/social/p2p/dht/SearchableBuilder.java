package trunk.social.p2p.dht;

import java.util.Collection;

import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.Number640;

public interface SearchableBuilder {

	Number640 from();

	Number640 to();

	Collection<Number160> contentKeys();

}
