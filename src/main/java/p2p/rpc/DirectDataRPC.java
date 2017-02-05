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
package p2p.rpc;

import io.netty.buffer.Unpooled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.connection.*;
import trunk.social.p2p.futures.FutureResponse;
import trunk.social.p2p.message.Buffer;
import trunk.social.p2p.message.Message;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.utils.Utils;

import java.io.IOException;

public class DirectDataRPC extends DispatchHandler {
    private static final Logger LOG = LoggerFactory.getLogger(DirectDataRPC.class);

    private volatile RawDataReply rawDataReply;

    private volatile ObjectDataReply objectDataReply;

    public DirectDataRPC(PeerBean peerBean, ConnectionBean connectionBean) {
        super(peerBean, connectionBean);
        register(RPC.Commands.DIRECT_DATA.getNr());
    }
    
    public Message sendInternal0(final PeerAddress remotePeer,
                                 final SendDirectBuilderI sendDirectBuilder) {
    	 return createMessage(remotePeer, RPC.Commands.DIRECT_DATA.getNr(),
                 sendDirectBuilder.isRaw() ? Message.Type.REQUEST_1 : Message.Type.REQUEST_2);
    }

    /**
     * Sends data directly to a peer. Make sure you have set up a reply handler. This is an RPC.
     * 
     * @param remotePeer
     *            The remote peer to store the data
     */
    public RequestHandler<FutureResponse> sendInternal(final FutureResponse futureResponse,
            final SendDirectBuilderI sendDirectBuilder) {        
    	Message message = futureResponse.request();
        if (sendDirectBuilder.isSign()) {
            message.publicKeyAndSign(sendDirectBuilder.keyPair());
        }
        message.streaming(sendDirectBuilder.isStreaming());

        if (sendDirectBuilder.isRaw()) {
        	Buffer buffer = new Buffer(sendDirectBuilder.dataBuffer().toByteBuf());
            message.buffer(buffer);
        } else {
            byte[] me;
            try {
                me = Utils.encodeJavaObject(sendDirectBuilder.object());
                message.buffer(new Buffer(Unpooled.wrappedBuffer(me)));
            } catch (IOException e) {
                futureResponse.failed("Cannot encode object.", e);
            }       
        }

        return new RequestHandler<FutureResponse>(futureResponse, peerBean(), connectionBean(),
                sendDirectBuilder);
    }

    public FutureResponse send(final PeerAddress remotePeer, final SendDirectBuilderI sendDirectBuilder,
            final ChannelCreator channelCreator) {
    	Message message = sendInternal0(remotePeer, sendDirectBuilder);
    	final FutureResponse futureResponse = new FutureResponse(message);
        final RequestHandler<FutureResponse> requestHandler = sendInternal(futureResponse, sendDirectBuilder);
        if (!sendDirectBuilder.isForceUDP()) {
            return requestHandler.sendTCP(channelCreator);
        } else {
            return requestHandler.sendUDP(channelCreator);
        }
    }

    public void rawDataReply(final RawDataReply rawDataReply) {
        this.rawDataReply = rawDataReply;
    }

    public void objectDataReply(ObjectDataReply objectDataReply) {
        this.objectDataReply = objectDataReply;
    }

    public boolean hasRawDataReply() {
        return rawDataReply != null;
    }

    public boolean hasObjectDataReply() {
        return objectDataReply != null;
    }

    @Override
    public void handleResponse(final Message message, PeerConnection peerConnection, final boolean sign, Responder responder) throws Exception {
        if (!((message.type() == Message.Type.REQUEST_1 || message.type() == Message.Type.REQUEST_2) && message
                .command() == RPC.Commands.DIRECT_DATA.getNr())) {
            throw new IllegalArgumentException("Message content is wrong");
        }
        final Message responseMessage = createResponseMessage(message, Message.Type.OK);

        if (sign) {
            responseMessage.publicKeyAndSign(peerBean().getKeyPair());
        }
        final RawDataReply rawDataReply2 = rawDataReply;
        final ObjectDataReply objectDataReply2 = objectDataReply;
        if (message.type() == Message.Type.REQUEST_1 && rawDataReply2 == null) {
        	LOG.warn("no raw reply handler registered");
            responseMessage.type(Message.Type.NOT_FOUND);
        } else if (message.type() == Message.Type.REQUEST_2 && objectDataReply2 == null) {
        	LOG.warn("no object reply handler registered");
            responseMessage.type(Message.Type.NOT_FOUND);
        } else {
            final Buffer requestBuffer = message.buffer(0);
            // The user can reply with null, indicating not found or returning
            // the request buffer, which means nothing is returned.
            // Or an exception can be thrown.
            if (message.type() == Message.Type.REQUEST_1) {
                LOG.debug("handling REQUEST_1.");
                final Buffer replyBuffer = rawDataReply2.reply(message.sender(), requestBuffer,
                        message.isDone());
                if (replyBuffer == null && message.isDone()) {
                	LOG.warn("raw reply is null, returning not found - debug message");
                    responseMessage.type(Message.Type.NOT_FOUND);
                } else if (replyBuffer != requestBuffer) {
                    // can be partial as well
                    if (!replyBuffer.isComplete()) {
                        responseMessage.streaming();
                    }
                    responseMessage.buffer(replyBuffer);
                }
            } else { // no streaming here when we deal with objects
                Object obj = Utils.decodeJavaObject(requestBuffer.buffer());
                LOG.debug("handling {}", obj);

                Object reply = objectDataReply2.reply(message.sender(), obj);
                if (reply == null) {
                	LOG.warn("object reply is null, returning not found - debug message");
                    responseMessage.type(Message.Type.NOT_FOUND);
                } else if (reply == obj) {
                    responseMessage.type(Message.Type.OK);
                } else {
                    byte[] me = Utils.encodeJavaObject(reply);
                    responseMessage.buffer(new Buffer(Unpooled.wrappedBuffer(me)));
                }
            }
            requestBuffer.buffer().release();
        }
        responder.response(responseMessage);
    }
}
