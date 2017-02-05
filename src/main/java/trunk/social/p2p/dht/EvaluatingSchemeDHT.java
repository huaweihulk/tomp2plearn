/*
 * Copyright 2009 Thomas Bocek
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
import trunk.social.p2p.peers.Number640;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.rpc.DigestResult;
import trunk.social.p2p.storage.Data;
import trunk.social.p2p.storage.DataBuffer;

import java.util.Collection;
import java.util.Map;

public interface EvaluatingSchemeDHT {
    public Collection<Number640> evaluate1(Map<PeerAddress, Map<Number640, Number160>> rawKeys480);

    public Map<Number640, Data> evaluate2(Map<PeerAddress, Map<Number640, Data>> rawData);

    public Object evaluate3(Map<PeerAddress, Object> rawObjects);

    public DataBuffer evaluate4(Map<PeerAddress, DataBuffer> rawChannels);

    public DigestResult evaluate5(Map<PeerAddress, DigestResult> rawDigest);
    
    public Collection<Number640> evaluate6(Map<PeerAddress, Map<Number640, Byte>> rawKeys480);
    
}
