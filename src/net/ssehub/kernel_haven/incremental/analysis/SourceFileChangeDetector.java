package net.ssehub.kernel_haven.incremental.analysis;

import java.util.Iterator;

import net.ssehub.kernel_haven.code_model.CodeElement;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.undead_analyzer.FormulaRelevancyChecker;
import net.ssehub.kernel_haven.variability_model.VariabilityModel;

// TODO: Auto-generated Javadoc
/**
 * The Class CodeFileComparator.
 * 
 * @author Moritz
 */
public class SourceFileChangeDetector {

    /**
     * The Enum Consideration.
     */
    public static enum Consideration {

        /** The all elements. */
        ANY_CHANGE,
        /** The ignore linechange. */
        ANY_CHANGE_EXCEPT_LINECHANGE,
        /** The only variability change. */
        ONLY_VARIABILITY_CHANGE;
    }

    /** The consideration. */
    private Consideration consideration;

    /** The var model A. */
    private VariabilityModel varModelA;

    /** The var model B. */
    private VariabilityModel varModelB;

    /**
     * Instantiates a new code file comparator.
     *
     * @param consideration the consideration
     * @param varModelA     the var model A
     * @param varModelB     the var model B
     */
    public SourceFileChangeDetector(Consideration consideration, VariabilityModel varModelA,
            VariabilityModel varModelB) {
        this.consideration = consideration;
        this.varModelA = varModelA;
        this.varModelB = varModelB;
    }

    /**
     * Checks for changed.
     *
     * @param fileA the file A
     * @param fileB the file B
     * @return true, if changed
     */
    public boolean hasChanged(SourceFile<?> fileA, SourceFile<?> fileB) {
        boolean changed = false;
        if (!fileA.equals(fileB)) {
            if (this.consideration == Consideration.ANY_CHANGE) {
                changed = true;
            } else {
                if (this.consideration == Consideration.ONLY_VARIABILITY_CHANGE) {
                    reduceSourceFile(fileA, varModelA);
                    reduceSourceFile(fileB, varModelB);
                }
                changed = !isStructureSame(fileA, fileB);
            }
        }
        return changed;
    }

    /**
     * Reduce source file.
     *
     * @param file     the file
     * @param varModel the var model
     */
    private void reduceSourceFile(SourceFile<?> file, VariabilityModel varModel) {
        // TODO Idea reduce source file so that it only contains blocks with
        // dependencies to the variability model
        FormulaRelevancyChecker checker = new FormulaRelevancyChecker(varModel, true);

    }

    /**
     * Checks for changes within the structure of two given source files.
     *
     * @param fileA the file A
     * @param fileB the file B
     * @return true, if unchanged
     */
    private boolean isStructureSame(SourceFile<?> fileA, SourceFile<?> fileB) {

        // The Generic in {@link SourceFile} must be a {@link CodeElement}.
        @SuppressWarnings("unchecked")
        Iterator<CodeElement<?>> fileAIterator = (Iterator<CodeElement<?>>) fileA.iterator();
        @SuppressWarnings("unchecked")
        Iterator<CodeElement<?>> fileBIterator = (Iterator<CodeElement<?>>) fileB.iterator();

        boolean unchanged = fileA.getTopElementCount() == fileB.getTopElementCount();

        while (unchanged && (fileAIterator.hasNext())) {
            // We can assume next() can be called for both elements as we already
            // confirmed that the number of elements is the same
            CodeElement<?> fileAElement = fileAIterator.next();
            CodeElement<?> fileBElement = fileBIterator.next();
            unchanged = isStructureSame(fileAElement, fileBElement);

        }
        return unchanged;
    }

    /**
     * Checks for changes within the structure of both elements and their respective
     * nested elements.
     *
     * @param fileAElement the file A element
     * @param fileBElement the file B element
     * @return true, if unchanged
     */
    private boolean isStructureSame(CodeElement<?> fileAElement, CodeElement<?> fileBElement) {

        int fileANestedElementCount = fileAElement.getNestedElementCount();
        int fileBNestedElementCount = fileBElement.getNestedElementCount();

        boolean unchanged = fileAElement.getCondition().equals(fileBElement.getCondition())
                && fileANestedElementCount == fileBNestedElementCount;

        for (int i = 0; unchanged && i < fileANestedElementCount; i++) {
            unchanged = isStructureSame(fileAElement.getNestedElement(i), fileBElement.getNestedElement(i));
        }

        return unchanged;
    }
}
