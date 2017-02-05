package trunk.social.p2p.message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.connection.SignatureFactory;
import trunk.social.p2p.storage.AlternativeCompositeByteBuf;

import java.net.InetSocketAddress;

public class TomP2PCumulationTCP extends ChannelInboundHandlerAdapter {

	private static final Logger LOG = LoggerFactory
			.getLogger(TomP2PCumulationTCP.class);

	private final Decoder decoder;
	private final ByteBufAllocator byteBufAllocator;
	private AlternativeCompositeByteBuf cumulation = null;

	private int lastId = 0;

	public TomP2PCumulationTCP(final SignatureFactory signatureFactory, ByteBufAllocator byteBufAllocator) {
		decoder = new Decoder(signatureFactory);
		this.byteBufAllocator = byteBufAllocator;
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, final Object msg)
			throws Exception {

		if (!(msg instanceof ByteBuf)) {
			ctx.fireChannelRead(msg);
			return;
		}
		
		final ByteBuf buf = (ByteBuf) msg;
		final InetSocketAddress sender = (InetSocketAddress) ctx.channel().remoteAddress();

		try {
			if (cumulation == null) {
				cumulation = AlternativeCompositeByteBuf.compBuffer(byteBufAllocator, buf);
			} else {
				cumulation.addComponent(buf);
			}
			decoding(ctx, sender);
		} catch (Throwable t) {
			LOG.error("Error in TCP decoding", t);
            throw new Exception(t);
		} finally {
			//the cumulation buffer now maintains the buffer buf, so we can release it here
			buf.release();
			if (!cumulation.isReadable()) {
                cumulation.release();
                cumulation = null;
            } // no need to discard bytes as this was done in the decoder already
		}
	}

	private void decoding(final ChannelHandlerContext ctx,
			final InetSocketAddress sender) {
		boolean finished = true;
		boolean moreData = true;
		while (finished && moreData) {
			finished = decoder.decode(ctx, cumulation, (InetSocketAddress) ctx
					.channel().localAddress(), sender);
			if (finished) {
				lastId = decoder.message().messageId();
				moreData = cumulation.readableBytes() > 0;
				ctx.fireChannelRead(decoder.prepareFinish());
			} else {
				if(decoder.message() == null) {
					//wait for more data. This may happen if we don't get the first 58 bytes, 
					//which is the size of the header.
					return;
				}
				// this id was the same as the last and the last message already
				// finished the parsing. So this message
				// is finished as well although it may send only partial data.
				if (lastId == decoder.message().messageId()) {
					finished = true;
					moreData = cumulation.readableBytes() > 0;
					ctx.fireChannelRead(decoder.prepareFinish());
				} else if (decoder.message().isStreaming()) {
					ctx.fireChannelRead(decoder.message());
				}
			}
		}
	}

	@Override
	public void channelInactive(final ChannelHandlerContext ctx)
			throws Exception {
		decoder.release();
		final InetSocketAddress sender = (InetSocketAddress) ctx.channel()
				.remoteAddress();
		try {
			if (cumulation != null) {
				decoding(ctx, sender);
			}
		} catch (Throwable t) {
			LOG.error("Error in TCP decoding. (Inactive)", t);
            throw new Exception(t);
		} finally {
			if (cumulation != null) {
				cumulation.release();
				cumulation = null;
			}
			ctx.fireChannelInactive();
		}
	}

	@Override
	public void exceptionCaught(final ChannelHandlerContext ctx,
			final Throwable cause) throws Exception {
		if (cumulation != null) {
			cumulation.release();
			cumulation = null;
		}
		Message msg = decoder.message();
		decoder.release();
		// don't use getLocalizedMessage() -
		// http://stackoverflow.com/questions/8699521/any-way-to-ignore-only-connection-reset-by-peer-ioexceptions
		if (cause.getMessage().equals("Connection reset by peer")) {
			return; // ignore
		} else if (cause
				.getMessage()
				.equals("An existing connection was forcibly closed by the remote host")) {
			// with windows we see the following message
			return; // ignore
		} else if (cause
				.getMessage()
				.equals("Eine vorhandene Verbindung wurde vom Remotehost geschlossen")) {
			return;
		} else if (cause.getMessage().equals("Connexion ré-initialisée par le correspondant")) {
		    return;
		}
		if (msg == null && decoder.lastContent() == null) {
			LOG.error("Exception in decoding TCP. Occurred before starting to decode.", cause);
		} else if (msg != null && !msg.isDone()) {
			LOG.error("Exception in decoding TCP. Occurred after starting to decode.", cause);
		}
	}
}
