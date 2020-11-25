/*
 * Java Genetic Algorithm Library (jenetics-6.0.1).
 * Copyright (c) 2007-2020 Franz Wilhelmstötter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author:
 *    Franz Wilhelmstötter (franz.wilhelmstoetter@gmail.com)
 */
package at.alexwais.cooper.genetic;

import io.jenetics.NumericChromosome;
import io.jenetics.util.ISeq;
import java.util.stream.IntStream;
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;

/**
 * Numeric chromosome implementation which holds 32 bit integer numbers.
 *
 * @see IntegerGene
 *
 * @implNote
 * This class is immutable and thread-safe.
 *
 * @author <a href="mailto:franz.wilhelmstoetter@gmail.com">Franz  Wilhelmstötter</a>
 * @since 2.0
 * @version 5.2
 */
public class DistributedIntegerChromosome implements NumericChromosome<Integer, DistributedIntegerGene> {
	private static final long serialVersionUID = 3L;

	private ISeq<DistributedIntegerGene> _seq;
	private EnumeratedIntegerDistribution _distribution;
	private final int _length;

	/**
	 * Create a new chromosome from the given {@code genes} and the allowed
	 * length range of the chromosome.
	 *
	 * @since 4.0
	 *
	 * @param genes the genes that form the chromosome.
	 * @param lengthRange the allowed length range of the chromosome
	 * @throws NullPointerException if one of the arguments is {@code null}.
	 * @throws IllegalArgumentException if the length of the gene sequence is
	 *         empty, doesn't match with the allowed length range, the minimum
	 *         or maximum of the range is smaller or equal zero or the given
	 *         range size is zero.
	 */
	private DistributedIntegerChromosome(
		final ISeq<DistributedIntegerGene> genes,
		final EnumeratedIntegerDistribution distribution,
		final int length
	) {
		this._seq = genes;
		this._distribution = distribution;
		this._length = length;
	}

	@Override
	public DistributedIntegerChromosome newInstance(final ISeq<DistributedIntegerGene> genes) {
		return new DistributedIntegerChromosome(genes, _distribution, _length);
	}

	@Override
	public DistributedIntegerChromosome newInstance() {
		return of(_distribution, _length);
	}

	/**
	 * Returns a sequential stream of the alleles with this chromosome as its
	 * source.
	 *
	 * @since 4.3
	 *
	 * @return a sequential stream of alleles
	 */
	public IntStream intStream() {
		return IntStream.range(0, length()).map(this::intValue);
	}

	/**
	 * Returns an int array containing all of the elements in this chromosome
	 * in proper sequence.  If the chromosome fits in the specified array, it is
	 * returned therein. Otherwise, a new array is allocated with the length of
	 * this chromosome.
	 *
	 * @since 3.0
	 *
	 * @param array the array into which the elements of this chromosomes are to
	 *        be stored, if it is big enough; otherwise, a new array is
	 *        allocated for this purpose.
	 * @return an array containing the elements of this chromosome
	 * @throws NullPointerException if the given {@code array} is {@code null}
	 */
//	public int[] toArray(final int[] array) {
//		final int[] a = array.length >= length() ? array : new int[length()];
//		for (int i = length(); --i >= 0;) {
//			a[i] = intValue(i);
//		}
//
//		return a;
//	}

	/**
	 * Returns an int array containing all of the elements in this chromosome
	 * in proper sequence.
	 *
	 * @since 3.0
	 *
	 * @return an array containing the elements of this chromosome
	 */
//	public int[] toArray() {
//		return toArray(new int[length()]);
//	}


	/* *************************************************************************
	 * Static factory methods.
	 * ************************************************************************/

	/**
	 * Create a new {@code IntegerChromosome} with the given genes.
	 *
	 * @param genes the genes of the chromosome.
	 * @return a new chromosome with the given genes.
	 * @throws IllegalArgumentException if the length of the genes array is
	 *         empty or the given {@code genes} doesn't have the same range.
	 */
	public static DistributedIntegerChromosome of(final DistributedIntegerGene[] genes, final EnumeratedIntegerDistribution distribution) {
		return new DistributedIntegerChromosome(ISeq.of(genes), distribution, genes.length);
	}

	/**
	 * Create a new {@code IntegerChromosome} with the given genes.
	 *
	 * @since 4.3
	 *
	 * @param genes the genes of the chromosome.
	 * @return a new chromosome with the given genes.
	 * @throws NullPointerException if the given {@code genes} are {@code null}
	 * @throws IllegalArgumentException if the of the genes iterable is empty or
	 *         the given {@code genes} doesn't have the same range.
	 */
//	public static DistributedIntegerChromosome of(final Iterable<IntegerGene> genes) {
//		final ISeq<IntegerGene> values = ISeq.of(genes);
//		checkGeneRange(values.stream().map(IntegerGene::range));
//		return new DistributedIntegerChromosome(values, IntRange.of(values.length()));
//	}

	/**
	 * Create a new random chromosome.
	 *
	 * @since 4.0
	 *
	 * @param min the min value of the {@link IntegerGene}s (inclusively).
	 * @param max the max value of the {@link IntegerGene}s (inclusively).
	 * @param lengthRange the allowed length range of the chromosome.
	 * @return a new {@code IntegerChromosome} with the given parameter
	 * @throws IllegalArgumentException if the length of the gene sequence is
	 *         empty, doesn't match with the allowed length range, the minimum
	 *         or maximum of the range is smaller or equal zero or the given
	 *         range size is zero.
	 * @throws NullPointerException if the given {@code lengthRange} is
	 *         {@code null}
	 */
	public static DistributedIntegerChromosome of(
		final EnumeratedIntegerDistribution distribution,
		final int length
	) {
		final ISeq<DistributedIntegerGene> values = DistributedIntegerGene.seq(distribution, length);
		return new DistributedIntegerChromosome(values, distribution, length);
	}

	public static DistributedIntegerChromosome of(
			final int[] allele,
			final EnumeratedIntegerDistribution distribution,
			final int length
	) {
		for (int v : allele) {
			var gene = DistributedIntegerGene.of(v, distribution);
		}
		final ISeq<DistributedIntegerGene> values = DistributedIntegerGene.seq(distribution, length);
		return new DistributedIntegerChromosome(values, distribution, length);
	}


	@Override
	public DistributedIntegerGene get(int index) {
		return _seq.get(index);
	}

	@Override
	public int length() {
		return _seq.length();
	}

	/**
	 * Create a new random {@code IntegerChromosome}.
	 *
	 * @param min the min value of the {@link IntegerGene}s (inclusively).
	 * @param max the max value of the {@link IntegerGene}s (inclusively).
	 * @param length the length of the chromosome.
	 * @return a new random {@code IntegerChromosome}
	 * @throws IllegalArgumentException if the length is smaller than one
	 */
//	public static DistributedIntegerChromosome of(
//		final int min,
//		final int max,
//		final int length
//	) {
//		return of(min, max, IntRange.of(length));
//	}

	/**
	 * Create a new random chromosome.
	 *
	 * @since 4.0
	 *
	 * @param range the integer range of the chromosome.
	 * @param lengthRange the allowed length range of the chromosome.
	 * @return a new {@code IntegerChromosome} with the given parameter
	 * @throws IllegalArgumentException if the length of the gene sequence is
	 *         empty, doesn't match with the allowed length range, the minimum
	 *         or maximum of the range is smaller or equal zero or the given
	 *         range size is zero.
	 * @throws NullPointerException if the given {@code lengthRange} is
	 *         {@code null}
	 */
//	public static DistributedIntegerChromosome of(
//		final IntRange range,
//		final IntRange lengthRange
//	) {
//		return of(range.min(), range.max(), lengthRange);
//	}

	/**
	 * Create a new random {@code IntegerChromosome}.
	 *
	 * @since 3.2
	 *
	 * @param range the integer range of the chromosome.
	 * @param length the length of the chromosome.
	 * @return a new random {@code IntegerChromosome}
	 * @throws NullPointerException if the given {@code range} is {@code null}
	 * @throws IllegalArgumentException if the length is smaller than one
	 */
//	public static DistributedIntegerChromosome of(final IntRange range, final int length) {
//		return of(range.min(), range.max(), length);
//	}

	/**
	 * Create a new random {@code IntegerChromosome} of length one.
	 *
	 * @param min the minimal value of this chromosome (inclusively).
	 * @param max the maximal value of this chromosome (inclusively).
	 * @return a new random {@code IntegerChromosome} of length one
	 */
//	public static DistributedIntegerChromosome of(final int min, final int max) {
//		return of(min, max, 1);
//	}

	/**
	 * Create a new random {@code IntegerChromosome} of length one.
	 *
	 * @since 3.2
	 *
	 * @param range the integer range of the chromosome.
	 * @return a new random {@code IntegerChromosome} of length one
	 * @throws NullPointerException if the given {@code range} is {@code null}
	 */
//	public static DistributedIntegerChromosome of(final IntRange range) {
//		return of(range.min(), range.max(), 1);
//	}



	/* *************************************************************************
	 *  Java object serialization
	 * ************************************************************************/

//	private Object writeReplace() {
//		return new Serial(Serial.INTEGER_CHROMOSOME, this);
//	}
//
//	private void readObject(final ObjectInputStream stream)
//		throws InvalidObjectException
//	{
//		throw new InvalidObjectException("Serialization proxy required.");
//	}
//
//	void write(final DataOutput out) throws IOException {
//		writeInt(length(), out);
//		writeInt(lengthRange().min(), out);
//		writeInt(lengthRange().max(), out);
//		writeInt(_min, out);
//		writeInt(_max, out);
//
//		for (int i = 0, n = length(); i < n; ++i) {
//			writeInt(intValue(i), out);
//		}
//	}
//
//	static DistributedIntegerChromosome read(final DataInput in) throws IOException {
//		final var length = readInt(in);
//		final var lengthRange = IntRange.of(readInt(in), readInt(in));
//		final var min = readInt(in);
//		final var max = readInt(in);
//
//		final MSeq<IntegerGene> values = MSeq.ofLength(length);
//		for (int i = 0; i < length; ++i) {
//			values.set(i, IntegerGene.of(readInt(in), min, max));
//		}
//
//		return new DistributedIntegerChromosome(values.toISeq(), lengthRange);
//	}

}
