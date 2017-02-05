package p2p.message;

import io.netty.buffer.ByteBuf;
import trunk.social.p2p.utils.Utils;

import java.io.IOException;

public class Buffer {

	private final ByteBuf buffer;
	private final int length;

	private int read = 0;

	public Buffer(final ByteBuf buffer, final int length) {
		this.buffer = buffer;
		this.length = length;
	}

	public Buffer(ByteBuf buffer) {
		this.buffer = buffer;
		this.length = buffer.readableBytes();
	}

	public int length() {
		return length;
	}

	public ByteBuf buffer() {
		return buffer;
	}

	public int readable() {
		int remaining = length - read;
		int available = buffer.readableBytes();
		return Math.min(remaining, available);
	}

	public boolean isComplete() {
		return length == buffer.readableBytes();
	}

	public int incRead(final int read) {
		this.read += read;
		return this.read;
	}

	public Object object() throws ClassNotFoundException, IOException {
		return Utils.decodeJavaObject(buffer.duplicate().readerIndex(0));
	}

	public void reset() {
		read = 0;
		buffer.resetReaderIndex();
	}
	
	@Override
    public int hashCode() {
        return buffer.duplicate().readerIndex(0).hashCode() ^ length;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Buffer)) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        final Buffer b = (Buffer) obj;
        if(b.length != length) {
            return false;
        }
        return b.buffer.duplicate().readerIndex(0).equals(buffer.duplicate().readerIndex(0));
    }
}
