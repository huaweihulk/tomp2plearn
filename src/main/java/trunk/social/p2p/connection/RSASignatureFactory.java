package trunk.social.p2p.connection;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trunk.social.p2p.message.RSASignatureCodec;
import trunk.social.p2p.message.SignatureCodec;
import trunk.social.p2p.p2p.PeerBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

/**
 * The signature is done with SHA1withRSA.
 * 
 * @author Seppi
 */
public class RSASignatureFactory implements SignatureFactory {
	
    private static final long serialVersionUID = -4788883684758981915L;
	private static final Logger LOG = LoggerFactory.getLogger(RSASignatureFactory.class);
	
	/**
	 * @return The signature mechanism
	 */
	private Signature signatureInstance() {
		try {
			return Signature.getInstance("SHA1withRSA");
		} catch (NoSuchAlgorithmException e) {
			LOG.error("could not find algorithm", e);
			return null;
		}
	}

	@Override
	public PublicKey decodePublicKey(final byte[] me) {
		X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(me);
		try {
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return keyFactory.generatePublic(pubKeySpec);
		} catch (NoSuchAlgorithmException e) {
			LOG.error("could not find algorithm", e);
			return null;
		} catch (InvalidKeySpecException e) {
			LOG.error("wrong keyspec", e);
			return null;
		}
	}

	// decodes with header
	@Override
	public PublicKey decodePublicKey(ByteBuf buf) {
		if (buf.readableBytes() < 2) {
			return null;
		}
		int len = buf.getUnsignedShort(buf.readerIndex());

		if (buf.readableBytes() - 2 < len) {
			return null;
		}
		buf.skipBytes(2);

		if (len <= 0) {
			return PeerBuilder.EMPTY_PUBLIC_KEY;
		}

		byte me[] = new byte[len];
		buf.readBytes(me);
		return decodePublicKey(me);
	}

	@Override
	public void encodePublicKey(PublicKey publicKey, ByteBuf buf) {
		byte[] data = publicKey.getEncoded();
		buf.writeShort(data.length);
		buf.writeBytes(data);
	}

	@Override
	public SignatureCodec sign(PrivateKey privateKey, ByteBuffer[] byteBuffers) throws InvalidKeyException,
			SignatureException, IOException {
		Signature signature = signatureInstance();
		signature.initSign(privateKey);
		int len = byteBuffers.length;
		for (int i = 0; i < len; i++) {
			ByteBuffer buffer = byteBuffers[i];
			signature.update(buffer);
		}
		
		byte[] signatureData = signature.sign();
		return new RSASignatureCodec(signatureData);
	}

	@Override
	public boolean verify(PublicKey publicKey, ByteBuffer[] byteBuffers, SignatureCodec signatureEncoded)
			throws SignatureException, InvalidKeyException {
		Signature signature = signatureInstance();
		signature.initVerify(publicKey);
		int len = byteBuffers.length;
		for (int i = 0; i < len; i++) {
			ByteBuffer buffer = byteBuffers[i];
			signature.update(buffer);
		}
		byte[] signatureReceived = signatureEncoded.encode();
		return signature.verify(signatureReceived);
	}

	@Override
	public Signature update(PublicKey receivedPublicKey, ByteBuffer[] byteBuffers)
			throws InvalidKeyException, SignatureException {
		Signature signature = signatureInstance();
		signature.initVerify(receivedPublicKey);
		int arrayLength = byteBuffers.length;
		for (int i = 0; i < arrayLength; i++) {
			signature.update(byteBuffers[i]);
		}
		return signature;
	}

	@Override
    public SignatureCodec signatureCodec(ByteBuf buf) {
	    return new RSASignatureCodec(buf);
    }

	@Override
	public int signatureSize() {
		return RSASignatureCodec.SIGNATURE_SIZE;
	}
}
