package trunk.social.p2p.p2p;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.futures.BaseFuture;
import trunk.social.p2p.futures.BaseFutureAdapter;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.peers.Maintainable;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.peers.PeerStatistic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MaintenanceTask implements Runnable {
    
    private static final Logger LOG = LoggerFactory.getLogger(MaintenanceTask.class);
    private static final int MAX_PING = 5;
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private Peer peer;

    private int intervalMillis = 1000;

    private List<Maintainable> maintainables = new ArrayList<Maintainable>();

    private Map<BaseFuture, PeerAddress> runningFutures = new HashMap<BaseFuture, PeerAddress>();

    private boolean shutdown = false;

    private final Object lock = new Object();
    
    private ScheduledFuture<?> scheduledFuture;

    public void init(Peer peer, ScheduledExecutorService timer) {
        this.peer = peer;
        scheduledFuture = timer.scheduleAtFixedRate(this, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        synchronized (lock) {
            //make sure we only have 5 pings in parallel
            if (shutdown || COUNTER.get() > MAX_PING) {
                return;
            }
            for (Maintainable maintainable : maintainables) {
                PeerStatistic peerStatistic = maintainable.nextForMaintenance(runningFutures.values());
                if(peerStatistic == null) {
                    continue;
                }
                BaseFuture future = peer.ping().peerAddress(peerStatistic.peerAddress()).start();
                LOG.debug("Maintenance ping from {} to {}.", peer.peerAddress(), peerStatistic.peerAddress());
            
                peer.notifyAutomaticFutures(future);
                runningFutures.put(future, peerStatistic.peerAddress());
                COUNTER.incrementAndGet();
                future.addListener(new BaseFutureAdapter<BaseFuture>() {
                    @Override
                    public void operationComplete(BaseFuture future) throws Exception {
                    	synchronized (lock) {
                            runningFutures.remove(future);
                            COUNTER.decrementAndGet();
                        }
                    }
                });
            }
        }
    }

    public FutureDone<Void> shutdown() {
    	if(scheduledFuture!=null) {
    		scheduledFuture.cancel(false);
    		//TODO: check if synchronized is really necessary here
    	}
        final FutureDone<Void> futureShutdown = new FutureDone<Void>();
        synchronized (lock) {
            shutdown = true;
            final int max = runningFutures.size();
            if(max == 0) {
                futureShutdown.done();
                return futureShutdown;
            }
            final AtomicInteger counter = new AtomicInteger(0);
            for (BaseFuture future : runningFutures.keySet()) {
                future.addListener(new BaseFutureAdapter<BaseFuture>() {
                    @Override
                    public void operationComplete(BaseFuture future) throws Exception {
                        if (counter.incrementAndGet() == max) {
                            futureShutdown.done();
                        }
                    }
                });
            }
        }
        return futureShutdown;
    }

    public int intervalMillis() {
        return intervalMillis;
    }

    public MaintenanceTask intervalMillis(int intervalMillis) {
        this.intervalMillis = intervalMillis;
        return this;
    }
    
    public void addMaintainable(Maintainable maintainable) {
        maintainables.add(maintainable);
    }
}
