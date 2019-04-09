package net.ssehub.kernel_haven.incremental.analysis;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.analysis.PipelineAnalysis;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.incremental.storage.IncrementalPostExtraction;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

/**
 * The Class IncrementalDeadCodeAnalysis.
 * 
 * @author Moritz
 */
public class IncrementalDeadCodeAnalysis extends PipelineAnalysis {

    /**
     * Instantiates a new incremental dead code analysis.
     *
     * @param config the config
     */
    public IncrementalDeadCodeAnalysis(@NonNull Configuration config) {
        super(config);
        // TODO Auto-generated constructor stub
    }

    /*
     * (non-Javadoc)
     * 
     * @see net.ssehub.kernel_haven.analysis.PipelineAnalysis#createPipeline()
     */
    @Override
    protected AnalysisComponent<?> createPipeline() throws SetUpException {

        IncrementalDeadCodeFinder dcf = new IncrementalDeadCodeFinder(config,
                new IncrementalPostExtraction(config, getCmComponent(), getBmComponent(), getVmComponent()));

        return dcf;

    }
}
