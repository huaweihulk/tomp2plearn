package trunk.social.p2p.rpc;

import java.security.KeyPair;

import trunk.social.p2p.connection.ConnectionConfiguration;
import trunk.social.p2p.storage.DataBuffer;

public interface SendDirectBuilderI extends ConnectionConfiguration {

    boolean isRaw();

    boolean isSign();

    boolean isStreaming();

    DataBuffer dataBuffer();

    Object object();

    KeyPair keyPair();

}
