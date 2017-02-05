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

package trunk.social.p2p.futures;

import java.util.Map;

import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.storage.Data;

/**
 * A future to keep track of a future task. The task is submitted using an RPC,
 * then a scheduled task keeps polling the peer if the peer is still alive. If
 * the peer dies, this future is notified. If the remote peer finishes the task,
 * the remote peer sends an RPC to the initiating peer that the task is
 * finished.
 * 
 * @author Thomas Bocek
 */
public class FutureAsyncTask extends BaseFutureImpl<FutureAsyncTask> {
    private final PeerAddress remotePeer;

    private Map<Number160, Data> dataMap;

    /**
     * Constructor
     * 
     * @param remotePeer
     *            The address of the peer that processes the task
     */
    public FutureAsyncTask(PeerAddress remotePeer) {
        this.remotePeer = remotePeer;
        self(this);
    }

    /**
     * Finishes the future and notifies listeners.
     * 
     * @param dataMap
     *            The result from the remote peer
     */
    public FutureAsyncTask dataMap(Map<Number160, Data> dataMap) {
        synchronized (lock) {
            if (!completedAndNotify()) {
                return this;
            }
            this.dataMap = dataMap;
            this.type = FutureType.OK;
        }
        notifyListeners();
        return this;
    }

    /**
     * @return The result of the remote peer from the task.
     */
    public Map<Number160, Data> dataMap() {
        synchronized (lock) {
            return dataMap;
        }
    }

    /**
     * @return The address of the peer that processes the task
     */
    public PeerAddress remotePeer() {
        return remotePeer;
    }
}