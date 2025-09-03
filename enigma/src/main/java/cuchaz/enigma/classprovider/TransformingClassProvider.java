package cuchaz.enigma.classprovider;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

import cuchaz.enigma.api.service.ClassTransformerService;
import cuchaz.enigma.api.view.entry.MapperView;
import cuchaz.enigma.translation.mapping.EntryRemapper;

public class TransformingClassProvider implements ClassProvider, ClassTransformerService.ClassTransformContext {
	private final ClassProvider delegate;
	private final ClassTransformerService service;
	private final EntryRemapper remapper;

	public TransformingClassProvider(ClassProvider delegate, ClassTransformerService service, EntryRemapper remapper) {
		this.delegate = delegate;
		this.service = service;
		this.remapper = remapper;
	}

	@Override
	public Collection<String> getClassNames() {
		return delegate.getClassNames();
	}

	@Override
	@Nullable
	public ClassNode get(String name) {
		ClassNode classNode = delegate.get(name);

		if (classNode == null) {
			return null;
		}

		service.transform(classNode, this);

		return classNode;
	}

	@Override
	public void invalidateCache() {
		delegate.invalidateCache();
	}

	@Override
	@Nullable
	public ClassNode getDownstreamClass(String name) {
		return delegate.get(name);
	}

	@Override
	public MapperView getMapper() {
		return remapper;
	}
}
