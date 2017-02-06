package com.hulk.learn;

import trunk.social.p2p.dht.FutureGet;
import trunk.social.p2p.dht.FuturePut;
import trunk.social.p2p.dht.PeerDHT;
import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.storage.Data;

import java.io.IOException;
import java.util.Random;

/**
 * Created by hulk on 17-2-6.
 */
public class PutGetTest {
    private static final Random RND = new Random(42L);
    private static final int PEER_NR_1 = 30;
    private static final int PEER_NR_2 = 77;

    public static void examplePutGetConfig(PeerDHT[] peers, Number160 nr2) throws IOException, ClassNotFoundException {
        Number160 nr = new Number160(RND);
        FuturePut futurePut = peers[30].put(nr).data(new Number160(11), new Data("hello"))
                .domainKey(Number160.createHash("my_domain")).start();
        futurePut.awaitUninterruptibly();
        System.out.println("peer 30 stored [key: " + nr + ", value: \"hallo\"]");
        // this will fail, since we did not specify the domain
//        FutureGet futureGet = peers[77].get(nr).all().start();
//        System.out.println("peer 77 got: \"" + futureGet.data() + "\" for the key " + nr);
        // this will succeed, since we specify the domain
        FutureGet futureGet =
                peers[77].get(nr).all().domainKey(Number160.createHash("my_domain")).start().awaitUninterruptibly();
        System.out.println("peer 77 got: \"" + futureGet.data().object() + "\" for the key " + nr);
    }
}
