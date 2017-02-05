/*
 * Copyright 2012 Thomas Bocek
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

package trunk.social.p2p.dht;

import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.Number320;
import trunk.social.p2p.peers.Number480;
import trunk.social.p2p.peers.Number640;
import trunk.social.p2p.storage.Data;

import java.security.PublicKey;
import java.util.Collection;
import java.util.NavigableMap;

/**
 * The storage is typically backed by multiple Java collections (HashMap, TreeMap, etc.). This map returns the map that
 * stores the values which are present in the DHT. If you plan to do transactions (put/get), make sure you do the
 * locking in order to not interfere with other threads that use this map. Although the storage is threadsafe, there may
 * be concurrency issues with respect to transactions (e.g., do a get before a put). Please use
 * {@link StorageLayer#getLockStorage()} for full locking, and {@link StorageLayer#getLockNumber160()},
 * {@link StorageLayer#getLockNumber320()}, {@link StorageLayer#getLockNumber480()},
 * {@link StorageLayer#getLockNumber640()} for fine grained locking.
 * 
 * 
 * @author Thomas Bocek
 * 
 */
public interface Storage {
    // Core storage
    public abstract Data put(Number640 key, Data value);

    public abstract Data get(Number640 key);

    public abstract boolean contains(Number640 key);

    public abstract int contains(Number640 from, Number640 to);

    public abstract Data remove(Number640 key, boolean returnData);

    public abstract NavigableMap<Number640, Data> remove(Number640 from, Number640 to);

    public abstract NavigableMap<Number640, Data> subMap(Number640 from, Number640 to);

    public abstract NavigableMap<Number640, Data> map();

    public abstract void close();

    // Maintenance
    public abstract void addTimeout(Number640 key, long expiration);

    public abstract void removeTimeout(Number640 key);

    public abstract Collection<Number640> subMapTimeout(long to);
    
    public int storageCheckIntervalMillis();

    // Domain / entry protection
    public abstract boolean protectDomain(Number320 key, PublicKey publicKey);

    public abstract boolean isDomainProtectedByOthers(Number320 key, PublicKey publicKey);
    
    public abstract boolean protectEntry(Number480 key, PublicKey publicKey);

    public abstract boolean isEntryProtectedByOthers(Number480 key, PublicKey publicKey);
    
    // Responsibility
    public abstract Number160 findPeerIDsForResponsibleContent(Number160 locationKey);

    public abstract Collection<Number160> findContentForResponsiblePeerID(Number160 peerID);

    public boolean updateResponsibilities(Number160 locationKey, Number160 peerId);

    public void removeResponsibility(Number160 locationKey);
}