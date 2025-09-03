package cuchaz.enigma.api.view.entry;

import java.util.stream.Stream;

public interface MapperView {
	<T extends EntryView> T remap(T entry);
	Stream<? extends EntryView> getAllEntries();
}
