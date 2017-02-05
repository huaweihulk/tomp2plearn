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
package p2p.dht;

import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.Number640;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.rpc.DigestResult;
import trunk.social.p2p.storage.Data;
import trunk.social.p2p.storage.DataBuffer;

import java.util.*;

public class CumulativeScheme implements EvaluatingSchemeDHT {
    @Override
    public Collection<Number640> evaluate1(Map<PeerAddress, Map<Number640, Number160>> rawKeys480) {
        Set<Number640> result = new HashSet<Number640>();
        if (rawKeys480 != null) {
            for (Map<Number640, Number160> tmp : rawKeys480.values()) {
                result.addAll(tmp.keySet());
            }
        }
        return result;
    }
    
    @Override
    public Collection<Number640> evaluate6(Map<PeerAddress, Map<Number640, Byte>> rawKeys480) {
    	Map<Number640, Byte> result = new HashMap<Number640, Byte>();
        for (Map<Number640, Byte> tmp : rawKeys480.values())
            result.putAll(tmp);
        return result.keySet();
    }

    @Override
    public Map<Number640, Data> evaluate2(Map<PeerAddress, Map<Number640, Data>> rawKeys) {
        Map<Number640, Data> result = new HashMap<Number640, Data>();
        for (Map<Number640, Data> tmp : rawKeys.values())
            result.putAll(tmp);
        return result;
    }

    @Override
    public Object evaluate3(Map<PeerAddress, Object> rawKeys) {
        throw new UnsupportedOperationException("cannot cumulate");
    }

    @Override
    public DataBuffer evaluate4(Map<PeerAddress, DataBuffer> rawKeys) {
        throw new UnsupportedOperationException("cannot cumulate");
    }

    @Override
    public DigestResult evaluate5(Map<PeerAddress, DigestResult> rawDigest) {
        throw new UnsupportedOperationException("cannot cumulate");
    }
}
