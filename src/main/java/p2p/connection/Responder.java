package p2p.connection;

import trunk.social.p2p.futures.FutureDone;
import trunk.social.p2p.message.Message;

public interface Responder {

	public abstract FutureDone<Void> response(Message responseMessage);
	
	public abstract void failed(Message.Type type, String reason);

	public abstract void responseFireAndForget();

}