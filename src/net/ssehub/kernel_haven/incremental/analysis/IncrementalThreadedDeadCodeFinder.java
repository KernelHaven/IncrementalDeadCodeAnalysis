package net.ssehub.kernel_haven.incremental.analysis;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.util.List;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.cnf.VmToCnfConverter;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.incremental.storage.HybridCache;
import net.ssehub.kernel_haven.util.FormatException;
import net.ssehub.kernel_haven.util.OrderPreservingParallelizer;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

/**
 * Incremental and multithreaded version of
 * {@link net.ssehub.kernel_haven.undead_analyzer.DeadCodeFinder}.
 * 
 * @author Moritz
 */
public class IncrementalThreadedDeadCodeFinder extends IncrementalDeadCodeFinder {

    /** The num threads. */
    private int numThreads;

    /**
     * Creates a dead code analysis.
     *
     * @param config         The user configuration; not used.
     * @param postExtraction the post extraction
     * @throws SetUpException the set up exception
     */
    public IncrementalThreadedDeadCodeFinder(@NonNull Configuration config,
            AnalysisComponent<HybridCache> postExtraction) throws SetUpException {

        super(config, postExtraction);

        IncrementalDeadCodeAnalysisSettings.registerAllSettings(config);
        numThreads = config.getValue(IncrementalDeadCodeAnalysisSettings.NUMBER_OF_THREADS);

    }

    /*
     * (non-Javadoc)
     * 
     * @see net.ssehub.kernel_haven.incremental.analysis.IncrementalDeadCodeFinder#
     * execute()
     */
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
            if (onlyVariabilyRelatedVariables) {
                relevancyChecker = new LinuxFormulaRelevancyChecker(vm, true);
            }

            OrderPreservingParallelizer<SourceFile<?>, List<@NonNull DeadCodeBlock>> parallelizer =
                    new OrderPreservingParallelizer<>(this::findDeadCodeBlocks, (deadBlocks) -> {
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

    /*
     * (non-Javadoc)
     * 
     * @see net.ssehub.kernel_haven.incremental.analysis.IncrementalDeadCodeFinder#
     * getResultName()
     */
    @Override
    public @NonNull String getResultName() {
        return "Dead Code Blocks";
    }

}
