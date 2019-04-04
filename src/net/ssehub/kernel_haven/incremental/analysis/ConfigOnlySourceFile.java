package net.ssehub.kernel_haven.incremental.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.ssehub.kernel_haven.code_model.CodeElement;
import net.ssehub.kernel_haven.code_model.SourceFile;
import net.ssehub.kernel_haven.util.FormatException;
import net.ssehub.kernel_haven.util.io.json.JsonObject;
import net.ssehub.kernel_haven.util.logic.Formula;
import net.ssehub.kernel_haven.util.null_checks.NonNull;
import net.ssehub.kernel_haven.util.null_checks.Nullable;

/**
 * The Class ConfigOnlySourceFile. This class is a wrapper for SourceFiles that
 * only provides CodeElements that have a CONFIG_ variable in their presence
 * condition.
 * 
 * @author Moritz
 */
public class ConfigOnlySourceFile extends SourceFile {

	/** The elements. */
	private @NonNull List<@NonNull CodeElement> elements = new ArrayList<CodeElement>();

	/**
	 * Instantiates a new config only source file.
	 *
	 * @param sourceFile the source file
	 */
	public ConfigOnlySourceFile(SourceFile sourceFile) {
		super(sourceFile.getPath());
		this.addCodeElements(sourceFile);
	}

	/**
	 * Adds the code elements in the source.
	 *
	 * @param sourceFile the source file
	 */
	private void addCodeElements(SourceFile sourceFile) {
		for (Object obj : sourceFile) {
			CodeElement element = (CodeElement) obj;
			if (containsConfigVariable(element.getCondition())) {
				elements.add(new ChildBlockingCodeElement(element));
			}
			addNestedCodeElements(element);
		}

	}

	/**
	 * Adds the code elements.
	 *
	 * @param element the element
	 */
	private void addNestedCodeElements(CodeElement element) {
		for (Object obj : element) {
			CodeElement child = (CodeElement) obj;
			if (containsConfigVariable(child.getCondition())) {
				elements.add(new ChildBlockingCodeElement(child));
			}
			addNestedCodeElements(child);
		}
	}

	/**
	 * Check if formula contains a config variable.
	 *
	 * @param formula the formula
	 * @return true, if successful
	 */
	private boolean containsConfigVariable(Formula formula) {
		Pattern pattern = Pattern.compile("(?<!(_|\\w|\\d))CONFIG_");
		Matcher matcher = pattern.matcher(formula.toString());
		return matcher.find();
	}

	/**
	 * The Class ChildBlockingCodeElement. This class is a wrapper for CodeElements
	 * so that they do not provide access to their nested elements (children).
	 */
	private static class ChildBlockingCodeElement implements CodeElement {

		/** The wrapped element. */
		CodeElement wrappedElement;

		/**
		 * Instantiates a new ChildBlockingCodeElement.
		 *
		 * @param element the element
		 */
		public ChildBlockingCodeElement(CodeElement element) {
			this.wrappedElement = element;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.ssehub.kernel_haven.code_model.CodeElement#getNestedElementCount()
		 */
		@Override
		public int getNestedElementCount() {
			return 0;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.ssehub.kernel_haven.code_model.CodeElement#iterateNestedElements()
		 */
		public Iterable<@NonNull CodeElement> iterateNestedElements() {
			return new Iterable<@NonNull CodeElement>() {

				@Override
				public @NonNull Iterator<@NonNull CodeElement> iterator() {
					return new Iterator<@NonNull CodeElement>() {

						@Override
						public boolean hasNext() {
							return false;
						}

						@Override
						public CodeElement next() {
							throw new IndexOutOfBoundsException("Iterator for nested elements was "
									+ "used on ChildBlockingCodeElement which is not allowed "
									+ "to have nested elements.");
						}
					};
				}
			};
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.ssehub.kernel_haven.code_model.CodeElement#getNestedElement(int)
		 */
		@Override
		public @NonNull CodeElement getNestedElement(int index) throws IndexOutOfBoundsException {
			throw new IndexOutOfBoundsException(
					"Tried to access a ChildBlockingCodeElement that does not allow access to children");
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * net.ssehub.kernel_haven.code_model.CodeElement#addNestedElement(net.ssehub.
		 * kernel_haven.code_model.CodeElement)
		 */
		@Override
		public void addNestedElement(@NonNull CodeElement element) throws IndexOutOfBoundsException {
			this.wrappedElement.addNestedElement(element);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.ssehub.kernel_haven.code_model.CodeElement#getLineStart()
		 */
		@Override
		public int getLineStart() {
			return wrappedElement.getLineStart();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.ssehub.kernel_haven.code_model.CodeElement#setLineStart(int)
		 */
		@Override
		public void setLineStart(int start) {
			wrappedElement.setLineStart(start);

		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.ssehub.kernel_haven.code_model.CodeElement#getLineEnd()
		 */
		@Override
		public int getLineEnd() {
			return wrappedElement.getLineEnd();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.ssehub.kernel_haven.code_model.CodeElement#setLineEnd(int)
		 */
		@Override
		public void setLineEnd(int end) {
			wrappedElement.setLineEnd(end);

		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.ssehub.kernel_haven.code_model.CodeElement#getSourceFile()
		 */
		@Override
		public @NonNull File getSourceFile() {
			return wrappedElement.getSourceFile();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.ssehub.kernel_haven.code_model.CodeElement#getCondition()
		 */
		@Override
		public @Nullable Formula getCondition() {
			return wrappedElement.getCondition();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.ssehub.kernel_haven.code_model.CodeElement#getPresenceCondition()
		 */
		@Override
		public @NonNull Formula getPresenceCondition() {
			return wrappedElement.getPresenceCondition();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.ssehub.kernel_haven.code_model.CodeElement#serializeCsv()
		 */
		@Override
		public @NonNull List<@NonNull String> serializeCsv() {
			return wrappedElement.serializeCsv();
		}

		@Override
		public Iterator<CodeElement<?>> iterator() {
			return new ArrayList<CodeElement<?>>().iterator();
		}

		@Override
		public @NonNull String elementToString(@NonNull String indentation) {
			// TODO Auto-generated method stub
			return wrappedElement.elementToString(indentation);
		}

		@Override
		public @NonNull String toString(@NonNull String indentation) {
			// TODO Auto-generated method stub
			return wrappedElement.toString();
		}

		@Override
		public void serializeToJson(JsonObject result, @NonNull Function serializeFunction,
				@NonNull Function idFunction) {
			wrappedElement.serializeToJson(result, serializeFunction, idFunction);

		}

		@Override
		public void resolveIds(Map mapping) throws FormatException {
			wrappedElement.resolveIds(mapping);
		}

	}
}