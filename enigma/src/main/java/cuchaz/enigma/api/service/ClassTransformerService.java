package cuchaz.enigma.api.service;

import java.util.Collection;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;

import cuchaz.enigma.api.view.entry.MapperView;

public interface ClassTransformerService extends EnigmaService {
	EnigmaServiceType<ClassTransformerService> TYPE = EnigmaServiceType.create("class_transformer");

	void transform(ClassNode classNode, ClassTransformContext context);

	@ApiStatus.NonExtendable
	interface ClassTransformContext {
		@Nullable
		ClassNode getDownstreamClass(String name);

		Collection<String> getClassNames();

		MapperView getMapper();
	}
}
