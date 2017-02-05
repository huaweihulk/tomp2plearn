package p2p.connection;

import trunk.social.p2p.message.Message;

/**
 * Default sending behavior for UDP and TCP messages. Depending whether the
 * recipient is relayed, slow and on the message size, decisions can be made
 * here.
 * 
 * @author Nico Rutishauser
 * 
 */
public class DefaultSendBehavior implements SendBehavior {

	private static final int MTU = 1000;
	
	@Override
	public SendMethod tcpSendBehavior(Dispatcher dispatcher, Message message) {
		if(message.recipient().peerId().equals(message.sender().peerId())) {
			// shortcut, just send to yourself
			return SendMethod.SELF;
		}
		//also check if we have multiple peers
		if(dispatcher.responsibleFor(message.recipient().peerId())) {
			// shortcut, just send to yourself
			return SendMethod.SELF;
		}
		
		if(message.recipientReflected() != null) {
			return SendMethod.DIRECT;
		}

		if (message.recipient().relaySize() > 0) {
			if (message.sender().relaySize() > 0) {
				// reverse connection is not possible because both peers are
				// relayed. Thus send the message to
				// one of the receiver's relay peers
				return SendMethod.HOLEP_RELAY;
			} else {
				// Messages with small size can be sent over relay, other messages should be sent directly (more efficient)
				if(message.estimateSize() > MTU) {
					return SendMethod.RCON;
				} else {
					return SendMethod.HOLEP_RELAY;
				}
			}
		}
		// send directly
		return SendMethod.DIRECT;
	}

	@Override
	public SendMethod udpSendBehavior(Dispatcher dispatcher, Message message) throws UnsupportedOperationException {
		if(message.recipient().peerId().equals(message.sender().peerId())) {
			// shortcut, just send to yourself
			return SendMethod.SELF;
		}
		//also check if we have multiple peers
		if(dispatcher.responsibleFor(message.recipient().peerId())) {
			// shortcut, just send to yourself
			return SendMethod.SELF;
		}
		
		if(message.recipientReflected() != null) {
			return SendMethod.DIRECT;
		}
		
		if(message.recipient().relaySize() > 0) {
			return SendMethod.HOLEP_RELAY;
		}

		return SendMethod.DIRECT;
	}
}
