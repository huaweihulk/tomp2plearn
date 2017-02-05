package trunk.social.p2p.dht;

import java.util.ArrayList;
import java.util.Collection;
import java.util.NavigableMap;
import java.util.TreeMap;

final public class RangeLock<K extends Comparable<K>> {
	private final Object lockInternal = new Object();
	private final NavigableMap<K, Long> cache = new TreeMap<K, Long>();
	
	final public class Range {
        final private K fromKey;
        final private K toKey;
        final private RangeLock<K> ref;
        private Range(final K fromKey, final K toKey, RangeLock<K> ref) {
        	this.fromKey = fromKey;
        	this.toKey = toKey;
        	this.ref = ref;
        }
        
        public void unlock() {
        	ref.unlock(this);
        }
    }
	
	public Range tryLock(final K fromKey, final K toKey) {
		final long id = Thread.currentThread().getId();
		synchronized (lockInternal) {
			//first check overlappings or smaller subset of keys
			final NavigableMap<K, Long> subMap = cache.subMap(fromKey, true, toKey, true);
			if (!subMap.isEmpty()) {
				if(sizeFiltered(id, subMap) != 0) {
					return null;
				}
			}
			
			//second check larger subset of keys
			final Collection<Long> before = cache.headMap(fromKey, false).values();
			final Collection<Long> after = cache.tailMap(toKey, false).values();
			//now check for intersection thread ids
			final Collection<Long> intersection = intersection(before, after);
			if(!intersection.isEmpty()) {
				if(sizeFiltered(id, intersection) != 0) {
					return null;
				}
			}
			
        	cache.put(fromKey, id);
        	cache.put(toKey, id);
        }
		return new Range(fromKey, toKey, this);
	}
	
	/**
	 * The same thread can lock a range twice. The first unlock for range x unlocks all range x.   
	 * @param fromKey
	 * @param toKey
	 * @return
	 */
	public Range lock(final K fromKey, final K toKey) {
		final long id = Thread.currentThread().getId();
		synchronized (lockInternal) {
			final NavigableMap<K, Long> subMap = cache.subMap(fromKey, true, toKey, true);
			final Collection<Long> before = cache.headMap(fromKey, false).values();
			final Collection<Long> after = cache.tailMap(toKey, false).values();
			Collection<Long> intersection = null;
			
			while (!(intersection = intersection(before, after)).isEmpty() || !subMap.isEmpty()) {
				if((subMap.isEmpty() || sizeFiltered(id, subMap) == 0) && 
						(intersection.isEmpty() || sizeFiltered(id, intersection) == 0)) {
					break;
				}
				
				try {
					lockInternal.wait();
				} catch (InterruptedException e) {
					return null;
				}
			}
        	
        	cache.put(fromKey, id);
        	cache.put(toKey, id);
        }
		return new Range(fromKey, toKey, this);
	}

	//make a copy!
	private Collection<Long> intersection(final Collection<Long> before,
			final Collection<Long> after) {
		final Collection<Long> intersection = new ArrayList<Long>(before);
		intersection.retainAll(after);
		return intersection;
	}

	public void unlock(Range lock) {
		synchronized (lockInternal) {
			cache.remove(lock.fromKey);
			cache.remove(lock.toKey);
			lockInternal.notifyAll();
	    }
	}
	
	public int size() {
		synchronized (lockInternal) {
			return cache.size();
		}
	}
	
	private static int sizeFiltered(final long id, final NavigableMap<?, Long> subMap) {
		int counter = 0;
		for(final long longValue:subMap.values()) {
			if(longValue != id) {
				counter ++;
			}
		}
		return counter;
	}
	
	private static int sizeFiltered(final long id, final Collection<Long> intersection) {
		int counter = 0;
		for(final long longValue:intersection) {
			if(longValue != id) {
				counter ++;
			}
		}
		return counter;
	}
}
