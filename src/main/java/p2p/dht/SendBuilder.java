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

package p2p.dht;

import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.rpc.SendDirectBuilderI;
import trunk.social.p2p.storage.DataBuffer;

public class SendBuilder extends DHTBuilder<SendBuilder> implements SendDirectBuilderI {

    private final static FutureSend FUTURE_SHUTDOWN = new FutureSend(null)
            .failed("send builder - peer is shutting down");

    private DataBuffer dataBuffer;

    private Object object;

    //
    private boolean cancelOnFinish = false;

    private boolean streaming = false;

    public SendBuilder(PeerDHT peer, Number160 locationKey) {
        super(peer, locationKey);
        self(this);
    }

    public DataBuffer dataBuffer() {
        return dataBuffer;
    }

    public SendBuilder dataBuffer(DataBuffer dataBuffer) {
        this.dataBuffer = dataBuffer;
        return this;
    }

    public Object object() {
        return object;
    }

    public SendBuilder object(Object object) {
        this.object = object;
        return this;
    }

    public boolean isCancelOnFinish() {
        return cancelOnFinish;
    }

    public SendBuilder cancelOnFinish(boolean cancelOnFinish) {
        this.cancelOnFinish = cancelOnFinish;
        return this;
    }

    public SendBuilder cancelOnFinish() {
        this.cancelOnFinish = true;
        return this;
    }

    public boolean isRaw() {
        return object == null;
    }

    public SendBuilder streaming(boolean streaming) {
        this.streaming = streaming;
        return this;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public SendBuilder streaming() {
        this.streaming = true;
        return this;
    }

    public FutureSend start() {
        if (peer.peer().isShutdown()) {
            return FUTURE_SHUTDOWN;
        }
        preBuild("send-builder");
        
        final FutureSend futureSend = new FutureSend(this, requestP2PConfiguration().minimumResults(), new VotingSchemeDHT());
        return peer.distributedHashTable().direct(this, futureSend);
    }
}
