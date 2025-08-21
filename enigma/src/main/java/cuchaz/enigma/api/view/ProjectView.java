package cuchaz.enigma.api.view;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

import cuchaz.enigma.api.DataInvalidationEvent;
import cuchaz.enigma.api.DataInvalidationListener;
import cuchaz.enigma.api.view.entry.EntryView;

public interface ProjectView {
	<T extends EntryView> T deobfuscate(T entry);

	Collection<String> getProjectClasses();

	@Nullable
	ClassNode getBytecode(String className);

	void addDataInvalidationListener(DataInvalidationListener listener);

	void invalidateData(@Nullable Collection<String> classes, DataInvalidationEvent.InvalidationType type);
}
