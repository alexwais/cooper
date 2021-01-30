package at.ac.tuwien.dsg.cooper.genetic;

import at.ac.tuwien.dsg.cooper.domain.Service;
import at.ac.tuwien.dsg.cooper.scheduler.Model;
import at.ac.tuwien.dsg.cooper.scheduler.dto.SystemMeasures;
import io.jenetics.NumericChromosome;
import io.jenetics.util.ISeq;
import java.util.Arrays;
import java.util.stream.Collectors;
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

	@Override
	public DistributedIntegerGene get(int index) {
		return _seq.get(index);
	}

	@Override
	public int length() {
		return _seq.length();
	}


	/* *************************************************************************
	 * Static factory methods.
	 * ************************************************************************/

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

	public static DistributedIntegerChromosome of(Service service, int length, Model model, SystemMeasures systemMeasures) {
		var containerDistribution = computeContainerDistribution(service, model, systemMeasures);

		final ISeq<DistributedIntegerGene> values = DistributedIntegerGene.seq(containerDistribution, length);
		return new DistributedIntegerChromosome(values, containerDistribution, length);
	}

	public static DistributedIntegerChromosome of(Service service, int[] geneValues, Model model, SystemMeasures systemMeasures) {
		var containerDistribution = computeContainerDistribution(service, model, systemMeasures);

		var genes = Arrays.stream(geneValues)
				.mapToObj(v -> DistributedIntegerGene.of(v, containerDistribution))
				.toArray(DistributedIntegerGene[]::new);
		return new DistributedIntegerChromosome(ISeq.of(genes), containerDistribution, genes.length);
	}

	private static EnumeratedIntegerDistribution computeContainerDistribution(Service service, Model model, SystemMeasures systemMeasures) {
		var containersOfService = service.getContainerTypes();
		var containerCount = containersOfService.size();
		var serviceShare = shareOfServiceLoadToOverallCapacity(service, model, systemMeasures);
		var containerShare = serviceShare / containerCount;

		// 1-based container index to probability
		var probabilityMap = containersOfService.stream()
				.collect(Collectors.toMap(c -> containersOfService.indexOf(c) + 1, c -> containerShare));

		var totalServiceProbability = probabilityMap.values().stream()
				.reduce(0d, Double::sum);
		var noAllocationProbability = 1 - totalServiceProbability;
		probabilityMap.put(0, noAllocationProbability);

		var indexArray = probabilityMap.keySet().stream().mapToInt(i -> i).toArray();
		var probabilityArray = probabilityMap.values().stream().mapToDouble(d -> d).toArray();
		var containerDistribution = new EnumeratedIntegerDistribution(indexArray, probabilityArray);
		return containerDistribution;
	}

	private static double shareOfServiceLoadToOverallCapacity(Service service, Model model, SystemMeasures systemMeasures) {
		var overallServiceLoad = systemMeasures.getTotalServiceLoad().get(service.getName());
		var containerTypeCount = service.getContainerTypes().size();
		var overallCapacityForService = service.getContainerTypes().stream()
				// one container type per service can be allocated once on a VM
				.map(t -> t.getRpmCapacity() * model.getVms().size() / containerTypeCount)
				.reduce(0L, Long::sum);
		var loadToCapacityRatio = (double) overallServiceLoad / (double) overallCapacityForService;

		return loadToCapacityRatio;
	}

}
