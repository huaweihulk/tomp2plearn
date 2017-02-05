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

package trunk.social.p2p.rpc;

import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.Number640;
import trunk.social.p2p.storage.Data;
import trunk.social.p2p.utils.Utils;

import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;

public class DigestResult {
    final private SimpleBloomFilter<Number160> contentKeyBloomFilter;
    final private SimpleBloomFilter<Number160> versionKeyBloomFilter;
    final private SimpleBloomFilter<Number160> contentBloomFilter;

    final private NavigableMap<Number640, Collection<Number160>> keyDigest;
    
    final private Map<Number640, Data> dataMap;

    public DigestResult(SimpleBloomFilter<Number160> contentKeyBloomFilter, SimpleBloomFilter<Number160> versionKeyBloomFilter, 
    		SimpleBloomFilter<Number160> contentBloomFilter) {
        this.contentKeyBloomFilter = contentKeyBloomFilter;
        this.versionKeyBloomFilter = versionKeyBloomFilter;
        this.contentBloomFilter = contentBloomFilter;
        this.keyDigest = null;
        this.dataMap = null;
    }

    public DigestResult(NavigableMap<Number640, Collection<Number160>> keyDigest) {
        this.keyDigest = keyDigest;
        this.contentKeyBloomFilter = null;
        this.versionKeyBloomFilter = null;
        this.contentBloomFilter = null;
        this.dataMap = null;
    }

    public DigestResult(Map<Number640, Data> dataMap) {
	    this.dataMap = dataMap;
	    this.keyDigest = null;
	    this.contentKeyBloomFilter = null;
        this.versionKeyBloomFilter = null;
        this.contentBloomFilter = null;
    }

	public SimpleBloomFilter<Number160> contentKeyBloomFilter() {
        return contentKeyBloomFilter;
    }

    public SimpleBloomFilter<Number160> versionKeyBloomFilter() {
        return versionKeyBloomFilter;
    }
    
    public SimpleBloomFilter<Number160> contentBloomFilter() {
        return contentBloomFilter;
    }

    public NavigableMap<Number640, Collection<Number160>> keyDigest() {
        return keyDigest;
    }

    public Map<Number640, Data> dataMap() {
        return dataMap;
    }
    

    @Override
    public int hashCode() {
        int hashCode = 0;
        if (keyDigest != null) {
            hashCode ^= keyDigest.hashCode();
        }
        if (contentKeyBloomFilter != null) {
            hashCode ^= contentKeyBloomFilter.hashCode();
        }
        if (versionKeyBloomFilter != null) {
            hashCode ^= versionKeyBloomFilter.hashCode();
        }
        if (contentBloomFilter != null) {
            hashCode ^= contentBloomFilter.hashCode();
        }
        if	(dataMap!=null) {
        	hashCode ^= dataMap.hashCode();
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DigestResult)) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        DigestResult o = (DigestResult) obj;
        
        return 	Utils.equals(keyDigest, o.keyDigest) &&
        		Utils.equals(contentKeyBloomFilter, o.contentKeyBloomFilter) &&
        		Utils.equals(versionKeyBloomFilter, o.versionKeyBloomFilter) &&
        		Utils.equals(contentBloomFilter, o.contentBloomFilter) &&
        		Utils.equals(dataMap, o.dataMap);
    }
}
