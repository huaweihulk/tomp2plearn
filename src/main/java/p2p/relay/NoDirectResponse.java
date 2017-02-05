package p2p.relay;

import trunk.social.p2p.connection.Responder;
import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.message.Message;
import trunk.social.p2p.message.Message.Type;

/**
 * Responder that doesn't respond to the Sender of a Message, but saves the
 * response message. This class is used for unreachable peers that are connected
 * to a relay, so they don't reply to the original Sender of a message but to a
 * relay peer
 * 
 * @author Raphael Voellmy
 * 
 */
class NoDirectResponse implements Responder {

    private Message response;

    /**
     * Saves the response message. The response message can be retrieved using {@link NoDirectResponse#response()}
     */
    public FutureDone<Void> response(Message responseMessage) {
        this.response = responseMessage;
        return new FutureDone<Void>().done();
    }

    /**
     * Retrieves the response message
     * 
     * @return the response message
     */
    public Message response() {
        return response;
    }

    /**
     * <strong>Do not use!</strong> This method doesn't do anything.
     */
    public void failed(Type type, String reason) {
        // do nothing
    }

    /**
     * <strong>Do not use!</strong> This method doesn't do anything.
     */
    public void responseFireAndForget() {
        // do nothing
    }
}
