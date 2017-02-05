package trunk.social.p2p.rpc;

public class RPC {
	//Max. 255 Commands - don't change the order!! keep .NET interoperability in mind
	public enum Commands{
		PING(), 
		PUT(), 
		GET(), 
		ADD(), 
		REMOVE(), 
		NEIGHBOR(), 
		QUIT(), 
		DIRECT_DATA(), 
		TRACKER_ADD(), 
		TRACKER_GET(), 
		PEX(), 
		DIGEST(), 
		BROADCAST(),
		PUT_META(), 
		DIGEST_BLOOMFILTER(),
		RELAY(),
		DIGEST_META_VALUES(),
		SYNC(),
		SYNC_INFO(),
		PUT_CONFIRM(),
		GET_LATEST(),
		RCON(),
		HOLEP(),
		GET_LATEST_WITH_DIGEST(),
		GCM(),
		REPLICA_PUT(), 
		DIGEST_ALL_BLOOMFILTER();
	public byte getNr() {
		return (byte) ordinal();
	}
	
	public static Commands find(int nr) {
		return values()[nr];
	}
	
	public static String toString(int nr) {
		return values()[nr].name();
	}
	
	public String toString() {
		return values()[ordinal()].name();
	}
		
}
}
