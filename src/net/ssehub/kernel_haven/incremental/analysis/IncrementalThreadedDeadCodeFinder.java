package net.ssehub.kernel_haven.incremental.analysis;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.io.IOException;
import java.util.List;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.cnf.VmToCnfConverter;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.incremental.storage.HybridCache;
import net.ssehub.kernel_haven.incremental.util.LinuxFormulaRelevancyChecker;
import net.ssehub.kernel_haven.incremental.util.SourceFileDifferenceDetector;
import net.ssehub.kernel_haven.incremental.util.SourceFileDifferenceDetector.Consideration;
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

            SourceFileDifferenceDetector detector = null;

            // If only variability related variables should be considered, the
            // set of considered SourceFile elements is reduced to the source files
            // that were changed in regards to their variability information
            if (onlyVariabilyRelatedVariables) {
                relevancyChecker = new LinuxFormulaRelevancyChecker(vm, true);
                try {
                    detector = new SourceFileDifferenceDetector(Consideration.ONLY_VARIABILITY_CHANGE, vm,
                            hybridCache.readPreviousVm());
                } catch (IOException e) {
                    LOGGER.logException("Could not read previous variability model", e);
                }

            }

            OrderPreservingParallelizer<SourceFile<?>, List<@NonNull DeadCodeBlock>> parallelizer =
                    new OrderPreservingParallelizer<>(this::findDeadCodeBlocks, (deadBlocks) -> {
                        for (DeadCodeBlock block : deadBlocks) {
                            addResult(block);
                        }
                    }, numThreads);

            for (SourceFile<?> sourceFile : cm) {
                boolean analyzeSourceFile = true;
                try {
                    analyzeSourceFile = detector == null
                            || detector.isDifferent(sourceFile, hybridCache.readCm(sourceFile.getPath()));
                } catch (IOException e) {
                    LOGGER.logException("Could not read previous code model for path " + sourceFile.getPath(), e);
                }

                if (analyzeSourceFile) {
                    parallelizer.add(sourceFile);
                }

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
