///*
// * Java Genetic Algorithm Library (jenetics-6.0.1).
// * Copyright (c) 2007-2020 Franz Wilhelmstötter
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// *
// * Author:
// *    Franz Wilhelmstötter (franz.wilhelmstoetter@gmail.com)
// */
//package at.alexwais.cooper.stuff;
//
//import io.jenetics.Genotype;
//import io.jenetics.Phenotype;
//import io.jenetics.util.ISeq;
//import java.util.*;
//import java.util.concurrent.atomic.AtomicReference;
//import java.util.function.Function;
//import java.util.function.Supplier;
//import java.util.stream.BaseStream;
//import java.util.stream.Collectors;
//
//import io.jenetics.Gene;
//import io.jenetics.engine.EvolutionInit;
//import io.jenetics.engine.EvolutionResult;
//import io.jenetics.engine.EvolutionStart;
//import io.jenetics.engine.EvolutionStream;
//import io.jenetics.engine.EvolutionStreamable;
//import io.jenetics.internal.engine.EvolutionStreamImpl;
//
//import io.jenetics.ext.internal.ConcatSpliterator;
//import org.apache.commons.lang3.tuple.Pair;
//
///**
// * Modified from io.jenetics.ext.engine.ConcatEngine
// * @param <G>
// * @param <C>
// */
//public final class MyConcatEngine<
//	G extends Gene<?, G>,
//	C extends Comparable<? super C>
//>
//	extends MyEnginePool<G, C>
//{
//
//	private Map<? extends EvolutionStreamable<G, C>, Function<Genotype<G>, Genotype<G>>> enginesWithMappings;
//
//	/**
//	 * Create a new concatenating evolution engine with the given list of engines.
//	 *
//	 * @param engines the engines which are concatenated to <em>one</em> engine
//	 * @throws NullPointerException if the {@code engines} or one of it's
//	 *         elements is {@code null}
//	 */
//	public MyConcatEngine(final List<Pair<? extends EvolutionStreamable<G, C>, Function<Genotype<G>, Genotype<G>>>> enginesWithMappings) {
//		super(enginesWithMappings.stream().map(Pair::getLeft).collect(Collectors.toList()));
//		this.enginesWithMappings = enginesWithMappings.stream().collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
//	}
//
//	@Override
//	public EvolutionStream<G, C>
//	stream(final Supplier<EvolutionStart<G, C>> start) {
//		final AtomicReference<EvolutionStart<G, C>> other =
//			new AtomicReference<>(null);
//
//		return new EvolutionStreamImpl<>(
//			new ConcatSpliterator<>(
//				_engines.stream()
//					.map(engine -> engine
//						.stream(() -> start(start, other))
//						.peek(result -> other.set(mapEvolutionStart(result.toEvolutionStart(), enginesWithMappings.get(engine)))))
//					.map(BaseStream::spliterator)
//					.collect(Collectors.toList())
//			),
//			false
//		);
//	}
//
//	private EvolutionStart<G, C> mapEvolutionStart(EvolutionStart<G, C> evo, Function<Genotype<G>, Genotype<G>> mapping) {
//		ISeq<Phenotype<G, C>> mappedPopulation = evo.population().stream()
//				.map(phenotype -> (Phenotype<G, C>) Phenotype.of(mapping.apply(phenotype.genotype()), phenotype.generation()))
//				.collect(ISeq.toISeq());
//
//		return EvolutionStart.of(mappedPopulation, evo.generation());
//	}
//
//
//	private EvolutionStart<G, C> start(
//		final Supplier<EvolutionStart<G, C>> first,
//		final AtomicReference<EvolutionStart<G, C>> other
//	) {
//		return other.get() != null ? other.get() : first.get();
//	}
//
//	@Override
//	public EvolutionStream<G, C> stream(final EvolutionInit<G> init) {
//		final AtomicReference<EvolutionStart<G, C>> other =
//			new AtomicReference<>(null);
//
//		return new EvolutionStreamImpl<>(
//			new ConcatSpliterator<>(spliterators(init, other)),
//			false
//		);
//	}
//
//	private Collection<Spliterator<EvolutionResult<G, C>>> spliterators(
//		final EvolutionInit<G> init,
//		final AtomicReference<EvolutionStart<G, C>> other
//	) {
//		final Collection<Spliterator<EvolutionResult<G, C>>> result;
//		if (_engines.isEmpty()) {
//			result = Collections.emptyList();
//		} else if (_engines.size() == 1) {
//			result = List.of(
//				_engines.get(0)
//					.stream(init)
//					.peek(er -> other.set(er.toEvolutionStart()))
//					.spliterator()
//			);
//		} else {
//			final List<Spliterator<EvolutionResult<G, C>>> concat =
//				new ArrayList<>();
//
//			concat.add(
//				_engines.get(0)
//					.stream(init)
//					.peek(er -> other.set(er.toEvolutionStart()))
//					.spliterator()
//			);
//			concat.addAll(
//				_engines.subList(1, _engines.size()).stream()
//					.map(engine -> engine
//						.stream(other::get)
//						.peek(er -> other.set(er.toEvolutionStart())))
//					.map(BaseStream::spliterator)
//					.collect(Collectors.toList())
//			);
//
//			result = concat;
//		}
//
//		return result;
//	}
//
////	/**
////	 * Create a new concatenating evolution engine with the given array of
////	 * engines.
////	 *
////	 * @param engines the engines which are concatenated to <em>one</em> engine
////	 * @param <G> the gene type
////	 * @param <C> the fitness type
////	 * @return a new concatenating evolution engine
////	 * @throws NullPointerException if the {@code engines} or one of it's
////	 *         elements is {@code null}
////	 */
////	@SafeVarargs
////	public static <G extends Gene<?, G>, C extends Comparable<? super C>>
////	MyConcatEngine<G, C> of(final Map<EvolutionStreamable<G, C>... engines) {
////		return new MyConcatEngine<>(List.of(engines), genotypeMapping);
////	}
//
//
//}
