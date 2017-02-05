/*
 * Copyright 2013 Thomas Bocek, Maxat Pernebayev
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

package p2p.synchronization;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.dht.DHTBuilder;
import trunk.social.p2p.futures.BaseFutureAdapter;
import trunk.social.p2p.futures.FutureChannelCreator;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.futures.FutureResponse;
import trunk.social.p2p.message.DataMap;
import trunk.social.p2p.message.Message;
import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.Number640;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.storage.AlternativeCompositeByteBuf;
import trunk.social.p2p.storage.Data;
import trunk.social.p2p.storage.DataBuffer;
import trunk.social.p2p.utils.Utils;

import java.util.*;

/**
 * The builder for the synchronization. This class first sends an info message to get the checksums, then it checks what
 * needs to be done, (nothing, full copy, diff).
 *
 * @author Thomas Bocek
 * @author Maxat Pernebayev
 */
public class SyncBuilder extends DHTBuilder<SyncBuilder> {

    private static final Logger LOG = LoggerFactory.getLogger(SyncBuilder.class);
    private static final FutureDone<SyncStat> FUTURE_SHUTDOWN = new FutureDone<SyncStat>()
            .failed("sync builder - peer is shutting down");
    static final int DEFAULT_BLOCK_SIZE = 700;

    private final PeerAddress other;
    private final PeerSync peerSync;
    private final int blockSize;

    private DataMap dataMap;
    private Number640 key;
    private Set<Number640> keys;
    private NavigableMap<Number640, Collection<Number160>> dataMapHash;
    private ArrayList<Instruction> instructions;
    private boolean syncFromOldVersion = false;

    public SyncBuilder(final PeerSync peerSync, final PeerAddress other) {
        this(peerSync, other, DEFAULT_BLOCK_SIZE);
    }

    /**
     * Constructor.
     * <p>
     * The responsible peer that performs synchronization
     */
    public SyncBuilder(final PeerSync peerSync, final PeerAddress other, final int blockSize) {
        super(peerSync.peerDHT(), Number160.ZERO);
        self(this);
        this.other = other;
        this.peerSync = peerSync;
        this.blockSize = blockSize;
    }

    public SyncBuilder dataMap(DataMap dataMap) {
        this.dataMap = dataMap;
        return this;
    }

    public Number640 key() {
        return key;
    }

    public SyncBuilder key(Number640 key) {
        this.key = key;
        return this;
    }

    public Set<Number640> keys() {
        return keys;
    }

    public SyncBuilder keys(Set<Number640> keys) {
        this.keys = keys;
        return this;
    }

    public SyncBuilder syncFromOldVersion() {
        syncFromOldVersion = true;
        return this;
    }

    public boolean isSyncFromOldVersion() {
        return syncFromOldVersion;
    }

    public SyncBuilder syncFromOldVersion(boolean syncFromOldVersion) {
        this.syncFromOldVersion = syncFromOldVersion;
        return this;
    }

    public DataMap dataMap() {
        if (dataMap != null) {
            return dataMap;
        } else {
            NavigableMap<Number640, Data> newDataMap = new TreeMap<Number640, Data>();
            if (key != null) {
                Data data = peer.storageLayer().get(key);
                if (data == null) {
                    data = new Data().flag2();
                }
                newDataMap.put(key, data);
            }
            if (keys != null) {
                for (Number640 key : keys) {
                    Data data = peer.storageLayer().get(key);
                    if (data == null) {
                        data = new Data().flag2();
                    }
                    newDataMap.put(key, data);
                }
            }
            if (newDataMap.size() > 0) {
                return new DataMap(newDataMap);
            } else {
                throw new IllegalArgumentException("Need either dataMap, key, or keys!");
            }
        }
    }

    public NavigableMap<Number640, Collection<Number160>> dataMapHash() {
        if (dataMapHash == null) {
            dataMapHash = new TreeMap<Number640, Collection<Number160>>();
        }
        if (dataMap != null) {
            for (Map.Entry<Number640, Number160> entry : dataMap.convertToHash().entrySet()) {
                Set<Number160> hashSet = new HashSet<Number160>(1);
                hashSet.add(entry.getValue());
                dataMapHash.put(entry.getKey(), hashSet);
            }
        }
        if (key != null) {
            Set<Number160> hashSet = new HashSet<Number160>(1);
            hashSet.add(peer.storageLayer().get(key).hash());
            dataMapHash.put(key, hashSet);
        }
        if (keys != null) {
            for (Number640 key : keys) {
                Set<Number160> hashSet = new HashSet<Number160>(1);
                hashSet.add(peer.storageLayer().get(key).hash());
                dataMapHash.put(key, hashSet);
            }
        }
        return dataMapHash;
    }

    public ArrayList<Instruction> instructions() {
        return instructions;
    }

    public FutureDone<SyncStat> start() {
        if (peer.peer().isShutdown()) {
            return FUTURE_SHUTDOWN;
        }
        final FutureDone<SyncStat> futureSync = new FutureDone<SyncStat>();
        FutureChannelCreator futureChannelCreator = peer.peer().connectionBean().reservation().create(0, 2);
        Utils.addReleaseListener(futureChannelCreator, futureSync);
        futureChannelCreator.addListener(new BaseFutureAdapter<FutureChannelCreator>() {
            @Override
            public void operationComplete(final FutureChannelCreator future2) throws Exception {
                if (!future2.isSuccess()) {
                    futureSync.failed(future2);
                    LOG.error("checkDirect failed {}", future2.failedReason());
                    return;
                }
                final FutureResponse futureResponse = peerSync.syncRPC().infoMessage(other,
                        SyncBuilder.this, future2.channelCreator());
                futureResponse.addListener(new BaseFutureAdapter<FutureResponse>() {
                    @Override
                    public void operationComplete(FutureResponse future) throws Exception {
                        if (future.isFailed()) {
                            Utils.addReleaseListener(future2.channelCreator(), futureResponse);
                            futureSync.failed(future);
                            LOG.error("checkDirect failed {}", future.failedReason());
                            return;
                        }

                        Message responseMessage = future.responseMessage();
                        DataMap dataMap = responseMessage.dataMap(0);

                        if (dataMap == null) {
                            LOG.error("nothing received, something is wrong");
                            futureSync.failed("nothing received, something is wrong");
                            return;
                        }

                        NavigableMap<Number640, Data> retVal = new TreeMap<Number640, Data>();
                        boolean syncMessageRequired = false;
                        int dataCopy = 0;
                        int dataOrig = 0;
                        //int dataCopyCount = 0;
                        //int diffCount = 0;
                        //int dataNotCopied = 0;
                        for (Map.Entry<Number640, Data> entry : dataMap.dataMap().entrySet()) {

                            Data data = entry.getValue();
                            if (data.length() == 0) {
                                if (data.isFlag1()) {
                                    LOG.debug("no sync required");
                                    syncMessageRequired = false;
                                } else if (data.isFlag2()) {
                                    LOG.debug("copy required for key {}", entry.getKey());
                                    syncMessageRequired = true;
                                    Data data2 = peer.storageLayer().get(entry.getKey());
                                    dataOrig += data2.length();
                                    //copy
                                    retVal.put(entry.getKey(), data2);
                                    dataCopy += data2.length();

                                }
                            } else {
                                LOG.debug("sync required");
                                syncMessageRequired = true;
                                Data data2 = peer.storageLayer().get(entry.getKey());
                                dataOrig += data2.length();
                                final ByteBuf buffer = data.buffer();
                                Number160 versionKey = SyncUtils.decodeHeader(buffer);
                                Number160 hash = SyncUtils.decodeHeader(buffer);

                                List<Checksum> checksums = SyncUtils.decodeChecksums(buffer);
                                buffer.release();
                                // TODO: don't copy data, toBytes does a copy!
                                List<Instruction> instructions = RSync.instructions(
                                        data2.toBytes(), checksums, blockSize);

                                AlternativeCompositeByteBuf abuf = AlternativeCompositeByteBuf.compBuffer(AlternativeCompositeByteBuf.UNPOOLED_HEAP);

                                dataCopy += SyncUtils.encodeInstructions(instructions, versionKey, hash, abuf);
                                DataBuffer dataBuffer = new DataBuffer(abuf);
                                abuf.release();
                                //diff
                                Data data1 = new Data(dataBuffer).flag1();
                                retVal.put(entry.getKey(), data1);
                            }
                        }
                        final SyncStat syncStat = new SyncStat(peer.peerAddress().peerId(), other.peerId(), dataCopy, dataOrig);
                        if (syncMessageRequired) {
                            SyncBuilder.this.dataMap(new DataMap(retVal));
                            FutureResponse fr = peerSync.syncRPC().syncMessage(other,
                                    SyncBuilder.this, future2.channelCreator());
                            fr.addListener(new BaseFutureAdapter<FutureResponse>() {
                                @Override
                                public void operationComplete(FutureResponse future) throws Exception {
                                    if (future.isFailed()) {
                                        futureSync.failed(future);
                                    } else {
                                        futureSync.done(syncStat);
                                    }
                                }
                            });
                        } else {
                            futureSync.done(syncStat);
                        }
                    }
                });
            }
        });
        return futureSync;
    }
}
