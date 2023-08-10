package cuchaz.enigma.translation.annotations;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.objectweb.asm.tree.ClassNode;

import cuchaz.enigma.Enigma;
import cuchaz.enigma.classprovider.ClassProvider;
import cuchaz.enigma.translation.mapping.tree.EntryTree;

public class AnnotationModifierClassProvider implements ClassProvider {
	private final ClassProvider delegate;
	private final Supplier<EntryTree<AnnotationMods>> annotationModsTree;

	public AnnotationModifierClassProvider(ClassProvider delegate, Supplier<EntryTree<AnnotationMods>> annotationModsTree) {
		this.delegate = delegate;
		this.annotationModsTree = annotationModsTree;
	}

	@Nullable
	@Override
	public ClassNode get(String name) {
		ClassNode clazz = delegate.get(name);

		if (clazz == null) {
			return null;
		}

		ClassNode newClass = new ClassNode();
		clazz.accept(new AnnotationModifierVisitor(Enigma.ASM_VERSION, newClass, annotationModsTree.get()));
		return newClass;
	}
}
