package com.hulk.learn;

import trunk.social.p2p.connection.PeerConnection;
import trunk.social.p2p.futures.BaseFuture;
import trunk.social.p2p.futures.BaseFutureListener;
import trunk.social.p2p.futures.FutureDiscover;
import trunk.social.p2p.nat.FutureNAT;
import trunk.social.p2p.nat.PeerBuilderNAT;
import trunk.social.p2p.nat.PeerNAT;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.p2p.PeerBuilder;
import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.relay.RelayCallback;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;

/**
 * Created by hulk on 17-2-6.
 */
public class NatTest {
    public static void startServer() throws IOException, InterruptedException {
        Random r = new Random(42L);
        Peer peer = new PeerBuilder(new Number160(r)).ports(4000).start();
        System.out.println("peer started");
        for (; ; ) {
            for (PeerAddress pa : peer.peerBean().peerMap().all()) {
                System.out.println("peer online(Tcp):" + pa);
            }
            Thread.sleep(2000L);
        }
    }

    public static void startClientNAT(String ip) throws IOException, InterruptedException {
        Random r = new Random(43L);
        Peer peer = new PeerBuilder(new Number160(r)).ports(4001).behindFirewall().start();
        final PeerNAT peerNAT = new PeerBuilderNAT(peer).relayCallback(new RelayCallback() {
            @Override
            public void onRelayAdded(PeerAddress relay, PeerConnection object) {
                System.out.println("relay added: " + relay);
            }

            @Override
            public void onRelayRemoved(PeerAddress relay, PeerConnection object) {
                System.out.println("relay removed: " + relay);
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
            }

            @Override
            public void onFullRelays(int activeRelays) {
                System.out.println("could find all relays: " + activeRelays);
            }

            @Override
            public void onNoMoreRelays(int activeRelays) {
                System.out.println("could not find more relays: " + activeRelays);
            }

            @Override
            public void onShutdown() {

            }
        }).start();

        final PeerAddress pa = PeerAddress.create(Number160.ZERO, InetAddress.getByName(ip), 4000, 4000, 40001);
        final FutureDiscover fd = peer.discover().peerAddress(pa).start();
        final FutureNAT fn = peerNAT.portForwarding(fd);

        fn.addListener(new BaseFutureListener<BaseFuture>() {
            @Override
            public void operationComplete(BaseFuture future) throws Exception {
                if (future.isFailed()) {
                    peerNAT.startRelay(fd.reporter());
                }
            }

            @Override
            public void exceptionCaught(Throwable t) throws Exception {
                t.printStackTrace();
            }
        });

        if (fd.isSuccess()) {
            System.out.println("found that my outside address is " + fd.peerAddress());
        } else {
            System.out.println("failed " + fd.failedReason());
        }

        if (fn.isSuccess()) {
            System.out.println("NAT success: " + fn.peerAddress());
        } else {
            System.out.println("failed " + fn.failedReason());
            //this is enough time to print out the status of the relay search
            Thread.sleep(5000);
        }

        peer.shutdown();
    }
}
