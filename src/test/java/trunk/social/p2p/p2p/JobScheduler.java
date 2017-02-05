package trunk.social.p2p.p2p;

import java.lang.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import trunk.social.p2p.futures.BaseFuture;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.p2p.builder.Builder;

public class JobScheduler implements Shutdown {

	final private ScheduledExecutorService scheduledExecutorService;
	final private Peer peer;
	final private AtomicInteger runnerCounter = new AtomicInteger(0);
	final private FutureDone<Void> shutdownFuture = new FutureDone<Void>();

	private boolean shutdown = false;

	final private class DirectReplicationWorker implements Runnable {

		private int counter = 0;
		private ScheduledFuture<?> future;
		final int repetitions;
		final AutomaticFuture automaticFuture;
		final Builder builder;
		final CountDownLatch latch;

		private DirectReplicationWorker(final Builder builder, final AutomaticFuture automaticFuture,
		        final int repetitions, final CountDownLatch latch) {
			runnerCounter.incrementAndGet();
			this.builder = builder;
			this.repetitions = repetitions;
			this.automaticFuture = automaticFuture;
			this.latch = latch;
		}

		@Override
		public void run() {
			boolean shutdownNow = false;
			synchronized (JobScheduler.this) {
				shutdownNow = shutdown;
            }
			try {
				if (shutdownNow || ( !(repetitions<0) && counter++ > repetitions)) {
					shutdown();
				} else {
					BaseFuture baseFuture = builder.start();
					peer.notifyAutomaticFutures(baseFuture);
					if (automaticFuture != null) {
						automaticFuture.futureCreated(baseFuture);
					}
				}
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}

		private void shutdown() {
			// wait until future is set
			try {
				latch.await();
			} catch (InterruptedException e) {
				// try anyway
			}
			if (future != null) {
				future.cancel(false);
			}
			if (runnerCounter.decrementAndGet() == 0) {
				shutdownFuture.done();
			}
		}
	}

	public JobScheduler(Peer peer) {
		this(peer, 1, Executors.defaultThreadFactory());
	}

	public JobScheduler(Peer peer, int corePoolSize) {
		this(peer, corePoolSize, Executors.defaultThreadFactory());
	}

	public JobScheduler(Peer peer, int corePoolSize, ThreadFactory threadFactory) {
		this.scheduledExecutorService = Executors.newScheduledThreadPool(corePoolSize, threadFactory);
		this.peer = peer;
		peer.addShutdownListener(this);
	}

	public Shutdown start(final Builder builder, final int intervalMillis, final int repetitions) {
		return start(builder, intervalMillis, repetitions, null);
	}

	public Shutdown start(final Builder builder, final int intervalMillis, final int repetitions,
						  final AutomaticFuture automaticFuture) {
		synchronized (this) {
			if (shutdown) {
				return new Shutdown() {
					@Override
					public BaseFuture shutdown() {
						return new FutureDone<Void>().done();
					}
				};
			}
			final CountDownLatch latch = new CountDownLatch(1);
			final DirectReplicationWorker worker = new DirectReplicationWorker(builder, automaticFuture, repetitions, latch);
			ScheduledFuture<?> future = scheduledExecutorService.scheduleWithFixedDelay(worker, 0, intervalMillis,
			        TimeUnit.MILLISECONDS);
			worker.future = future;
			latch.countDown();
			return new Shutdown() {
				@Override
				public BaseFuture shutdown() {
					worker.shutdown();
					return new FutureDone<Void>().done();
				}
			};
		}
	}

	@Override
	public FutureDone<Void> shutdown() {
		synchronized (this) {
			shutdown = true;
		}
		for (Runnable worker : scheduledExecutorService.shutdownNow()) {
			//running them will cause a shutdown if the flag is set
			worker.run();
		}
		peer.removeShutdownListener(this);
		return shutdownFuture;
	}
}
