package net.ssehub.kernel_haven.incremental.analysis;

import static net.ssehub.kernel_haven.util.null_checks.NullHelpers.notNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.ssehub.kernel_haven.SetUpException;
import net.ssehub.kernel_haven.analysis.AnalysisComponent;
import net.ssehub.kernel_haven.build_model.BuildModel;
import net.ssehub.kernel_haven.cnf.Cnf;
import net.ssehub.kernel_haven.cnf.ConverterException;
import net.ssehub.kernel_haven.cnf.FormulaToCnfConverterFactory;
import net.ssehub.kernel_haven.cnf.FormulaToCnfConverterFactory.Strategy;
import net.ssehub.kernel_haven.cnf.IFormulaToCnfConverter;
import net.ssehub.kernel_haven.cnf.ISatSolver;
import net.ssehub.kernel_haven.cnf.SatSolverFactory;
import net.ssehub.kernel_haven.cnf.SolverException;
import net.ssehub.kernel_haven.cnf.VmToCnfConverter;
import net.ssehub.kernel_haven.code_model.CodeElement;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.config.Configuration;
import net.ssehub.kernel_haven.config.DefaultSettings;
import net.ssehub.kernel_haven.incremental.analysis.IncrementalDeadCodeFinder.DeadCodeBlock;
import net.ssehub.kernel_haven.incremental.storage.HybridCache;
import net.ssehub.kernel_haven.incremental.storage.HybridCache.ChangeFlag;
import net.ssehub.kernel_haven.incremental.util.LinuxFormulaRelevancyChecker;
import net.ssehub.kernel_haven.incremental.util.SourceFileDifferenceDetector;
import net.ssehub.kernel_haven.incremental.util.SourceFileDifferenceDetector.Consideration;
import net.ssehub.kernel_haven.util.FormatException;
import net.ssehub.kernel_haven.util.io.TableElement;
import net.ssehub.kernel_haven.util.io.TableRow;
import net.ssehub.kernel_haven.util.logic.Conjunction;
import net.ssehub.kernel_haven.util.logic.Formula;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.util.null_checks.Nullable;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;

/**
 * A simple implementation for dead code detection.
 * 
 * @author Adam, Moritz
 */
public class IncrementalDeadCodeFinder extends AnalysisComponent<DeadCodeBlock> {

	/** The only variabily related variables. */
	protected boolean findDcbForVariabilityRelatedPcsOnly;

	/** The relevancy checker. */
	protected LinuxFormulaRelevancyChecker relevancyChecker;

	/** The vm. */
	protected VariabilityModel vm;

	/** The code model. */
	protected Collection<SourceFile<?>> cm;

	/** The vm cnf. */
	protected Cnf vmCnf;

	/** The bm. */
	protected BuildModel bm;

	/** The previous bm. */
	protected BuildModel previousBm;

	/** The variability model changed. */
	protected boolean variabilityModelChanged;

	/** The build model optimization. */
	protected boolean buildModelOptimization;

	/** The build model changed. */
	protected boolean buildModelChanged;

	/** The hybrid cache. */
	protected @NonNull HybridCache hybridCache;

	/** The post extraction. */
	@NonNull
	protected AnalysisComponent<HybridCache> postExtraction;

	/** The code model config only. */
	protected boolean considerOnlyVariabilityRelatedCodeBlocks;

	protected boolean codeModelOptimization;

	/**
	 * Creates a dead code analysis.
	 *
	 * @param config         The user configuration; not used.
	 * @param postExtraction the post extraction
	 * @throws SetUpException the set up exception
	 */
	public IncrementalDeadCodeFinder(@NonNull Configuration config,
			@NonNull AnalysisComponent<HybridCache> postExtraction) throws SetUpException {
		super(config);
		IncrementalDeadCodeAnalysisSettings.registerAllSettings(config);
		this.postExtraction = postExtraction;

		findDcbForVariabilityRelatedPcsOnly = config.getValue(DefaultSettings.ANALYSIS_USE_VARMODEL_VARIABLES_ONLY);
		buildModelOptimization = config.getValue(IncrementalDeadCodeAnalysisSettings.BUILD_MODEL_OPTIMIZATION);
		codeModelOptimization = config.getValue(IncrementalDeadCodeAnalysisSettings.CODE_MODEL_OPTIMIZATION);
	}

	/**
	 * A class that holds all variables relevant for solving SAT. This is created
	 * once per {@link DeadCodeFinder#findDeadCodeBlocks(SourceFile))}, so that this
	 * method is thread-safe.
	 */
	private static class SatUtilities {

		/** The converter. */
		private @NonNull IFormulaToCnfConverter converter;

		/** The solver. */
		private @NonNull ISatSolver solver;

		/** The sat cache. */
		private @NonNull Map<Formula, Boolean> satCache;

		/**
		 * Creates this instance.
		 * 
		 * @param converter the formula to CNF converter.
		 * @param solver    The SAT solver.
		 * @param satCache  The SAT cache.
		 */
		SatUtilities(@NonNull IFormulaToCnfConverter converter, @NonNull ISatSolver solver,
				@NonNull Map<Formula, Boolean> satCache) {
			this.converter = converter;
			this.solver = solver;
			this.satCache = satCache;
		}

	}

	/**
	 * Finds dead code blocks. This method is thread-safe.
	 * 
	 * @param sourceFile The source file to search in.
	 * @return The list of dead code blocks.
	 */
	protected @NonNull List<@NonNull DeadCodeBlock> findDeadCodeBlocks(@NonNull SourceFile<?> sourceFile) {

		List<@NonNull DeadCodeBlock> result = new ArrayList<>();

		Formula filePc = bm.getPc(sourceFile.getPath());

		boolean runForFile = true;

		// skip files with no presence condition
		if (filePc == null) {
			runForFile = false;
			LOGGER.logInfo("Skipping " + sourceFile.getPath() + " because it has no build PC.");
		} else if (buildModelOptimization && buildModelChanged && !variabilityModelChanged && previousBm != null) {
			Collection<ChangeFlag> flagsForCodeFile = hybridCache.getFlags(sourceFile);
			// we can only consider removing files from pc-checking when they
			// were not newly extracted
			if (!flagsForCodeFile.contains(ChangeFlag.EXTRACTION_CHANGE)) {
				Formula previousFilePc = previousBm.getPc(sourceFile.getPath());
				runForFile = !filePc.equals(previousFilePc);
				if (!runForFile) {
					LOGGER.logInfo("Skipping " + sourceFile.getPath() + " because its build PC did not change.");
				} else {
					LOGGER.logInfo("Processing " + sourceFile + " because its build PC did change.");
				}
			} 
		}

		if (runForFile) {
			LOGGER.logInfo("Running for file " + sourceFile.getPath());
			LOGGER.logDebug("File PC: " + filePc);

			// Lazy initialization for SatUtils. Only gets initialized if the sourceFile
			// contains at least one element.
			SatUtilities satUtils = null;

			for (CodeElement<?> element : sourceFile) {
				if (satUtils == null) {
					satUtils = new SatUtilities(FormulaToCnfConverterFactory.create(Strategy.RECURISVE_REPLACING),
							SatSolverFactory.createSolver(vmCnf, false), new HashMap<>(10000));
				}

				try {
					checkElement(element, filePc, sourceFile, satUtils, result);
				} catch (SolverException | ConverterException e) {
					LOGGER.logException("Exception while trying to check element", e);
				}

			}
		}

		return result;
	}

	/**
	 * Checks whether the given formula is satisfiable with the variability model.
	 * Internally, this method has a cache to speed up when the same formula is
	 * passed to it several times.
	 * 
	 * @param pc       The formula to check.
	 * @param satUtils The sat utils to use.
	 * 
	 * @return Whether the formula is satisfiable with the variability model.
	 * 
	 * @throws ConverterException If the conversion to CNF fails.
	 * @throws SolverException    If the SAT-solver fails.
	 */
	private boolean isSat(@NonNull Formula pc, @NonNull SatUtilities satUtils)
			throws ConverterException, SolverException {

		Boolean sat = satUtils.satCache.get(pc);

		if (sat == null) {
			Cnf pcCnf = satUtils.converter.convert(pc);

			String[] cnfLines = pcCnf.toString().split("\n");
			String[] output = new String[cnfLines.length + 1];
			System.arraycopy(cnfLines, 0, output, 1, cnfLines.length);
			output[0] = "PcCnf: ";
			LOGGER.logDebug(output);

			sat = satUtils.solver.isSatisfiable(pcCnf);
			satUtils.satCache.put(pc, sat);
			LOGGER.logDebug("sat(" + pc + ") = " + sat);
		}

		return sat;
	}

	/**
	 * Checks if a given element is dead. Recursively walks over each child element,
	 * too.
	 * 
	 * @param element    The element to check.
	 * @param filePc     The presence condition of the file.
	 * @param sourceFile The source file; used for creating the result.
	 * @param satUtils   The SAT utils to use.
	 * @param result     The list to add result {@link DeadCodeBlock}s to.
	 * 
	 * @throws ConverterException If converting the formula to CNF fails.
	 * @throws SolverException    If solving the CNF fails.
	 */
	private void checkElement(@NonNull CodeElement<?> element, @NonNull Formula filePc,
			@NonNull SourceFile<?> sourceFile, @NonNull SatUtilities satUtils,
			@NonNull List<@NonNull DeadCodeBlock> result) throws ConverterException, SolverException {

		Formula pc = new Conjunction(element.getPresenceCondition(), filePc);

		boolean considerBlock = this.relevancyChecker != null
				? this.relevancyChecker.visit(element.getPresenceCondition())
				: true;

		if (considerBlock && !isSat(pc, satUtils)) {
			DeadCodeBlock deadBlock = new DeadCodeBlock(element, filePc);
			LOGGER.logInfo("Found dead block: " + deadBlock);
			result.add(deadBlock);
		}
		int nestedCount = element.getNestedElementCount();
		for (int i = 0; i < nestedCount; i++) {
			CodeElement<?> child = element.getNestedElement(i);
			checkElement(child, filePc, sourceFile, satUtils, result);
		}
	}

	/**
	 * A dead code block.
	 */
	@TableRow
	public static class DeadCodeBlock {

		/** The source file. */
		private @NonNull File sourceFile;

		/** The file pc. */
		private @Nullable Formula filePc;

		/** The start line. */
		private int startLine;

		/** The end line. */
		private int endLine;

		/** The presence condition. */
		private @Nullable Formula presenceCondition;

		/**
		 * Creates a dead code block.
		 * 
		 * @param sourceFile The source file.
		 * @param line       The line of the element.
		 */
		public DeadCodeBlock(@NonNull File sourceFile, int line) {
			this.sourceFile = sourceFile;
			this.startLine = line;
			this.endLine = 0;
			this.presenceCondition = null;
			this.filePc = null;
		}

		/**
		 * Converts a {@link CodeElement} into a {@link DeadCodeBlock}. This constructor
		 * stores more information.
		 * 
		 * @param deadElement An element which was identified to be dead.
		 * @param filePc      The presence condition for the complete file, maybe
		 *                    <tt>null</tt>
		 */
		public DeadCodeBlock(@NonNull CodeElement<?> deadElement, @NonNull Formula filePc) {
			this(deadElement.getSourceFile(), deadElement.getLineStart());
			this.endLine = deadElement.getLineEnd();
			this.presenceCondition = deadElement.getPresenceCondition();
			this.filePc = filePc;
		}

		/**
		 * Returns the source file that this block is in.
		 * 
		 * @return The source file.
		 */
		@TableElement(name = "Source File", index = 0)
		public @NonNull File getSourceFile() {
			return sourceFile;
		}

		/**
		 * Returns the presence condition (PC) of the file.
		 * 
		 * @return The PC of the file. May be <code>null</code>.
		 */
		@TableElement(name = "File PC", index = 1)
		public @Nullable Formula getFilePc() {
			return filePc;
		}

		/**
		 * The starting line of this block.
		 * 
		 * @return The staring line.
		 */
		@TableElement(name = "Line Start", index = 2)
		public int getStartLine() {
			return startLine;
		}

		/**
		 * The end line of this block.
		 * 
		 * @return The end line.
		 */
		@TableElement(name = "Line End", index = 3)
		public int getEndLine() {
			return endLine;
		}

		/**
		 * Returns the presence condition (PC) of this block.
		 * 
		 * @return The PC.
		 */
		@TableElement(name = "Presence Condition", index = 4)
		public @Nullable Formula getPresenceCondition() {
			return presenceCondition;
		}

		/**
		 * To string.
		 *
		 * @return the string
		 */
		@Override
		public @NonNull String toString() {
			char separator = ' ';
			StringBuffer result = new StringBuffer();
			result.append(sourceFile.getPath());
			result.append(separator);
			if (null != filePc) {
				result.append(filePc.toString());
			}
			result.append(separator);
			result.append(startLine);
			result.append(separator);
			if (0 != endLine) {
				result.append(endLine);
			}
			result.append(separator);
			if (null != presenceCondition) {
				result.append(presenceCondition.toString());
			}

			return notNull(result.toString());
		}

	}

	/**
	 * Load models from hybrid cache according to the situation required for the
	 * analysis.
	 */
	protected void loadModelsFromHybridCache() {
		this.hybridCache = postExtraction.getNextResult();
		try {

			vm = hybridCache.readVm();
			bm = hybridCache.readBm();

			Collection<ChangeFlag> bmFlags = hybridCache.getBmFlags();

			// If the changes were auxillary changes, we do not consider them to be relevant
			// changes for the analysis.
			this.buildModelChanged = bmFlags.contains(ChangeFlag.EXTRACTION_CHANGE)
					|| bmFlags.contains(ChangeFlag.ADDITION) || bmFlags.contains(ChangeFlag.MODIFICATION)
					|| bmFlags.contains(ChangeFlag.DELETION) && !bmFlags.contains(ChangeFlag.AUXILLARY_CHANGE);

			Collection<ChangeFlag> vmFlags = hybridCache.getVmFlags();
			// If the changes were auxillary changes, we do not consider them to be relevant
			// changes for the analysis.
			this.variabilityModelChanged = vmFlags.contains(ChangeFlag.EXTRACTION_CHANGE)
					|| vmFlags.contains(ChangeFlag.ADDITION) || vmFlags.contains(ChangeFlag.MODIFICATION)
					|| vmFlags.contains(ChangeFlag.DELETION) && !vmFlags.contains(ChangeFlag.AUXILLARY_CHANGE);

			// if bm or cm changed, we need the entire code model
			if (buildModelChanged || variabilityModelChanged) {
				LOGGER.logInfo("Performing a full analysis based on the complete code model."
						+ " from the current and previous extractions");
				cm = hybridCache.readCm();
			} else {
				// if bm and vm remained the same, we only need the newly
				// extracted parts of the code model
				LOGGER.logInfo("Performing a partial analysis based on newly extracted parts of the code model.");
				cm = hybridCache.readCmForFlags(ChangeFlag.EXTRACTION_CHANGE);

			}

		} catch (FormatException | IOException exc) {
			exc.printStackTrace();
		}
	}

	/**
	 * Execute.
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

			// If only variability related variables should be considered, the
			// set of considered SourceFile elements is reduced to the source files
			// that were changed in regards to their variability information
			if (findDcbForVariabilityRelatedPcsOnly) {
				relevancyChecker = new LinuxFormulaRelevancyChecker(vm, true);
			}

			SourceFileDifferenceDetector detector = null;
			boolean reduceCodeModel = false;
			if (codeModelOptimization) {
				try {
					detector = new SourceFileDifferenceDetector(Consideration.ONLY_VARIABILITY_CHANGE, vm,
							hybridCache.readPreviousVm());
					reduceCodeModel = !buildModelChanged && !variabilityModelChanged;
				} catch (IOException e) {
					LOGGER.logException("Could not read previous variability model", e);
				}
			}

			for (SourceFile<?> sourceFile : cm) {
				boolean analyzeSourceFile = true;
				if (reduceCodeModel) {
					try {
						analyzeSourceFile = detector.isDifferent(sourceFile,
								hybridCache.readPreviousCm(sourceFile.getPath()));
						if (!analyzeSourceFile) {
							LOGGER.logInfo("Skipping" + sourceFile.getPath()
									+ " because the structure of variability related code blocks did not change");
						}

					} catch (IOException e) {
						LOGGER.logException("Could not read previous sourceFile for path " + sourceFile.getPath(), e);
					}
				}

				if (analyzeSourceFile) {
					List<@NonNull DeadCodeBlock> deadBlocks = findDeadCodeBlocks(sourceFile);
					for (DeadCodeBlock block : deadBlocks) {
						addResult(block);
					}
				}
			}

		} catch (FormatException e) {
			LOGGER.logException("Invalid variability model", e);
		}
	}

	/**
	 * Gets the result name.
	 *
	 * @return the result name
	 */
	@Override
	public String getResultName() {
		return "Dead Code Blocks";
	}

}
