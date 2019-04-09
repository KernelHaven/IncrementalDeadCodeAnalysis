package net.ssehub.kernel_haven.incremental.analysis;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.analysis.PipelineAnalysis;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.incremental.storage.IncrementalPostExtraction;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

/**
 * The Class IncrementalThreadedDeadCodeAnalysis.
 * 
 * @author Moritz
 */
public class IncrementalThreadedDeadCodeAnalysis extends PipelineAnalysis {

    /**
     * Instantiates a new incremental threaded dead code analysis.
     *
     * @param config the config
     */
    public IncrementalThreadedDeadCodeAnalysis(@NonNull Configuration config) {
        super(config);
    }

    /*
     * (non-Javadoc)
     * 
     * @see net.ssehub.kernel_haven.analysis.PipelineAnalysis#createPipeline()
     */
    @Override
    protected @NonNull AnalysisComponent<?> createPipeline() throws SetUpException {
        IncrementalThreadedDeadCodeFinder dcf = new IncrementalThreadedDeadCodeFinder(config,
                new IncrementalPostExtraction(config, getCmComponent(), getBmComponent(), getVmComponent()));

        return dcf;

    }

}
