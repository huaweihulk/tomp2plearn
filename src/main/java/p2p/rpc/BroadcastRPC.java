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
package p2p.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.connection.*;
import trunk.social.p2p.futures.FutureResponse;
import trunk.social.p2p.message.DataMap;
import trunk.social.p2p.message.Message;
import trunk.social.p2p.p2p.BroadcastHandler;
import trunk.social.p2p.p2p.builder.BroadcastBuilder;
import trunk.social.p2p.peers.PeerAddress;

public class BroadcastRPC extends DispatchHandler {

    private static final Logger LOG = LoggerFactory.getLogger(BroadcastRPC.class);

    private final BroadcastHandler broadcastHandler;

    public BroadcastRPC(PeerBean peerBean, ConnectionBean connectionBean, BroadcastHandler broadcastHandler) {
        super(peerBean, connectionBean);
        register(RPC.Commands.BROADCAST.getNr());
        this.broadcastHandler = broadcastHandler;
    }

    public FutureResponse send(final PeerAddress remotePeer, final BroadcastBuilder broadcastBuilder,
                               final ChannelCreator channelCreator, final ConnectionConfiguration configuration, int bucketNr) {
        final Message message = createMessage(remotePeer, RPC.Commands.BROADCAST.getNr(), Message.Type.REQUEST_FF_1);
        message.intValue(broadcastBuilder.hopCounter());
        message.intValue(bucketNr);
        message.key(broadcastBuilder.messageKey());
        
        if (broadcastBuilder.dataMap() != null) {
            message.setDataMap(new DataMap(broadcastBuilder.dataMap()));
        }
        final FutureResponse futureResponse = new FutureResponse(message);
        final RequestHandler<FutureResponse> requestHandler = new RequestHandler<FutureResponse>(
                futureResponse, peerBean(), connectionBean(), configuration);
        if (!broadcastBuilder.isUDP()) {
            return requestHandler.sendTCP(channelCreator);
        } else {
            return requestHandler.fireAndForgetUDP(channelCreator);
        }
    }

    @Override
    public void handleResponse(final Message message, PeerConnection peerConnection, final boolean sign, Responder responder) throws Exception {
        if (!(message.type() == Message.Type.REQUEST_FF_1 && message.command() == RPC.Commands.BROADCAST.getNr())) {
            throw new IllegalArgumentException("Message content is wrong for this handler.");
        }
        LOG.debug("received BRODACAST message: {}", message);
        broadcastHandler.receive(message);
        if(message.isUdp()) {
            responder.responseFireAndForget();
        } else {
            responder.response(createResponseMessage(message, Message.Type.OK));
        }
    }

    /**
     * @return The broadcast handler that is currently used
     */
    public BroadcastHandler broadcastHandler() {
        return broadcastHandler;
    }
}
