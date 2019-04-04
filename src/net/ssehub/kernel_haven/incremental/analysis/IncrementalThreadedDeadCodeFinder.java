package net.ssehub.kernel_haven.incremental.analysis;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.util.List;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.cnf.VmToCnfConverter;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.incremental.storage.HybridCache;
import net.ssehub.kernel_haven.undead_analyzer.FormulaRelevancyChecker;
import net.ssehub.kernel_haven.util.FormatException;
import net.ssehub.kernel_haven.util.OrderPreservingParallelizer;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

public class IncrementalThreadedDeadCodeFinder extends IncrementalDeadCodeFinder {

	private int numThreads;

	/**
	 * Creates a dead code analysis.
	 * 
	 * @param config      The user configuration; not used.
	 * @param vmComponent The component to provide the variability model.
	 * @param bmComponent The component to provide the build model.
	 * @param cmComponent The component to provide the code model.
	 * @throws SetUpException
	 */
	public IncrementalThreadedDeadCodeFinder(@NonNull Configuration config,
			AnalysisComponent<HybridCache> postExtraction) throws SetUpException {

		super(config, postExtraction);

		IncrementalDeadCodeAnalysisSettings.registerAllSettings(config);
		numThreads = config.getValue(IncrementalDeadCodeAnalysisSettings.NUMBER_OF_THREADS);

	}

	@Override
	protected void execute() {
		loadModelsFromHybridCache();

		if (vm == null || bm == null || cm == null) {
			LOGGER.logError("Couldn't get models");
			return;
		}

		try {
			vmCnf = new VmToCnfConverter().convertVmToCnf(notNull(vm));

			// Only consider formulas with relation to variability model
			if (considerVmVarsOnly) {
				relevancyChecker = new FormulaRelevancyChecker(vm, considerVmVarsOnly);
			}

			OrderPreservingParallelizer<SourceFile<?>, List<@NonNull DeadCodeBlock>> parallelizer = new OrderPreservingParallelizer<>(
					this::findDeadCodeBlocks, (deadBlocks) -> {
						for (DeadCodeBlock block : deadBlocks) {
							addResult(block);
						}
					}, numThreads);

			for (SourceFile<?> sourceFile : cm) {
				parallelizer.add(sourceFile);
			}

			parallelizer.end();
			parallelizer.join();

		} catch (FormatException e) {
			LOGGER.logException("Invalid variability model", e);
		}

	}

	@Override
	public @NonNull String getResultName() {
		return "Dead Code Blocks";
	}

}
