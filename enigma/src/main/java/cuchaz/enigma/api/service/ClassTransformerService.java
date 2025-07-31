package cuchaz.enigma.api.service;

import org.objectweb.asm.tree.ClassNode;

public interface ClassTransformerService extends EnigmaService {
	EnigmaServiceType<ClassTransformerService> TYPE = EnigmaServiceType.create("class_transformer");

	void transform(ClassNode classNode);
}
