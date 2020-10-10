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

import static io.jenetics.internal.util.Hashes.hash;

import io.jenetics.IntegerGene;
import io.jenetics.NumericGene;
import io.jenetics.util.ISeq;
import io.jenetics.util.MSeq;
import io.jenetics.util.Mean;
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;

/**
 * Adapted from IntegerGene
 */
public final class DistributedIntegerGene
	implements
		NumericGene<Integer, DistributedIntegerGene>,
		Mean<DistributedIntegerGene>,
		Comparable<DistributedIntegerGene>
{

	private static final long serialVersionUID = 2L;

	private final int _allele;
	private final EnumeratedIntegerDistribution _distribution;

	/**
	 * Create a new random {@code IntegerGene} with the given value and the
	 * given range. If the {@code value} isn't within the interval [min, max],
	 * no exception is thrown. In this case the method
	 * {@link IntegerGene#isValid()} returns {@code false}.
	 *
	 * @param allele the value of the gene.
	 * @param min the minimal valid value of this gene (inclusively).
	 * @param max the maximal valid value of this gene (inclusively).
	 */
	private DistributedIntegerGene(final int allele, final EnumeratedIntegerDistribution distribution) {
		_allele = allele;
		_distribution = distribution;
	}

	@Override
	public Integer allele() {
		return _allele;
	}

	@Override
	public Integer min() {
		return _distribution.getSupportLowerBound();
	}

	@Override
	public Integer max() {
		return _distribution.getSupportUpperBound();
	}

	/**
	 * Return the range of {@code this} gene.
	 *
	 * @since 4.4
	 *
	 * @return the range of {@code this} gene
	 */
//	public IntRange range() {
//		return IntRange.of(_min, _max);
//	}

	@Override
	public byte byteValue() {
		return (byte) _allele;
	}

	@Override
	public short shortValue() {
		return (short) _allele;
	}

	@Override
	public int intValue() {
		return _allele;
	}

	@Override
	public long longValue() {
		return _allele;
	}

	@Override
	public float floatValue() {
		return (float) _allele;
	}

	@Override
	public double doubleValue() {
		return _allele;
	}

	@Override
	public boolean isValid() {
		return _allele >= _distribution.getSupportLowerBound() && _allele <= _distribution.getSupportUpperBound();
	}

	@Override
	public int compareTo(final DistributedIntegerGene other) {
		return Integer.compare(_allele, other._allele);
	}

	@Override
	public DistributedIntegerGene mean(final DistributedIntegerGene that) {
		return DistributedIntegerGene.of(_allele + (that._allele - _allele)/2, _distribution);
	}

	/**
	 * Create a new gene from the given {@code value} and the gene context.
	 *
	 * @since 5.0
	 * @param allele the value of the new gene.
	 * @return a new gene with the given value.
	 */
//	public DistributedIntegerGene newInstance(final int allele) {
//		return DistributedIntegerGene.of(allele, _min, _max);
//	}

	@Override
	public DistributedIntegerGene newInstance(final Integer allele) {
		return DistributedIntegerGene.of(allele, _distribution);
	}

	@Override
	public DistributedIntegerGene newInstance(final Number allele) {
		final int value = (int)Math.round(allele.doubleValue());
		return DistributedIntegerGene.of(value, _distribution);
	}

	@Override
	public DistributedIntegerGene newInstance() {
		return DistributedIntegerGene.of(_distribution.sample(), _distribution);
	}

	@Override
	public int hashCode() {
		return hash(_allele, hash(_distribution));
	}

	@Override
	public boolean equals(final Object obj) {
		return obj == this ||
			obj instanceof DistributedIntegerGene &&
			((DistributedIntegerGene)obj)._allele == _allele &&
			((DistributedIntegerGene)obj)._distribution.equals(_distribution);
	}

	@Override
	public String toString() {
		return String.format("[%s]", _allele);
	}

	/* *************************************************************************
	 * Static factory methods.
	 * ************************************************************************/

	/**
	 * Create a new random {@code IntegerGene} with the given value and the
	 * given range. If the {@code value} isn't within the interval [min, max],
	 * no exception is thrown. In this case the method
	 * {@link IntegerGene#isValid()} returns {@code false}.
	 *
	 * @param allele the value of the gene.
	 * @param min the minimal valid value of this gene (inclusively).
	 * @param max the maximal valid value of this gene (inclusively).
	 * @return a new {@code IntegerGene} with the given {@code value}
	 */
	public static DistributedIntegerGene of(final int allele, final EnumeratedIntegerDistribution distribution) {
		return new DistributedIntegerGene(allele, distribution);
	}

	/**
	 * Create a new random {@code IntegerGene} with the given value and the
	 * given range. If the {@code value} isn't within the interval [min, max],
	 * no exception is thrown. In this case the method
	 * {@link IntegerGene#isValid()} returns {@code false}.
	 *
	 * @since 3.2
	 *
	 * @param allele the value of the gene.
	 * @param range the integer range to use
	 * @return a new {@code IntegerGene} with the give {@code value}
	 * @throws NullPointerException if the given {@code range} is {@code null}.
	 */
//	public static DistributedIntegerGene of(final int allele, final IntRange range) {
//		return DistributedIntegerGene.of(allele, range.min(), range.max());
//	}

	/**
	 * Create a new random {@code IntegerGene}. It is guaranteed that the value of
	 * the {@code IntegerGene} lies in the interval [min, max].
	 *
	 * @param min the minimal valid value of this gene (inclusively).
	 * @param max the maximal valid value of this gene (inclusively).
	 * @return a new random {@code IntegerGene}
	 */
//	public static DistributedIntegerGene of(final int min, final int max) {
//		return of(nextInt(random(), min, max), min, max);
//	}

	/**
	 * Create a new random {@code IntegerGene}. It is guaranteed that the value of
	 * the {@code IntegerGene} lies in the interval [min, max].
	 *
	 * @since 3.2
	 *
	 * @param range the integer range to use
	 * @return a new random {@code IntegerGene}
	 * @throws NullPointerException if the given {@code range} is {@code null}.
	 */
//	public static DistributedIntegerGene of(final IntRange range) {
//		return of(nextInt(random(), range.min(), range.max()), range);
//	}

	static ISeq<DistributedIntegerGene> seq(
		final EnumeratedIntegerDistribution distribution,
		final int length
	) {
		return MSeq.<DistributedIntegerGene>ofLength(length)
			.fill(() -> new DistributedIntegerGene(distribution.sample(), distribution))
			.toISeq();
	}

}
