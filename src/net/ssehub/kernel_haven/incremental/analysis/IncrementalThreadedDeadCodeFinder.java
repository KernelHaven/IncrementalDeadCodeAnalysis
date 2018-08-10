package net.ssehub.kernel_haven.incremental.analysis;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.cnf.VmToCnfConverter;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.Setting;
import net.ssehub.kernel_haven.incremental.storage.HybridCache;
import net.ssehub.kernel_haven.incremental.storage.HybridCache.ChangeFlag;
import net.ssehub.kernel_haven.undead_analyzer.FormulaRelevancyChecker;
import net.ssehub.kernel_haven.undead_analyzer.ThreadedDeadCodeFinder;
import net.ssehub.kernel_haven.util.FormatException;
import net.ssehub.kernel_haven.util.OrderPreservingParallelizer;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

public class IncrementalThreadedDeadCodeFinder
    extends IncrementalDeadCodeFinder {

    public static final @NonNull Setting<@NonNull Integer> NUMBER_OF_OF_THREADS =
        new Setting<>("analysis.undead.threads", Setting.Type.INTEGER, true,
            "2", "Number of threads to use for the "
                + ThreadedDeadCodeFinder.class.getName() + ". Must be >= 1.");

    private int numThreads;

    /**
     * Creates a dead code analysis.
     * 
     * @param config
     *            The user configuration; not used.
     * @param vmComponent
     *            The component to provide the variability model.
     * @param bmComponent
     *            The component to provide the build model.
     * @param cmComponent
     *            The component to provide the code model.
     * @throws SetUpException
     */
    public IncrementalThreadedDeadCodeFinder(@NonNull Configuration config,
        HybridCache hybridCache) throws SetUpException {

        super(config, hybridCache);

        config.registerSetting(NUMBER_OF_OF_THREADS);
        numThreads = config.getValue(NUMBER_OF_OF_THREADS);
        if (numThreads < 1) {
            throw new SetUpException(
                NUMBER_OF_OF_THREADS.getKey() + " is lower than 1");
        }
    }

    @Override
    protected void execute() {
        try {
            // TODO: check which models changed and read Cm accordingly
            vm = hybridCache.readVm();
            bm = hybridCache.readBm();
            Collection<ChangeFlag> bmFlags = hybridCache.getBmFlags();
            this.buildModelChanged = bmFlags.contains(ChangeFlag.ADDITION)
                || bmFlags.contains(ChangeFlag.MODIFICATION)
                || bmFlags.contains(ChangeFlag.DELETION);
            Collection<ChangeFlag> vmFlags = hybridCache.getBmFlags();
            this.buildModelChanged = vmFlags.contains(ChangeFlag.ADDITION)
                || vmFlags.contains(ChangeFlag.MODIFICATION)
                || vmFlags.contains(ChangeFlag.DELETION);

            // if bm or cm changed, we need the entire code model
            if (buildModelChanged || variabilityModelChanged) {
                cm = hybridCache.readCm();
            } else {
                // if bm and cm remained the same, we only need the newly
                // extracted parts of the code model
                cm = hybridCache.readCm(hybridCache
                    .getCmPathsForFlag(ChangeFlag.EXTRACTION_CHANGE));
            }

        } catch (FormatException | IOException exc) {
            exc.printStackTrace();
        }

        if (vm == null || bm == null || cm == null) {
            LOGGER.logError("Couldn't get models");
            return;
        }

        try {
            vmCnf = new VmToCnfConverter().convertVmToCnf(notNull(vm)); // vm
                                                                        // was
                                                                        // initialized
                                                                        // in
                                                                        // execute()

            if (considerVmVarsOnly) {
                relevancyChecker =
                    new FormulaRelevancyChecker(vm, considerVmVarsOnly);
            }

            OrderPreservingParallelizer<SourceFile, List<@NonNull DeadCodeBlock>> parallelizer =
                new OrderPreservingParallelizer<>(this::findDeadCodeBlocks,
                    (deadBlocks) -> {
                        for (DeadCodeBlock block : deadBlocks) {
                            addResult(block);
                        }
                    }, numThreads);


            for (SourceFile sourceFile : cm) {
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
