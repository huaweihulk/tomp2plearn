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

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * A simple Bloom Filter (see http://en.wikipedia.org/wiki/Bloom_filter) that
 * uses java.util.Random as a primitive hash function, and which implements
 * Java's Set interface for convenience. Only the add(), addAll(), contains(),
 * and containsAll() methods are implemented. Calling any other method will
 * yield an UnsupportedOperationException. This code may be used, modified, and
 * redistributed provided that the author tag below remains intact.
 * 
 * @author Ian Clarke <ian@uprizer.com>
 * @author Thomas Bocek <tom@tomp2p.net> Added methods to get and create a
 *         SimpleBloomFilter from existing data. The data can be either a BitSet
 *         or a bye[].
 * @param <E>
 *            The type of object the BloomFilter should contain
 */
public class SimpleBloomFilter<E> implements Set<E>, Serializable {

	private static final Logger LOG = LoggerFactory.getLogger(SimpleBloomFilter.class);

	private static final long serialVersionUID = 3527833617516722215L;

	private static final int SIZE_HEADER_LENGTH = 2;

	private static final int SIZE_HEADER_ELEMENTS = 4;

	public static final int SIZE_HEADER = SIZE_HEADER_LENGTH + SIZE_HEADER_ELEMENTS;

	private final int k;

	private final BitSet bitSet;

	private final int byteArraySize, bitArraySize, expectedElements;

	/**
	 * Construct an empty SimpleBloomFilter. You must specify the number of bits
	 * in the Bloom Filter, and also you should specify the number of items you
	 * expect to add. The latter is used to choose some optimal internal values
	 * to minimize the false-positive rate (which can be estimated with
	 * expectedFalsePositiveRate()).
	 * 
	 * @param byteArraySize
	 *            The number of bits in multiple of 8 in the bit array (often
	 *            called 'm' in the context of bloom filters).
	 * @param expectedElements
	 *            The typical number of items you expect to be added to the
	 *            SimpleBloomFilter (often called 'n').
	 */
	public SimpleBloomFilter(final int byteArraySize, final int expectedElements) {
		this(byteArraySize, expectedElements, new BitSet(byteArraySize * Byte.SIZE));
	}

	// inspired by https://github.com/magnuss/java-bloomfilter
	public SimpleBloomFilter(final double falsePositiveProbability, final int expectedElements) {
		final double c = Math.ceil(-(Math.log(falsePositiveProbability) / Math.log(2.0))) / Math.log(2.0);
		this.expectedElements = expectedElements;
		int tmpBitArraySize = (int) Math.ceil(c * expectedElements);
		this.byteArraySize = ((tmpBitArraySize + 7) / 8);
		this.bitArraySize = byteArraySize * Byte.SIZE;
		double hf = (bitArraySize / (double) expectedElements) * Math.log(2.0);
		// k may be larger as we may have increased the byte array size to match
		// a byte
		this.k = (int) Math.ceil(hf);
		this.bitSet = new BitSet(bitArraySize);
	}

	/**
	 * Constructs a SimpleBloomFilter out of existing data.
	 * 
	 * @param channelBuffer
	 *            The byte buffer with the data
	 */
	public SimpleBloomFilter(final ByteBuf channelBuffer) {
		this.byteArraySize = channelBuffer.readUnsignedShort() - (SIZE_HEADER_ELEMENTS + SIZE_HEADER_LENGTH);
		this.bitArraySize = byteArraySize * Byte.SIZE;
		int expectedElements = channelBuffer.readInt();
		this.expectedElements = expectedElements;
		double hf = (bitArraySize / (double) expectedElements) * Math.log(2.0);
		this.k = (int) Math.ceil(hf);
		if (byteArraySize > 0) {
			byte[] me = new byte[byteArraySize];
			channelBuffer.readBytes(me);
			this.bitSet = RPCUtils.fromByteArray(me);
		} else {
			this.bitSet = new BitSet();
		}
	}

	/**
	 * Constructs a SimpleBloomFilter out of existing data. You must specify the
	 * number of bits in the Bloom Filter, and also you should specify the
	 * number of items you expect to add. The latter is used to choose some
	 * optimal internal values to minimize the false-positive rate (which can be
	 * estimated with expectedFalsePositiveRate()).
	 * 
	 * @param byteArraySize
	 *            The number of bits in multiple of 8 in the bit array (often
	 *            called 'm' in the context of bloom filters).
	 * @param expectedElements
	 *            he typical number of items you expect to be added to the
	 *            SimpleBloomFilter (often called 'n').
	 * @param bitSet
	 *            The data that will be used in the backing BitSet
	 */
	public SimpleBloomFilter(final int byteArraySize, final int expectedElements, final BitSet bitSet) {
		this.byteArraySize = byteArraySize;
		this.bitArraySize = byteArraySize * Byte.SIZE;
		this.expectedElements = expectedElements;
		double hf = (bitArraySize / (double) expectedElements) * Math.log(2.0);
		this.k = (int) Math.ceil(hf);
		if (hf < 1.0) {
			LOG.warn(
			        "Bit size too small for storing all expected elements. For optimum result increase byteArraySize to {}",
			        expectedElements / Math.log(2.0));
		}
		this.bitSet = bitSet;
	}

	/**
	 * Calculates the approximate probability of the contains() method returning
	 * true for an object that had not previously been inserted into the bloom
	 * filter. This is known as the "false positive probability".
	 * 
	 * @return The estimated false positive rate
	 */
	public double expectedFalsePositiveProbability() {
		return Math.pow((1 - Math.exp(-k * (double) expectedElements / bitArraySize)), k);
	}

	/**
	 * Returns the expected elements that was provided by the user.
	 * 
	 * @return The expected elements that was provided by the user
	 */
	public int expectedElements() {
		return expectedElements;
	}

	/**
	 * @param o
	 *            Add element
	 * @return This method will always return false
	 * 
	 * @see Set#add(Object)
	 */
	@Override
	public boolean add(final E o) {
		Random r = new Random(o.hashCode());
		for (int x = 0; x < k; x++) {
			bitSet.set(r.nextInt(bitArraySize), true);
		}
		return false;
	}

	/**
	 * @param c
	 *            The elements to add
	 * @return This method will always return false
	 */
	@Override
	public boolean addAll(final Collection<? extends E> c) {
		for (E o : c) {
			add(o);
		}
		return false;
	}

	/**
	 * Clears this bloom filter.
	 */
	@Override
	public void clear() {
		for (int x = 0; x < bitSet.length(); x++) {
			bitSet.set(x, false);
		}
	}

	/**
	 * @param o
	 *            The object to compare
	 * @return False indicates that o was definitely not added to this Bloom
	 *         Filter, true indicates that it probably was. The probability can
	 *         be estimated using the expectedFalsePositiveProbability() method.
	 */
	@Override
	public boolean contains(final Object o) {
		if(isVoid()) {
			return false;
		}
		Random r = new Random(o.hashCode());
		for (int x = 0; x < k; x++) {
			if (!bitSet.get(r.nextInt(bitArraySize))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * @param c
	 *            The collection to check
	 * @return True, if all elements of the collection are in this bloom filter.
	 */
	@Override
	public boolean containsAll(final Collection<?> c) {
		for (Object o : c) {
			if (!contains(o)) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Not implemented.
	 * 
	 * @return nothing
	 */
	@Override
	public boolean isEmpty() {
		return bitSet.isEmpty();
	}

	/**
	 * Returns if the bloom filter is empty and never can be filled
	 * 
	 * @return nothing
	 */
	
	public boolean isVoid() {
		return byteArraySize == 0 && expectedElements == 0;
	}
	
	public boolean isFull() {
		return bitSet.cardinality() == bitSet.size();
	}

	/**
	 * Not implemented.
	 * 
	 * @return nothing
	 */
	@Override
	public Iterator<E> iterator() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Not implemented.
	 * 
	 * @param o
	 *            nothing
	 * @return nothing
	 */
	@Override
	public boolean remove(final Object o) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Not implemented.
	 * 
	 * @param c
	 *            nothing
	 * @return nothing
	 */
	@Override
	public boolean removeAll(final Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Not implemented.
	 * 
	 * @param c
	 *            nothing
	 * @return nothing
	 */
	@Override
	public boolean retainAll(final Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Not implemented.
	 *
	 * @return nothing
	 */
	@Override
	public int size() {
		return byteArraySize + 2 + 4;
	}

	/**
	 * Not implemented.
	 * 
	 * @return nothing
	 */
	@Override
	public Object[] toArray() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Not implemented.
	 * 
	 * @param a
	 *            nothing
	 * @param <T>
	 *            nothing
	 * @return nothing
	 */
	@Override
	public <T> T[] toArray(final T[] a) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns the bitset that backs the bloom filter.
	 * 
	 * @return bloom filter as a bitset
	 */
	public BitSet getBitSet() {
		return bitSet;
	}

	/**
	 * Converts data to a byte buffer. The first two bytes contain the size of
	 * this simple bloom filter. Thus, the bloom filter can only be of length
	 * 65536.
	 * 
	 * @param buf
	 *            The byte buffer where the bloom filter will be written.
	 */
	public void encode(final ByteBuf buf) {
		buf.writeShort(byteArraySize + SIZE_HEADER_ELEMENTS + SIZE_HEADER_LENGTH);
		buf.writeInt(expectedElements);
		byte[] tmp = RPCUtils.toByteArray(bitSet);
		int currentByteArraySize = tmp.length;
		buf.writeBytes(tmp);
		buf.writeZero(byteArraySize - currentByteArraySize);
	}

	/** Merges this bloom filter with the provided one using OR.
	 * @param toMerge
	 *
	 * @return A new bloom filter that contains both sets.
	 */
	public SimpleBloomFilter<E> merge(final SimpleBloomFilter<E> toMerge) {
		if (toMerge.bitArraySize != bitArraySize) {
			throw new RuntimeException("The two bloomfilters must have the same size.");
		}
		BitSet mergedBitSet = (BitSet) bitSet.clone();
		mergedBitSet.or(toMerge.bitSet);
		return new SimpleBloomFilter<E>(bitArraySize, expectedElements, mergedBitSet);
	}

	@Override
	public boolean equals(final Object obj) {
		if (!(obj instanceof SimpleBloomFilter)) {
			return false;
		}
		if (this == obj) {
			return true;
		}
		@SuppressWarnings("unchecked")
		SimpleBloomFilter<E> o = (SimpleBloomFilter<E>) obj;
		return o.k == k && o.bitArraySize == bitArraySize && expectedElements == o.expectedElements
		        && bitSet.equals(o.bitSet);
	}

	@Override
	public int hashCode() {
		final int magic = 31;
		int hash = 7;
		hash = magic * hash + bitSet.hashCode();
		hash = magic * hash + k;
		hash = magic * hash + expectedElements;
		hash = magic * hash + bitArraySize;
		return hash;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		int length = bitSet.length();
		for (int i = 0; i < length; i++) {
			sb.append(bitSet.get(i) ? "1" : "0");
		}
		return sb.toString();
	}

	public SimpleBloomFilter<E> setAll() {
		bitSet.set(0, bitSet.size(), true);
	    return this;
    }
}
