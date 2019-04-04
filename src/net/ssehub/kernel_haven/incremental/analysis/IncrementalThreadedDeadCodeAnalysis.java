package net.ssehub.kernel_haven.incremental.analysis;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.analysis.PipelineAnalysis;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.incremental.storage.IncrementalPostExtraction;
import net.ssehub.kernel_haven.util.null_checks.NonNull;

public class IncrementalThreadedDeadCodeAnalysis extends PipelineAnalysis {

	public IncrementalThreadedDeadCodeAnalysis(@NonNull Configuration config) {
		super(config);
	}

	@Override
	protected @NonNull AnalysisComponent<?> createPipeline() throws SetUpException {
		IncrementalThreadedDeadCodeFinder dcf = new IncrementalThreadedDeadCodeFinder(config,
				new IncrementalPostExtraction(config, getCmComponent(), getBmComponent(), getVmComponent()));

		return dcf;

	}

}
