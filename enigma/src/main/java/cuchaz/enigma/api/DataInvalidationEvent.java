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
		 */
		CLASS,
	}
}
