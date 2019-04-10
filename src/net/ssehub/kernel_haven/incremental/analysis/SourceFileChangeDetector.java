package net.ssehub.kernel_haven.incremental.analysis;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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
                    Set<CodeElement<?>> relevancyA =
                            collectRelevantElements(fileA, new LinuxFormulaRelevancyChecker(varModelA, true));
                    Set<CodeElement<?>> relevancyB =
                            collectRelevantElements(fileB, new LinuxFormulaRelevancyChecker(varModelB, true));
                    changed = !isStructureSame(fileA, fileB, relevancyA, relevancyB);
                } else {
                    changed = !isStructureSame(fileA, fileB, null, null);
                }
            }

        }
        return changed;
    }

    /**
     * Collects {@link CodeElement}s that are considered relevant from a given
     * {@link SourceFile}. If {@link FormulaRelevancyChecker} considers an element
     * to be relevant, all of its parents as well as children will be considered
     * relevant as well.
     *
     * @param file    the file
     * @param checker the checker
     * @return the sets the
     */
    protected Set<CodeElement<?>> collectRelevantElements(SourceFile<?> file, LinuxFormulaRelevancyChecker checker) {
        Set<CodeElement<?>> relevantElements = new HashSet<CodeElement<?>>();
        for (CodeElement<?> element : file) {
            collectRelevantElements(element, checker, new HashSet<CodeElement<?>>(), new HashSet<CodeElement<?>>(),
                    relevantElements);
        }
        return relevantElements;
    }

    /**
     * Collects {@link CodeElement}s that are considered relevant from a given
     * {@link CodeElement}. If {@link FormulaRelevancyChecker} considers an element
     * to be relevant, all of its parents as well as children will be considered
     * relevant as well.
     *
     * @param currentElement           the current element
     * @param checker                  the checker
     * @param parents                  the parents
     * @param directlyRelevantElements the directly relevant elements
     * @param relevantElements         the relevant elements
     */
    protected void collectRelevantElements(CodeElement<?> currentElement, LinuxFormulaRelevancyChecker checker,
            Set<CodeElement<?>> parents, Set<CodeElement<?>> directlyRelevantElements,
            Set<CodeElement<?>> relevantElements) {
        // if the path contains an element that is directly relevant,
        // currentElement is relevant as well.
        if (!Collections.disjoint(relevantElements, parents)) {
            relevantElements.add(currentElement);
            // Otherwise check if the element is relevant on its own (=directly relevant)
        } else if (checker.visit(currentElement.getPresenceCondition())) {
            relevantElements.add(currentElement);
            directlyRelevantElements.add(currentElement);
            // if the element is element on its own, it also makes all of its parents
            // relevant
            relevantElements.addAll(parents);
        }

        // After the current element itself was handled, take care of its nested
        // elements
        int nestedCount = currentElement.getNestedElementCount();
        if (nestedCount > 0) {
            // Create a copy of the parent list and add currentElement as a parent
            Set<CodeElement<?>> newParents = new HashSet<CodeElement<?>>(parents);
            newParents.add(currentElement);
            for (int i = 0; i < nestedCount; i++) {
                CodeElement<?> nestedElement = currentElement.getNestedElement(i);
                collectRelevantElements(nestedElement, checker, newParents, directlyRelevantElements, relevantElements);
            }
        }

    }

    /**
     * Checks for changes within the structure of two given source files.
     *
     * @param fileA      the file A
     * @param fileB      the file B
     * @param relevancyA the relevancy A
     * @param relevancyB the relevancy B
     * @return true, if unchanged
     */
    protected boolean isStructureSame(SourceFile<?> fileA, SourceFile<?> fileB, Set<CodeElement<?>> relevancyA,
            Set<CodeElement<?>> relevancyB) {

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
            unchanged = isStructureSame(fileAElement, fileBElement, relevancyA, relevancyB);

        }
        return unchanged;
    }

    /**
     * Checks for changes within the structure of both elements and their respective
     * nested elements.
     *
     * @param fileAElement the file A element
     * @param fileBElement the file B element
     * @param relevancyA   the relevancy A
     * @param relevancyB   the relevancy B
     * @return true, if unchanged
     */
    private boolean isStructureSame(CodeElement<?> fileAElement, CodeElement<?> fileBElement,
            Set<CodeElement<?>> relevancyA, Set<CodeElement<?>> relevancyB) {

        boolean unchanged = getNestedCount(fileAElement, relevancyA) == getNestedCount(fileBElement, relevancyB);

        unchanged = isStructureSame(fileAElement, fileBElement, relevancyA, relevancyB);

        return unchanged;
    }

    /**
     * Gets the nested count.
     *
     * @param fileElement the file element
     * @param relevancy   the relevancy
     * @return the nested count
     */
    public int getNestedCount(CodeElement<?> fileElement, Set<CodeElement<?>> relevancy) {
        int nestedCount;
        // if no relevancy was determined, we consider all elements to be relevant
        // children
        if (relevancy == null) {
            nestedCount = fileElement.getNestedElementCount();

            // otherwise only those in the relevancy list are considered
        } else {
            int allNestedElementsCount = fileElement.getNestedElementCount();
            nestedCount = 0;
            for (int i = 0; i < allNestedElementsCount; i++) {
                if (relevancy.contains(fileElement.getNestedElement(i))) {
                    nestedCount++;
                }
            }

        }
        return nestedCount;
    }
}
