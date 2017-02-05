package p2p.connection;

public interface DiscoverNetworkListener {

	void discoverNetwork(DiscoverResults discoverResults);

	void exception(Throwable throwable);

}
