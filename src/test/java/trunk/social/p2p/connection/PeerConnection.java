package trunk.social.p2p.connection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import trunk.social.p2p.futures.BaseFutureAdapter;
import trunk.social.p2p.futures.FutureChannelCreator;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.futures.FutureResponse;
import trunk.social.p2p.peers.PeerAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerConnection {
	final private static Logger LOG = LoggerFactory.getLogger(PeerConnection.class);
	final public static int HEART_BEAT_MILLIS = 2000;
    
	final private Semaphore oneConnection;
    final private PeerAddress remotePeer;
    final private ChannelCreator cc;
    final private boolean initiator;

    final private Map<FutureChannelCreator, FutureResponse> map;
    final private FutureDone<Void> closeFuture;
    final private int heartBeatMillis;
    final private int idleTCP;

    // these may be called from different threads, but they will never be called concurrently within this library
    private volatile ChannelFuture channelFuture;
    
    private PeerConnection(Semaphore oneConnection, PeerAddress remotePeer, ChannelCreator cc, 
    		boolean initiator, Map<FutureChannelCreator, FutureResponse> map, FutureDone<Void> closeFuture, 
    		int heartBeatMillis, int idleTCP, ChannelFuture channelFuture) {
    	this.oneConnection = oneConnection;
    	this.remotePeer = remotePeer;
    	this.cc = cc;
    	this.initiator = initiator;
    	this.map = map;
    	this.closeFuture = closeFuture;
    	this.heartBeatMillis = heartBeatMillis;
    	this.idleTCP = idleTCP;
    	this.channelFuture = channelFuture;
    }
    

    /**
     * If we don't have an open TCP connection, we first need a channel creator to open a channel.
     * 
     * @param remotePeer
     *            The remote peer to connect to
     * @param cc
     *            The channel creator where we can open a TCP connection
     * @param heartBeatMillis
     *            The heart beat in milliseconds
     */
    public PeerConnection(PeerAddress remotePeer, ChannelCreator cc, int heartBeatMillis, int idleTCP) {
        this.remotePeer = remotePeer;
        this.cc = cc;
        this.heartBeatMillis = heartBeatMillis;
        this.idleTCP = idleTCP;
        this.initiator = true;
        this.oneConnection = new Semaphore(1);
        this.map = new LinkedHashMap<FutureChannelCreator, FutureResponse>();
        this.closeFuture = new FutureDone<Void>();
    }

    /**
     * If we already have an open TCP connection, we don't need a channel creator
     * 
     * @param remotePeer
     *            The remote peer to connect to
     * @param channelFuture
     *            The channel future of an already open TCP connection
     * @param heartBeatMillis
     *            The heart beat in milliseconds
     */
    public PeerConnection(PeerAddress remotePeer, ChannelFuture channelFuture, int heartBeatMillis, int idleTCP) {
        this.remotePeer = remotePeer;
        this.channelFuture = channelFuture;
        addCloseListener(channelFuture);
        this.cc = null;
        this.heartBeatMillis = heartBeatMillis;
        this.idleTCP = idleTCP;
        this.initiator = false;
        this.oneConnection = new Semaphore(1);
        this.map = new LinkedHashMap<FutureChannelCreator, FutureResponse>();
        this.closeFuture = new FutureDone<Void>();
    }

    public PeerConnection channelFuture(ChannelFuture channelFuture) {
        this.channelFuture = channelFuture;
        addCloseListener(channelFuture);
        return this;
    }
    
    public int heartBeatMillis() {
	    return heartBeatMillis;
    }
    
    public int idleTCP() {
	    return idleTCP;
    }

    public ChannelFuture channelFuture() {
        return channelFuture;
    }

    public FutureDone<Void> closeFuture() {
        return closeFuture;
    }

    private void addCloseListener(final ChannelFuture channelFuture) {
        channelFuture.channel().closeFuture().addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> arg0) throws Exception {
            	LOG.debug("About to close the connection {}, {}.",  channelFuture.channel(), initiator ? "initiator" : "from-dispatcher");
                closeFuture.done();
            }
        });
    }

    public FutureDone<Void> close() {
        // cc is not null if we opened the connection
    	Channel channel = channelFuture != null ? channelFuture.channel() : null;
        if (cc != null) {
        	LOG.debug("Close connection {}. We were the initiator.", channel);
            FutureDone<Void> future = cc.shutdown();
            // Maybe done on arrival? Set close future in any case
            future.addListener(new BaseFutureAdapter<FutureDone<Void>>() {
                @Override
                public void operationComplete(FutureDone<Void> future) throws Exception {
                    closeFuture.done();
                }
            });        
        } else {
        	// cc is null if it is an incoming connection
            // we can close it here or it will be closed when the dispatcher is shut down
        	LOG.debug("Close connection {}. We are not the initiator.", channel);
            channelFuture.channel().close();
        }
        return closeFuture;
    }

    public FutureChannelCreator acquire(final FutureResponse futureResponse) {
        FutureChannelCreator futureChannelCreator = new FutureChannelCreator();
        return acquire(futureChannelCreator, futureResponse);
    }

    private FutureChannelCreator acquire(final FutureChannelCreator futureChannelCreator,
            final FutureResponse futureResponse) {
    	LOG.debug("About to acquire a peer connection for {}.", remotePeer);
        if (oneConnection.tryAcquire()) {
        	LOG.debug("Acquired a peer connection for {}.", remotePeer);
            futureResponse.addListener(new BaseFutureAdapter<FutureResponse>() {
                @Override
                public void operationComplete(FutureResponse future) throws Exception {
                    oneConnection.release();
                    LOG.debug("released peer connection for {}", remotePeer);
                    synchronized (map) {
                        Iterator<Map.Entry<FutureChannelCreator, FutureResponse>> iterator = map.entrySet()
                                .iterator();
                        if (iterator.hasNext()) {
                            Map.Entry<FutureChannelCreator, FutureResponse> entry = iterator.next();
                            iterator.remove();
                            acquire(entry.getKey(), entry.getValue());
                        }
                    }
                }
            });
            futureChannelCreator.reserved(cc);
            return futureChannelCreator;
        } else {
            synchronized (map) {
                map.put(futureChannelCreator, futureResponse);
            }
        }
        return futureChannelCreator;
    }

    public ChannelCreator channelCreator() {
        return cc;
    }

    public PeerAddress remotePeer() {
        return remotePeer;
    }
    
    public boolean isOpen() {
    	if (channelFuture!=null) {
    		return channelFuture.channel().isOpen();
    	} else {
    		return false;
    	}
    }
    
    public PeerConnection changeRemotePeer(PeerAddress remotePeer) {
    	return new PeerConnection(oneConnection, remotePeer, cc, initiator, map, closeFuture, heartBeatMillis, idleTCP, channelFuture);
    }
    
	@Override
	public int hashCode() {
		if(channelFuture!=null) {
            return channelFuture.hashCode();
		}
        return remotePeer.hashCode();
	}
    
    @Override
    public boolean equals(Object obj) {
    	if (!(obj instanceof PeerConnection)) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		PeerConnection p = (PeerConnection) obj;
		if (channelFuture!=null) {
			return channelFuture.channel().equals(p.channelFuture.channel());
		}
        return remotePeer.equals(p.remotePeer);
    }
    
    @Override
    public String toString() {
    	StringBuilder sb = new StringBuilder("pconn: ");
    	sb.append(remotePeer);
    	return sb.toString();
    }
}
