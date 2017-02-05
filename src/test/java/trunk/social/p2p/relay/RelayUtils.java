package trunk.social.p2p.relay;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import trunk.social.p2p.connection.ConnectionBean;
import trunk.social.p2p.connection.PeerBean;
import trunk.social.p2p.connection.PeerConnection;
import trunk.social.p2p.connection.RequestHandler;
import trunk.social.p2p.connection.SignatureFactory;
import trunk.social.p2p.futures.BaseFutureAdapter;
import trunk.social.p2p.futures.FutureChannelCreator;
import trunk.social.p2p.futures.FutureResponse;
import trunk.social.p2p.message.Buffer;
import trunk.social.p2p.message.Decoder;
import trunk.social.p2p.message.Encoder;
import trunk.social.p2p.message.Message;
import trunk.social.p2p.p2p.Peer;
import trunk.social.p2p.peers.Number160;
import trunk.social.p2p.peers.PeerAddress;
import trunk.social.p2p.peers.PeerMap;
import trunk.social.p2p.peers.PeerMapConfiguration;
import trunk.social.p2p.peers.PeerStatistic;
import trunk.social.p2p.storage.AlternativeCompositeByteBuf;

public class RelayUtils {

	private static final Logger LOG = LoggerFactory.getLogger(RelayUtils.class);
	private static Charset charset = Charset.forName("UTF-8");
	private static CharsetEncoder encoder = charset.newEncoder();
	private static CharsetDecoder decoder = charset.newDecoder();
	
	private RelayUtils() {
		// only static methods
	}

	public static List<Map<Number160, PeerStatistic>> unflatten(Collection<PeerAddress> map, PeerAddress sender) {
		PeerMapConfiguration peerMapConfiguration = new PeerMapConfiguration(sender.peerId());
		PeerMap peerMap = new PeerMap(peerMapConfiguration);
		for (PeerAddress peerAddress : map) {
			LOG.debug("found peer in unflatten for relaying, {}", peerAddress);
			peerMap.peerFound(peerAddress, null, null, null);
		}
		return peerMap.peerMapVerified();
	}

	public static Collection<PeerAddress> flatten(List<Map<Number160, PeerStatistic>> maps) {
		Collection<PeerAddress> result = new ArrayList<PeerAddress>();
		for (Map<Number160, PeerStatistic> map : maps) {
			for (PeerStatistic peerStatistic : map.values()) {
				result.add(peerStatistic.peerAddress());
			}
		}
		return result;
	}

	/**
	 * Composes all messages of a list into a single buffer object, ready to be transmitted over the network.
	 * The composing happens in-order. Alternatively, the message size and then the message is written to the
	 * buffer. Use {@link MessageBuffer#decomposeCompositeBuffer(ByteBuf)} to disassemble.
	 * 
	 * @param messages the messages to compose
	 * @param signatureFactory the signature factory, necessary for encoding the messages
	 * @return a single buffer holding all messages of the list
	 */
	public static ByteBuf composeMessageBuffer(List<Message> messages, SignatureFactory signatureFactory) {
		ByteBuf buffer = Unpooled.buffer();
		for (Message msg : messages) {
			try {
				msg.restoreContentReferences();
				msg.restoreBuffers();
				Buffer encoded = encodeMessage(msg, signatureFactory);

				buffer.writeInt(encoded.length());
				buffer.writeBytes(encoded.buffer());
			} catch (Exception e) {
				LOG.error("Cannot encode the buffered message. Skip it.", e);
			}
		}
		return buffer;
	}

	/**
	 * Decomposes a buffer containing multiple buffers into an (ordered) list of small buffers. Alternating,
	 * the size of the message and the message itself are encoded in the message buffer. First, the size is
	 * read, then k bytes are read from the buffer (the message). Then again, the size of the next messages is
	 * determined.
	 * 
	 * @param messageBuffer the message buffer
	 * @return a list of buffers
	 */
	public static List<Message> decomposeCompositeBuffer(ByteBuf messageBuffer, InetSocketAddress recipient, InetSocketAddress sender,
			SignatureFactory signatureFactory) {
		List<Message> messages = new ArrayList<Message>();
		while (messageBuffer.readableBytes() > 0) {
			int size = messageBuffer.readInt();
			ByteBuf message = messageBuffer.readBytes(size);
			
			try {
				Message decodedMessage = decodeMessage(message, recipient, sender, signatureFactory);
				messages.add(decodedMessage);
			} catch (Exception e) {
				LOG.error("Cannot decode buffered message. Skip it.", e);
			}
		}

		return messages;
	}
	
	/**
	 * Encodes a message into a buffer, such that it can be used as a message payload (piggybacked), stored, etc.
	 */
	public static Buffer encodeMessage(Message message, SignatureFactory signatureFactory) throws InvalidKeyException, SignatureException, IOException {
		Encoder e = new Encoder(signatureFactory);
		AlternativeCompositeByteBuf buf = AlternativeCompositeByteBuf.compBuffer(AlternativeCompositeByteBuf.UNPOOLED_HEAP);
		e.write(buf, message, message.receivedSignature());
		System.err.println("got: "+buf);
		return new Buffer(buf);
	}

	/**
	 * Decodes a message which was encoded using {{@link #encodeMessage(Message, SignatureFactory)}}.
	 */
	public static Message decodeMessage(ByteBuf buf, InetSocketAddress recipient, InetSocketAddress sender, SignatureFactory signatureFactory)
	        throws InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException, SignatureException, IOException {
		Decoder d = new Decoder(signatureFactory);
		final int readerBefore = buf.readerIndex();
		d.decodeHeader(buf, recipient, sender);
		final boolean donePayload = d.decodePayload(buf);
		d.decodeSignature(buf, readerBefore, donePayload);
		return d.message();
	}
	
	/**
	 * Basically does the same as
	 * {@link MessageUtils#decodeMessage(Buffer, InetSocketAddress, InetSocketAddress, SignatureFactory)}, but
	 * in addition checks that the relay peers of the decoded message are set correctly
	 */
	public static Message decodeRelayedMessage(ByteBuf buf, InetSocketAddress recipient, InetSocketAddress sender,
			SignatureFactory signatureFactory) throws InvalidKeyException, NoSuchAlgorithmException,
			InvalidKeySpecException, SignatureException, IOException {
		final Message decodedMessage = decodeMessage(buf, recipient, sender, signatureFactory);
		return decodedMessage;
	}
	
	/**
	 * Calculates the size of the message
	 */
	public static int getMessageSize(Message message, SignatureFactory signatureFactory) throws InvalidKeyException, SignatureException, IOException {
		// TODO instead of real encoding, calculate it using the content references
		int size = encodeMessage(message, signatureFactory).length();
		message.restoreContentReferences();
		message.restoreBuffers();
		return size;
	}

	/**
	 * Encodes any String into a buffer to send it with a message
	 * 
	 * @param content the String to encode into a buffer
	 * @return a buffer containing the (encoded) String.
	 */
	public static Buffer encodeString(String content) {
		if (content == null) {
			return null;
		}

		ByteBuffer byteBuffer;
		synchronized (encoder) {
			encoder.reset();
			try {
				byteBuffer = encoder.encode(CharBuffer.wrap(content));
			} catch (CharacterCodingException e) {
				return null;
			}
			encoder.flush(byteBuffer);
		}
		ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(byteBuffer);
		return new Buffer(wrappedBuffer);
	}

	/**
	 * Decodes buffer containing a String
	 * 
	 * @param buffer the buffer received in a message
	 * @return the encoded String
	 */
	public static String decodeString(Buffer buffer) {
		if (buffer == null || buffer.buffer() == null) {
			return null;
		}

		ByteBuffer nioBuffer = buffer.buffer().nioBuffer();
		synchronized (decoder) {
			decoder.reset();
			CharBuffer decoded;
			try {
				decoded = decoder.decode(nioBuffer);
			} catch (CharacterCodingException e) {
				return null;
			}
			decoder.flush(decoded);
			return decoded.toString();
		}
	}
	
	/**
	 * Send a Message from one Peer to another Peer internally. This avoids the
	 * overhead of sendDirect.
	 */
	private static void send(final PeerConnection peerConnection, PeerBean peerBean, ConnectionBean connectionBean, final FutureResponse futureResponse) {
		final RequestHandler<FutureResponse> requestHandler = new RequestHandler<FutureResponse>(futureResponse, peerBean, connectionBean, connectionBean.channelServer().channelServerConfiguration());
		final FutureChannelCreator fcc = peerConnection.acquire(futureResponse);
		fcc.addListener(new BaseFutureAdapter<FutureChannelCreator>() {
			@Override
			public void operationComplete(FutureChannelCreator future) throws Exception {
				if (future.isSuccess()) {
					requestHandler.sendTCP(peerConnection.channelCreator(), peerConnection);
				} else {
					futureResponse.failed(future);
				}
			}
		});
	}

	/**
	 * Send a Message from one Peer to another Peer internally. This avoids the
	 * overhead of sendDirect. This Method is used for relaying and reverse
	 * Connection setup.
	 * @return the response
	 */
	public static FutureResponse send(final PeerConnection peerConnection, PeerBean peerBean, ConnectionBean connectionBean, Message message) {
		final FutureResponse futureResponse = new FutureResponse(message);
		send(peerConnection, peerBean, connectionBean, futureResponse);
		return futureResponse;
	}
	
	/**
	 * Opens a new peer connection to the receiver and sends the message through it.
	 * @param peer
	 * @param message
	 * @return
	 */
	public static FutureResponse connectAndSend(final Peer peer, final Message message) {
		final FutureResponse futureResponse = new FutureResponse(message);
		final RequestHandler<FutureResponse> requestHandler = new RequestHandler<FutureResponse>(futureResponse, peer.peerBean(), peer.connectionBean(), peer.connectionBean().channelServer().channelServerConfiguration());
		final FutureChannelCreator fpc = peer.connectionBean().reservation().create(0, 1);
		
		fpc.addListener(new BaseFutureAdapter<FutureChannelCreator>() {
            public void operationComplete(final FutureChannelCreator futureChannelCreator) throws Exception {
                if (futureChannelCreator.isSuccess()) {
                	requestHandler.sendTCP(fpc.channelCreator(), null);
                } else {
                    futureResponse.failed(fpc);
                }
            }
        });
		
		return futureResponse;
	}
}
