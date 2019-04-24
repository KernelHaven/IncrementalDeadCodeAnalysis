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
            LOGGER.logError("Couldn't get models: ", "got variability model: " + (vm != null),
                    "got build model: " + (vm != null), "got code model: " + (cm != null));
            return;
        }

        try {
            vmCnf = new VmToCnfConverter().convertVmToCnf(notNull(vm));

            /*
             * if option to only consider variability related items was selected,
             * instantiate relevancyChecker. Otherwise it remains set to null. The
             * relevancyChecker itself is used within the findDeadCodeBlocks() method and
             * checks every presence condition for its relevance. If the relevancyChecker is
             * null, all blocks will be considered for analysis.
             */
            if (onlyConsiderVariabilityRelatedVariables) {
                relevancyChecker = new LinuxFormulaRelevancyChecker(vm, true);
            }

            /*
             * if the current code model should be compared with the previous one to detect
             * differences a detector instance is created that is used to skip all code
             * files where variability information was not changed.
             */
            SourceFileDifferenceDetector detector = null;
            boolean reduceCodeModel = false;
            if (codeModelOptimization) {
                try {
                    detector = new SourceFileDifferenceDetector(Consideration.ONLY_VARIABILITY_CHANGE, vm,
                            hybridCache.readPreviousVm());
                    // we can only skip code files if the build and variability model did not change
                    // otherwise we need to consider all files
                    reduceCodeModel = !buildModelChanged && !variabilityModelChanged;
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

            // Feed parallelizer with input
            for (SourceFile<?> sourceFile : cm) {
                boolean analyzeSourceFile = true;
                if (reduceCodeModel) {
                    try {
                        analyzeSourceFile =
                                detector.isDifferent(sourceFile, hybridCache.readPreviousCm(sourceFile.getPath()));
                    } catch (IOException e) {
                        LOGGER.logException("Could not read previous code model for path " + sourceFile.getPath(), e);
                    }
                }
                if (analyzeSourceFile) {
                    parallelizer.add(sourceFile);
                } else {
                    LOGGER.logDebug("Skipping " + sourceFile.getPath()
                            + " because it introduced no variability related changes.");
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
