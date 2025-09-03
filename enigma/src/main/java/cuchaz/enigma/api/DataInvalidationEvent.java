package cuchaz.enigma.api;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;

public interface DataInvalidationEvent {
	/**
	 * The classes for which the invalidation applies, or {@code null} if the invalidation applies to all classes.
	 */
	@Nullable
	Collection<String> getClasses();

	InvalidationType getType();

	enum InvalidationType {
		/**
		 * Only mappings are being invalidated.
		 */
		MAPPINGS,

		/**
		 * Javadocs are being invalidated. This also implies {@link #MAPPINGS}.
		 */
		JAVADOC,

		/**
		 * Class bytecode is being invalidated. This also implies {@link #JAVADOC} and {@link #MAPPINGS}.
		 *
		 * <p>Note that this can only be used to invalidate class bytecode that can be hotswapped, i.e., method
		 * contents, and not class, field, and method definitions.
		 */
		CLASS,
	}
}
