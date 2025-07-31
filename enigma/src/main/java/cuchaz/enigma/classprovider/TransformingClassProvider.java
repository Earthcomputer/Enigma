package cuchaz.enigma.classprovider;

import java.util.Collection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

import cuchaz.enigma.EnigmaServices;
import cuchaz.enigma.api.service.ClassTransformerService;

public class TransformingClassProvider implements ClassProvider {
	private final ClassProvider delegate;
	private final EnigmaServices services;

	public TransformingClassProvider(ClassProvider delegate, EnigmaServices services) {
		this.delegate = delegate;
		this.services = services;
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

		services.get(ClassTransformerService.TYPE).forEach(transformer -> transformer.transform(classNode));

		return classNode;
	}
}
