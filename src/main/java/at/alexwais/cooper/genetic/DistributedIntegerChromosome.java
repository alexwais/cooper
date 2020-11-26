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
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;

/**
 * Based on IntegerChromosome.
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


	/* *************************************************************************
	 * Static factory methods.
	 * ************************************************************************/

	/**
	 * Create a new {@code DistributedIntegerChromosome} with the given genes.
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
	public static DistributedIntegerChromosome of(final EnumeratedIntegerDistribution distribution, final int length) {
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

}
