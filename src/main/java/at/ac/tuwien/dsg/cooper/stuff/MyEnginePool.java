//package at.alexwais.cooper.stuff;
//
//import io.jenetics.Gene;
//import io.jenetics.engine.EvolutionStreamable;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Objects;
//
//abstract class MyEnginePool<
//	G extends Gene<?, G>,
//	C extends Comparable<? super C>
//>
//	implements EvolutionStreamable<G, C>
//{
//
//	final List<? extends EvolutionStreamable<G, C>> _engines;
//
//	MyEnginePool(final List<? extends EvolutionStreamable<G, C>> engines) {
//		engines.forEach(Objects::requireNonNull);
//		_engines = new ArrayList<>(engines);
//	}
//
//}