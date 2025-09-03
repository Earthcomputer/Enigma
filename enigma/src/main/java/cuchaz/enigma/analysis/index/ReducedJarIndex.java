package cuchaz.enigma.analysis.index;

import java.util.List;
import java.util.Map;

import cuchaz.enigma.translation.mapping.EntryResolver;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.ParentedEntry;

/**
 * An interface containing only methods ready to be used on a {@link JarIndex} that is partially complete, at the point
 * where the {@link JarIndex.MidIndexAction} is called.
 */
public interface ReducedJarIndex {
	Map<ClassEntry, List<ParentedEntry<?>>> getChildrenByClass();
	EntryIndex getEntryIndex();
	InheritanceIndex getInheritanceIndex();
	BridgeMethodIndex getBridgeMethodIndex();
	EntryResolver getEntryResolver();
}
