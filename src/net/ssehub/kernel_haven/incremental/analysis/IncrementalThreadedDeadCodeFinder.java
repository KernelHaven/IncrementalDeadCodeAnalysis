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
             * If option to only consider variability related items was selected,
             * instantiate relevancyChecker. Otherwise it remains set to null. The
             * relevancyChecker itself is used within the findDeadCodeBlocks() method and
             * checks every presence condition for its relevance. If the relevancyChecker is
             * null, all blocks will be considered for analysis.
             */
            if (findDcbForVariabilityRelatedPcsOnly) {
                relevancyChecker = new LinuxFormulaRelevancyChecker(vm, true);
            }

            /*
             * The SourceFileDifferenceDetector can be used to identify changes between the
             * current and previous model of a given SourceFile element. Therefore it is
             * able to reduce the analyzed part of the code model further so that only
             * elements with relevant changes are analyzed.
             */
            SourceFileDifferenceDetector detector = null;
            boolean reduceCodeModel = false;
            
            // the code model can be further optimized for partial analyses
            // in this case we instantiate the detector and enable the reduction of the code
            // model
            if (codeModelOptimization && !cm.isEmpty() && !buildModelChanged && !variabilityModelChanged) {
                try {
                    detector = new SourceFileDifferenceDetector(Consideration.ONLY_VARIABILITY_CHANGE, vm,
                            hybridCache.readPreviousVm());

                    reduceCodeModel = true;
                    LOGGER.logInfo(
                            "Analysis targets will be chosen considering the difference between current and previous code model.");
                } catch (IOException e) {
                    LOGGER.logException("Could not read previous variability model", e);
                }
            }

            LOGGER.logInfo("Dead Code Detection will be performed using " + numThreads + " Threads.");
            
            OrderPreservingParallelizer<SourceFile<?>, List<@NonNull DeadCodeBlock>> parallelizer =
                    new OrderPreservingParallelizer<>(this::findDeadCodeBlocks, (deadBlocks) -> {
                        for (DeadCodeBlock block : deadBlocks) {
                            addResult(block);
                        }
                    }, numThreads);

            int sourceFilesCovered = 0;
            // Feed parallelizer with input
            for (SourceFile<?> sourceFile : cm) {
                boolean analyzeSourceFile = true;
                if (reduceCodeModel) {
                    try {
                        /*
                         * Check if we have to analyze the file by detecting changes between previous
                         * and current version of codemodel
                         */
                        analyzeSourceFile =
                                detector.isDifferent(sourceFile, hybridCache.readPreviousCm(sourceFile.getPath()));
                    } catch (IOException e) {
                        LOGGER.logException("Could not read previous code model for path " + sourceFile.getPath(), e);
                    }
                }
                if (analyzeSourceFile) {
                    sourceFilesCovered++;
                    parallelizer.add(sourceFile);
                } else {
                    LOGGER.logDebug("Skipping " + sourceFile.getPath()
                            + " because it introduced no variability related changes.");
                }
            }

            parallelizer.end();
            parallelizer.join();
            LOGGER.logInfo("Analysis finished covering " + sourceFilesCovered + " source files.");

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
