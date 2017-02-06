package com.hulk.learn;

import trunk.social.p2p.dht.PeerDHT;
import trunk.social.p2p.peers.Number160;

import java.io.IOException;
import java.util.Random;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException {
//        PeerDHT master = null;
//        final int nrPeers = 100;
//        final int port = 4001;
//        final Random RND = new Random(42L);
//        PeerDHT[] peers = ExampleUtils.createAndAttachPeersDHT(nrPeers, port);
//        ExampleUtils.bootstrap(peers);
//        master = peers[0];
//        Number160 nr = new Number160(RND);
//        PutGetTest.examplePutGetConfig(peers, nr);
        //NatTest.startServer();
        NatTest.startClientNAT("localhost");
    }
}
