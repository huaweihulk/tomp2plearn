package trunk.social.p2p.nat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.connection.PeerConnection;
import trunk.social.p2p.connection.Ports;
import trunk.social.p2p.futures.*;
import trunk.social.p2p.message.Message;
import trunk.social.p2p.message.Message.Type;
import trunk.social.p2p.natpmp.NatPmpException;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.p2p.builder.BootstrapBuilder;
import trunk.social.p2p.p2p.builder.DiscoverBuilder;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.peers.PeerSocketAddress.PeerSocket4Address;
import trunk.social.p2p.relay.DistributedRelay;
import trunk.social.p2p.relay.PeerMapUpdateTask;
import trunk.social.p2p.relay.RelayRPC;
import trunk.social.p2p.relay.RelayUtils;
import trunk.social.p2p.rpc.RPC;

import java.net.Inet4Address;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PeerNAT {

    private static final Logger LOG = LoggerFactory.getLogger(PeerNAT.class);

    private final Peer peer;
    private final NATUtils natUtils;
    private final RelayRPC relayRPC;
    private final boolean manualPorts;
    private final DistributedRelay distributedRelay;
    private boolean relayMaintenance;
    private BootstrapBuilder bootstrapBuilder;
    private int peerMapUpdateIntervalSeconds;

    PeerNAT(Peer peer, NATUtils natUtils, RelayRPC relayRPC, boolean manualPorts, DistributedRelay distributedRelay,
            boolean relayMaintenance, BootstrapBuilder bootstrapBuilder, int peerMapUpdateIntervalSeconds) {
        this.peer = peer;
        this.natUtils = natUtils;
        this.relayRPC = relayRPC;
        this.manualPorts = manualPorts;
        this.distributedRelay = distributedRelay;
        this.relayMaintenance = relayMaintenance;
        this.bootstrapBuilder = bootstrapBuilder;
        this.peerMapUpdateIntervalSeconds = peerMapUpdateIntervalSeconds;
    }

    public Peer peer() {
        return peer;
    }

    public NATUtils natUtils() {
        return natUtils;
    }

    public RelayRPC relayRPC() {
        return relayRPC;
    }

    public boolean isManualPorts() {
        return manualPorts;
    }

    public FutureNAT portForwarding(final FutureDiscover futureDiscover) {
        DiscoverBuilder builder = new DiscoverBuilder(peer);
        return portForwarding(futureDiscover, builder);
    }

    /**
     * Setup UPNP or NATPMP port forwarding.
     *
     * @param futureDiscover The result of the discovery process. This information from the
     *                       discovery process is important to setup UPNP or NATPMP. If
     *                       this fails, then this future will also fail, and other means
     *                       to connect to the network needs to be found.
     * @return The future object that tells you if you are reachable (success),
     * if UPNP or NATPMP could be setup and then you are reachable
     * (success), or if it failed.
     */
    public FutureNAT portForwarding(final FutureDiscover futureDiscover, final DiscoverBuilder builder) {
        final FutureNAT futureNAT = new FutureNAT();
        futureDiscover.addListener(new BaseFutureAdapter<FutureDiscover>() {

            @Override
            public void operationComplete(FutureDiscover future) throws Exception {

                // set the peer that we contacted
                if (future.reporter() != null) {
                    futureNAT.reporter(future.reporter());
                }

                if (future.isFailed() && future.isNat() && !manualPorts) {
                    Ports externalPorts = setupPortforwarding(future.internalAddress().ipv4().toInetAddress().getHostAddress(), peer
                            .connectionBean().channelServer().channelServerConfiguration().portsForwarding());
                    if (externalPorts != null) {
                        final PeerAddress serverAddressOrig = peer.peerBean().serverPeerAddress();
                        final PeerAddress serverAddress = serverAddressOrig
                                .withIpv4Socket(
                                        PeerSocket4Address.create(
                                                (Inet4Address) future.externalAddress().ipv4().toInetAddress(),
                                                externalPorts.udpPort(),
                                                externalPorts.tcpPort(),
                                                externalPorts.udpPort() + 1))
                                .withIpInternalSocket(serverAddressOrig.ipv4Socket());

                        // set the new address regardless wheter it will succeed
                        // or not.
                        // The discover will eventually check wheter the
                        // announced ip matches the one that it sees.
                        peer.peerBean().serverPeerAddress(serverAddress);

                        // test with discover again
                        builder.peerAddress(futureNAT.reporter()).start()
                                .addListener(new BaseFutureAdapter<FutureDiscover>() {
                                    @Override
                                    public void operationComplete(FutureDiscover future) throws Exception {
                                        if (future.isSuccess()) {
                                            // UPNP or NAT-PMP was
                                            // successful, set flag
                                            PeerAddress newServerAddress = serverAddress.withUnreachable(false);
                                            peer.peerBean().serverPeerAddress(newServerAddress);
                                            futureNAT.done(future.peerAddress(), future.reporter());
                                        } else {
                                            // indicate relay
                                            PeerAddress pa = serverAddressOrig.withUnreachable(true);
                                            peer.peerBean().serverPeerAddress(pa);
                                            futureNAT.failed(future);
                                        }
                                    }
                                });
                    } else {
                        // indicate relay
                        PeerAddress pa = peer.peerBean().serverPeerAddress().withUnreachable(true);
                        peer.peerBean().serverPeerAddress(pa);
                        futureNAT.failed("could not setup NAT");
                    }
                } else if (future.isSuccess()) {
                    LOG.info("nothing to do, you are reachable from outside");
                    futureNAT.done(futureDiscover.peerAddress(), futureDiscover.reporter());
                } else {
                    LOG.info("not reachable, maybe your setup is wrong");
                    futureNAT.failed("could discover anything", future);
                }
            }
        });
        return futureNAT;
    }

    /**
     * The Dynamic and/or Private Ports are those from 49152 through 65535
     * (http://www.iana.org/assignments/port-numbers).
     *
     * @param internalHost The IP of the internal host
     * @return The new external ports if port forwarding seemed to be
     * successful, otherwise null
     */
    public Ports setupPortforwarding(final String internalHost, Ports ports) {
        LOG.debug("setup UPNP for host {} and ports {}", internalHost, ports);
        // new random ports
        boolean success;

        try {
            success = natUtils.mapUPNP(internalHost, peer.peerAddress().ipv4Socket().tcpPort(), peer.peerAddress().ipv4Socket().udpPort(),
                    ports.udpPort(), ports.tcpPort());
        } catch (Exception e) {
            LOG.error("cannot map UPNP", e);
            success = false;
        }

        if (!success) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("cannot find UPNP devices");
            }
            try {
                success = natUtils.mapPMP(peer.peerAddress().ipv4Socket().tcpPort(), peer.peerAddress().ipv4Socket().udpPort(), ports.udpPort(),
                        ports.tcpPort());
                if (!success) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("cannot find NAT-PMP devices");
                    }
                }
            } catch (NatPmpException e1) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("cannot find NAT-PMP devices ", e1);
                }
            }
        }
        if (success) {
            return ports;
        }
        return null;
    }

    public Shutdown startRelay(PeerAddress... relays) {
        return startRelay(Arrays.asList(relays));
    }

    public Shutdown startRelay(List<PeerAddress> relays) {
        distributedRelay.setupRelays(relays);

        final Shutdown shutdownRelay = new Shutdown() {
            @Override
            public BaseFuture shutdown() {
                return distributedRelay.shutdown();
            }
        };
        peer.addShutdownListener(shutdownRelay);

        if (relayMaintenance) {
            final PeerMapUpdateTask peerMapUpdateTask = new PeerMapUpdateTask(relayRPC, bootstrapBuilder, distributedRelay);
            peer.connectionBean().timer()
                    .scheduleAtFixedRate(peerMapUpdateTask, 0, peerMapUpdateIntervalSeconds, TimeUnit.SECONDS);

            final Shutdown shutdownTask = new Shutdown() {
                @Override
                public BaseFuture shutdown() {
                    peerMapUpdateTask.cancel();
                    return new FutureDone<Void>().done();
                }
            };
            peer.addShutdownListener(shutdownTask);

            return new Shutdown() {
                @Override
                public BaseFuture shutdown() {
                    peer.removeShutdownListener(shutdownTask);
                    peer.removeShutdownListener(shutdownRelay);
                    peerMapUpdateTask.cancel();
                    return distributedRelay.shutdown();
                }
            };
        } else {
            return new Shutdown() {
                @Override
                public BaseFuture shutdown() {
                    peer.removeShutdownListener(shutdownRelay);
                    return distributedRelay.shutdown();
                }
            };
        }
    }


    /**
     * This Method creates a {@link PeerConnection} to an unreachable (behind a
     * NAT) peer using an active relay of the unreachable peer. The connection
     * will be kept open until close() is called.
     *
     * @param relayPeerAddress
     * @param unreachablePeerAddress
     * @return {@link FutureDone}
     * @throws TimeoutException
     */
    public FutureDone<PeerConnection> startSetupRcon(final PeerAddress relayPeerAddress,
                                                     final PeerAddress unreachablePeerAddress) {
        checkRconPreconditions(relayPeerAddress, unreachablePeerAddress);

        final FutureDone<PeerConnection> futureDone = new FutureDone<PeerConnection>();
        final FuturePeerConnection fpc = peer.createPeerConnection(relayPeerAddress);
        fpc.addListener(new BaseFutureAdapter<FuturePeerConnection>() {
            // wait for the connection to the relay Peer
            @Override
            public void operationComplete(FuturePeerConnection future) throws Exception {
                if (fpc.isSuccess()) {
                    final PeerConnection peerConnection = fpc.peerConnection();
                    if (peerConnection != null) {
                        // create the necessary messages
                        final Message setUpMessage = createSetupMessage(relayPeerAddress, unreachablePeerAddress);

                        // send the message to the relay so it forwards it to
                        // the unreachable peer
                        FutureResponse futureResponse = RelayUtils.send(peerConnection, peer.peerBean(),
                                peer.connectionBean(), setUpMessage);

                        // wait for the unreachable peer to answer
                        futureResponse.addListener(new BaseFutureAdapter<FutureResponse>() {
                            @Override
                            public void operationComplete(FutureResponse future) throws Exception {
                                // get the PeerConnection which is
                                // cached in the
                                // PeerBean object
                                final PeerConnection openPeerConnection = peer.peerBean().peerConnection(
                                        unreachablePeerAddress.peerId());
                                if (openPeerConnection != null && openPeerConnection.isOpen()) {
                                    futureDone.done(openPeerConnection);
                                } else {
                                    LOG.error("The reverse connection to the unreachable peer failed.");
                                    handleFail(futureDone, "No reverse connection could be established");
                                }

                                // can close the connection to the relay
                                // peer
                                peerConnection.close();
                            }
                        });
                    } else {
                        handleFail(futureDone, "The PeerConnection was null!");
                    }
                } else {
                    handleFail(futureDone, "no channel could be established");
                }
            }

            private void handleFail(final FutureDone<PeerConnection> futureDone, final String failMessage) {
                LOG.error(failMessage);
                futureDone.failed(failMessage);
            }

            // this message is sent to the relay peer to initiate the rcon setup
            private Message createSetupMessage(final PeerAddress relayPeerAddress,
                                               final PeerAddress unreachablePeerAddress) {
                Message setUpMessage = new Message();
                setUpMessage.version(peer.connectionBean().p2pId());
                setUpMessage.sender(peer.peerAddress());
                setUpMessage.recipient(relayPeerAddress.withPeerId(unreachablePeerAddress.peerId()));
                setUpMessage.command(RPC.Commands.RCON.getNr());
                setUpMessage.type(Type.REQUEST_1);
                // setUpMessage.keepAlive(true);
                return setUpMessage;
            }
        });
        return futureDone;
    }

    /**
     * This method checks if a reverse connection setup with startSetupRcon() is
     * possible.
     *
     * @param relayPeerAddress
     * @param unreachablePeerAddress
     * @param timeoutSeconds
     */
    private void checkRconPreconditions(final PeerAddress relayPeerAddress, final PeerAddress unreachablePeerAddress) {
        if (relayPeerAddress == null || unreachablePeerAddress == null) {
            throw new IllegalArgumentException(
                    "either the relay PeerAddress or the unreachablePeerAddress or both was/were null!");
        }

        // If we are already a relay of the unreachable peer, we shouldn't use a
        // reverse connection setup. It just doesn't make sense!
        if (peer.peerAddress().peerId().equals(relayPeerAddress.peerId())) {
            throw new IllegalStateException(
                    "We are already a relay for the target peer. We shouldn't use a reverse connection to connect to the targeted peer!");
        }
    }


}
