package net.ssehub.kernel_haven.incremental.analysis;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.analysis.PipelineAnalysis;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.incremental.settings.IncrementalAnalysisSettings;
import net.ssehub.kernel_haven.incremental.storage.HybridCacheAdapter;
import net.ssehub.kernel_haven.incremental.storage.HybridCacheAdapter.CodeModelProcessing;
import net.ssehub.kernel_haven.incremental.storage.IncrementalPostExtraction;
import net.ssehub.kernel_haven.undead_analyzer.ThreadedDeadCodeFinder;
import net.ssehub.kernel_haven.util.Logger;

// TODO: Auto-generated Javadoc
/**
 * Incremental implementation of the {@link DeadCodeAnalysis}.
 *
 * @author Moritz
 */
public class IncrementalThreadedDeadCodeAnalysis extends PipelineAnalysis {

    /** The Constant LOGGER. */
    private static final Logger LOGGER = Logger.get();

    /**
     * Instantiates a new incremental dead code analysis.
     *
     * @param config
     *            the config
     */
    public IncrementalThreadedDeadCodeAnalysis(Configuration config) {
        super(config);
    }

    /**
     * Creates the pipeline.
     *
     * @return the analysis component
     * @throws SetUpException the set up exception
     */
    /*
     * (non-Javadoc)
     * 
     * @see net.ssehub.kernel_haven.analysis.PipelineAnalysis#createPipeline()
     */
    @Override
    protected AnalysisComponent<?> createPipeline() throws SetUpException {
        boolean updatedBuildModel =
            config.getValue(IncrementalAnalysisSettings.EXTRACT_BUILD_MODEL);
        boolean updatedVariabilityModel = config
            .getValue(IncrementalAnalysisSettings.EXTRACT_VARIABILITY_MODEL);

        CodeModelProcessing cmProcessing;

        if (!updatedBuildModel && !updatedVariabilityModel) {
            cmProcessing = CodeModelProcessing.NEWLY_EXTRACTED;
            LOGGER.logInfo("Neither build nor variability-model were modified "
                + "compared to the last revision that was analyzed. Therefore "
                + IncrementalDeadCodeAnalysis.class.getSimpleName()
                + " will run on the updated parts of the code-model only.");
        } else {
            cmProcessing = CodeModelProcessing.COMPLETE;
            LOGGER.logInfo("variability and/or build-model was modified "
                + "compared to the last revision that was analyzed. Therefore "
                + IncrementalDeadCodeAnalysis.class.getSimpleName()
                + " will perform a full analysis.");
        }

        HybridCacheAdapter hca = new HybridCacheAdapter(config,
            new IncrementalPostExtraction(config, getCmComponent(),
                getBmComponent(), getVmComponent()),
            cmProcessing);

        ThreadedDeadCodeFinder dcf = new ThreadedDeadCodeFinder(config,
            hca.getVmComponent(), hca.getBmComponent(), hca.getCmComponent());

        return dcf;

    }

}