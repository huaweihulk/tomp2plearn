package p2p.dht;

import trunk.social.p2p.connection.ChannelCreator;
import trunk.social.p2p.futures.BaseFutureAdapter;
import trunk.social.p2p.futures.FutureDone;

public class UtilsDHT {
	
    
    public static int dataSize(RemoveBuilder builder) {
	    if (builder.contentKeys()!=null) {
	    	return builder.contentKeys().size();
	    }
	    //we don't know how much, at least one.
	    return 1;
    }
    
    /**
     * Adds a listener to the response future and releases all aquired channels in channel creator.
     * 
     * @param channelCreator
     *            The channel creator that will be shutdown and all connections will be closed
     * @param baseFutures
     *            The futures to listen to. If all the futures finished, then the channel creator is shutdown. If null
     *            provided, the channel creator is shutdown immediately.
     */
	public static void addReleaseListener(final ChannelCreator channelCreator,
			final FutureDHT<?> futureDHT) {
		if (futureDHT == null) {
			channelCreator.shutdown();
			return;
		}

		futureDHT.addListener(new BaseFutureAdapter<FutureDHT<?>>() {
			@Override
			public void operationComplete(final FutureDHT<?> future)
					throws Exception {
				FutureDone<Void> futuresCompleted = futureDHT
						.futuresCompleted();
				if (futuresCompleted != null) {
					futureDHT.futuresCompleted().addListener(
							new BaseFutureAdapter<FutureDone<Void>>() {
								@Override
								public void operationComplete(
										final FutureDone<Void> future)
										throws Exception {
									channelCreator.shutdown();
								}
							});
				} else {
					channelCreator.shutdown();
				}
			}
		});
	}
	
}
