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
package trunk.social.p2p.futures;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The key future for recursive loops. A first version with the fork-join framework did not reduce the code complexity
 * significantly, thus I decided to write this class. The basic idea is that you can create parallel loops. For example
 * in a routing process (loop to find closest peers), one starts to ask 3 peers in parallel, the first that returns
 * result gets evaluated for new information about other peers, and a new peer is asked. If two peers finish, then two
 * other peers are asked. Thus, we keep always 3 connections running until we get the result.
 * 
 * @author Thomas Bocek
 * @param <K>
 */
public class FutureForkJoin<K extends BaseFuture> extends BaseFutureImpl<FutureForkJoin<K>> implements
        BaseFuture {
    // the references are stored in an atomic array, as they are access (although sequentially) but in different
    // threads.
    private final AtomicReferenceArray<K> forks;

    private final int nrFutures;

    private final int nrFinishFuturesSuccess;

    private final boolean cancelFuturesOnFinish;

    private final List<K> forksCopy = new ArrayList<K>();

    // all these values are accessed within synchronized blocks

    private int counter = 0;

    private int successCounter = 0;
    
    private final FutureDone<Void> futuresCompleted = new FutureDone<Void>();
    
    private final AtomicInteger futureCounter;

    /**
     * Facade if we expect everything to return successfully.
     * 
     * @param forks
     *            The futures that can also be modified outside this class. If a future is finished the future in that
     *            array will be set to null. A future may be initially null, which is considered a failure.
     */
    public FutureForkJoin(final AtomicReferenceArray<K> forks) {
        this(forks.length(), false, forks);
    }

    /**
     * Create a future fork join object.
     * 
     * @param nrFinishFuturesSuccess
     *            Is the number of futures that we expect to succeed.
     * @param cancelFuturesOnFinish
     *            Tells use if we should cancel the remaining futures. For get() it makes sense to cancel, for store()
     *            it does not.
     * @param forks
     *            The futures that can also be modified outside this class. If a future is finished the future in that
     *            array will be set to null. A future may be initially null, which is considered a failure.
     */
    public FutureForkJoin(final int nrFinishFuturesSuccess, final boolean cancelFuturesOnFinish,
            final AtomicReferenceArray<K> forks) {
        this.nrFinishFuturesSuccess = nrFinishFuturesSuccess;
        this.forks = forks;
        this.cancelFuturesOnFinish = cancelFuturesOnFinish;
        // the futures array may have null entries, so count first.
        nrFutures = forks.length();
        futureCounter = new AtomicInteger(nrFutures);
        if (this.nrFutures <= 0) {
            failed("We have no futures: " + nrFutures);
        } else {
            join();
        }
        self(this);
    }

    /**
     * Adds listeners and evaluates the result and when to notify the listeners.
     */
    private void join() {
        for (int i = 0; i < nrFutures; i++) {
            synchronized (lock) {
                if (completed) {
                    return;
                }
            }
            final int index = i;
            if (forks.get(index) != null) {
                forks.get(index).addListener(new BaseFutureAdapter<K>() {
                    @Override
                    public void operationComplete(final K future) throws Exception {
                        evaluate(future, index);
                        if (futureCounter.decrementAndGet() == 0) {
                        	futuresCompleted.done();
                        }
                    }
                });
            } else {
                boolean notifyNow = false;
                synchronized (lock) {
                    // if counter reaches nrFutures, that means we are finished
                    // and in this case, we failed otherwise, in evaluate,
                    // successCounter would finish first
                    if (++counter >= nrFutures) {
                        notifyNow = finish(FutureType.FAILED);
                    }
                }
                
                if (futureCounter.decrementAndGet() == 0) {
                	futuresCompleted.done();
                }
                
                if (notifyNow) {
                    notifyListeners();
                    cancelAll();
                    return;
                }
            }
        }
    }

    /**
     * Evaluates one future and determines if this future is finished.
     * 
     * @param finished
     *            The future to evaluate
     * @param index
     *            the index in the array.
     */
    private void evaluate(final K finished, final int index) {
        boolean notifyNow = false;
        synchronized (lock) {
            // this if statement is very important. If the future is finished, then any subsequent evaluation, which
            // will happen as we add the listener in the join, must not set the future to null!
            if (completed) {
                return;
            }
            // add the future that we have evaluated
            forksCopy.add(finished);
            forks.set(index, (K) null);
            if (finished.isSuccess() && ++successCounter >= nrFinishFuturesSuccess) {
                notifyNow = finish(FutureType.OK);
            } else if (++counter >= nrFutures) {
                notifyNow = finish(FutureType.FAILED);
            }
        }
        if (notifyNow) {
            notifyListeners();
            cancelAll();
        }
    }

    /**
     * Cancels all remaining futures if requested by the user.
     */
    private void cancelAll() {
        if (cancelFuturesOnFinish) {
            for (int i = 0; i < nrFutures; i++) {
                K future = forks.get(i);
                if (future != null) {
                    future.cancel();
                }
            }
        }
    }

    /**
     * Sets this future to complete. Always call this from a synchronized block.
     * 
     * @param type
     *            The type of the future, if it has failed, its ok, or it has been canceled
     * @return True if other listener should get notified
     */
    private boolean finish(final FutureType type) {
        if (!completedAndNotify()) {
            return false;
        }
        this.type = type;
        this.reason = reason();
        return true;
    }
    
    private String reason() {
    	final StringBuilder sb = new StringBuilder("forkjoin:");
        sb.append(type.name());
        for (final K k : completed()) {
            sb.append(",").append(k.failedReason());
        }
        return sb.toString();
    }

    /**
     * Returns the last evaluated future. This method may return null if an array with null values have been has been
     * used.
     * 
     * @return The last evaluated future.
     */
    public K last() {
        synchronized (lock) {
        	if(!forksCopy.isEmpty()) {
        		return forksCopy.get(forksCopy.size() - 1);
        	}
        }
        return null;
    }
    
    public K first() {
    	synchronized (lock) {
    		if(!forksCopy.isEmpty()) {
    			return forksCopy.get(0);
    		}
        }
    	return null;
    }

    /**
     * Returns a list of evaluated futures. The last completed future is the same as retrieved with {@link #last()}.
     * 
     * @return A list of evaluated futures.
     */
    public List<K> completed() {
        synchronized (lock) {
            return forksCopy;
        }
    }

    /**
     * Returns the number of successful finished futures.
     * 
     * @return The number of successful finished futures
     */
    public int successCounter() {
        synchronized (lock) {
            return successCounter;
        }
    }
    
    public FutureDone<Void> futuresCompleted() {
    	synchronized (lock) {
    		return futuresCompleted;
    	}
    }
}
