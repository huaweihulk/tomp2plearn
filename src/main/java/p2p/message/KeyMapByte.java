package p2p.message;

import trunk.social.p2p.peers.Number640;
import trunk.social.p2p.utils.Utils;

import java.util.Map;

public class KeyMapByte {

    private final Map<Number640, Byte> keysMap;
    public KeyMapByte(Map<Number640, Byte> keysMap) {
        this.keysMap = keysMap;
    }
    
    public Map<Number640, Byte> keysMap() {
        return keysMap;
    }

    public int size() {
        return keysMap.size();
    }

    public void put(Number640 key, Byte value) {
        keysMap.put(key, value);
    }
    
    @Override
    public int hashCode() {
        return keysMap.hashCode();
    }
    
    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof KeyMapByte)) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        final KeyMapByte k = (KeyMapByte) obj;
        final boolean test1 = Utils.isSameSets(k.keysMap.keySet(), keysMap.keySet());
        final boolean test2 = Utils.isSameSets(k.keysMap.values(), keysMap.values());
        return test1 && test2;
    }
}
