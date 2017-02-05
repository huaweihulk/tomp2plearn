/*
 * Copyright 2013 Thomas Bocek
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

package p2p.connection;

import io.netty.buffer.ByteBuf;
import trunk.social.p2p.message.SignatureCodec;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.*;

/**
 * This interface is used in the encoder and decoders. A user may set its own
 * signature algorithm.
 * 
 * @author Thomas Bocek
 * 
 */
public interface SignatureFactory extends Serializable {

	/**
	 * The public key is sent over the wire, thus the decoding of it needs
	 * special handling.
	 * 
	 * @param me
	 *            The byte array that contains the public key
	 * @return The decoded public key
	 */
	PublicKey decodePublicKey(byte[] me);

	PublicKey decodePublicKey(ByteBuf buf);

	void encodePublicKey(PublicKey publicKey, ByteBuf buf);
	
	SignatureCodec sign(PrivateKey private1, ByteBuffer[] byteBuffers) throws InvalidKeyException, SignatureException, IOException;
	
	boolean verify(PublicKey public1, ByteBuffer[] byteBuffers, SignatureCodec signature) throws SignatureException,
			InvalidKeyException;


	Signature update(PublicKey publicKey, ByteBuffer[] byteBuffers) throws InvalidKeyException, SignatureException;

	/**
	 * Get the signature codec and read the signature directly from the buffer
	 * 
	 * @param buf the buffer containing the signature at the reader index
	 * @return the signature codec
	 * @throws IOException if the signature cannot be read from the buffer
	 */
	SignatureCodec signatureCodec(ByteBuf buf);
	
	/**
	 * @return the number of bytes of the signature codec
	 */
	int signatureSize();

}
