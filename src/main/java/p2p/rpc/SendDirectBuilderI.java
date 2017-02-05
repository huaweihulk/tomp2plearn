package p2p.rpc;

import trunk.social.p2p.connection.ConnectionConfiguration;
import trunk.social.p2p.storage.DataBuffer;

import java.security.KeyPair;

public interface SendDirectBuilderI extends ConnectionConfiguration {

    boolean isRaw();

    boolean isSign();

    boolean isStreaming();

    DataBuffer dataBuffer();

    Object object();

    KeyPair keyPair();

}
