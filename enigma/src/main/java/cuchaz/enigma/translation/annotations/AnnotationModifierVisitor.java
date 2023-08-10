package cuchaz.enigma.translation.annotations;

import java.util.List;

import javax.annotation.Nullable;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.representation.entry.ClassDefEntry;
import cuchaz.enigma.translation.representation.entry.FieldDefEntry;
import cuchaz.enigma.translation.representation.entry.MethodDefEntry;
import cuchaz.enigma.utils.Pair;

public class AnnotationModifierVisitor extends ClassVisitor {
	private final EntryTree<AnnotationMods> annotationModsTree;
	private ClassDefEntry classEntry;
	private AnnotationMods classMods;
	private boolean addingEntries = false;

	public AnnotationModifierVisitor(int api, ClassVisitor classVisitor, EntryTree<AnnotationMods> annotationModsTree) {
		super(api, classVisitor);
		this.annotationModsTree = annotationModsTree;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		classEntry = ClassDefEntry.parse(access, name, signature, superName, interfaces);
		classMods = annotationModsTree.get(classEntry);

		if (classMods != null) {
			access = applyDeprecated(access, classMods);

			addingEntries = true;

			for (AnnotationMods.AddedEntry addedEntry : classMods.added()) {
				acceptArgs(cv.visitAnnotation(addedEntry.annotationDesc(), addedEntry.visible()), addedEntry.annotationArgs());
			}

			for (AnnotationMods.TypeAddedEntry typeAddedEntry : classMods.typeAdded()) {
				acceptArgs(cv.visitTypeAnnotation(typeAddedEntry.typeRef(), TypePath.fromString(typeAddedEntry.path()), typeAddedEntry.annotationDesc(), typeAddedEntry.visible()), typeAddedEntry.annotationArgs());
			}

			addingEntries = false;
		}

		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		if (classMods != null && !addingEntries && classMods.removed().contains(descriptor)) {
			return null;
		}

		return super.visitAnnotation(descriptor, visible);
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
		if (classMods != null && !addingEntries && classMods.typeRemoved().contains(new AnnotationMods.TypeRemovedEntry(typeRef, typePath.toString(), descriptor))) {
			return null;
		}

		return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
		FieldDefEntry fieldEntry = FieldDefEntry.parse(classEntry, access, name, descriptor, signature);
		AnnotationMods fieldMods = annotationModsTree.get(fieldEntry);

		if (fieldMods == null) {
			return cv.visitField(access, name, descriptor, signature, value);
		}

		access = applyDeprecated(access, fieldMods);

		FieldVisitor fv = cv.visitField(access, name, descriptor, signature, value);

		if (fv == null) {
			return null;
		}

		for (AnnotationMods.AddedEntry addedEntry : fieldMods.added()) {
			acceptArgs(fv.visitAnnotation(addedEntry.annotationDesc(), addedEntry.visible()), addedEntry.annotationArgs());
		}

		for (AnnotationMods.TypeAddedEntry typeAddedEntry : fieldMods.typeAdded()) {
			acceptArgs(fv.visitTypeAnnotation(typeAddedEntry.typeRef(), TypePath.fromString(typeAddedEntry.path()), typeAddedEntry.annotationDesc(), typeAddedEntry.visible()), typeAddedEntry.annotationArgs());
		}

		return new AnnotationModifierFieldVisitor(api, fv, fieldMods);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodDefEntry methodEntry = MethodDefEntry.parse(classEntry, access, name, descriptor, signature);
		AnnotationMods methodMods = annotationModsTree.get(methodEntry);

		if (methodMods == null) {
			return cv.visitMethod(access, name, descriptor, signature, exceptions);
		}

		access = applyDeprecated(access, methodMods);

		MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);

		if (mv == null) {
			return null;
		}

		for (AnnotationMods.AddedEntry addedEntry : methodMods.added()) {
			acceptArgs(mv.visitAnnotation(addedEntry.annotationDesc(), addedEntry.visible()), addedEntry.annotationArgs());
		}

		for (AnnotationMods.TypeAddedEntry typeAddedEntry : methodMods.typeAdded()) {
			acceptArgs(mv.visitTypeAnnotation(typeAddedEntry.typeRef(), TypePath.fromString(typeAddedEntry.path()), typeAddedEntry.annotationDesc(), typeAddedEntry.visible()), typeAddedEntry.annotationArgs());
		}

		return new AnnotationModifierMethodVisitor(api, mv, methodMods);
	}

	private static void acceptArgs(@Nullable AnnotationVisitor av, List<Pair<String, Object>> args) {
		if (av == null) {
			return;
		}

		for (Pair<String, Object> arg : args) {
			String name = arg.a;
			Object value = arg.b;

			if (value instanceof ClassValue c) {
				av.visit(name, Type.getType(c.name()));
			} else if (value instanceof EnumValue e) {
				av.visitEnum(name, e.owner(), e.name());
			} else if (value instanceof List<?> list) {
				acceptArray(av.visitArray(name), list);
			} else if (value instanceof AnnotationValue a) {
				acceptArgs(av.visitAnnotation(name, a.desc()), a.args());
			} else {
				av.visit(name, value);
			}
		}

		av.visitEnd();
	}

	private static void acceptArray(@Nullable AnnotationVisitor av, List<?> list) {
		if (av == null) {
			return;
		}

		for (Object value : list) {
			if (value instanceof ClassValue c) {
				av.visit(null, Type.getType(c.name()));
			} else if (value instanceof EnumValue e) {
				av.visitEnum(null, e.owner(), e.name());
			} else if (value instanceof List<?> l) {
				acceptArray(av.visitArray(null), l);
			} else if (value instanceof AnnotationValue a) {
				acceptArgs(av.visitAnnotation(null, a.desc()), a.args());
			} else {
				av.visit(null, value);
			}
		}

		av.visitEnd();
	}

	private static int applyDeprecated(int access, AnnotationMods mods) {
		if (mods.removed().contains("Ljava/lang/Deprecated;")) {
			access &= ~Opcodes.ACC_DEPRECATED;
		}

		if (mods.added().stream().anyMatch(it -> "Ljava/lang/Deprecated;".equals(it.annotationDesc()))) {
			access |= Opcodes.ACC_DEPRECATED;
		}

		return access;
	}

	private static class AnnotationModifierFieldVisitor extends FieldVisitor {
		private final AnnotationMods fieldMods;

		AnnotationModifierFieldVisitor(int api, FieldVisitor fieldVisitor, AnnotationMods fieldMods) {
			super(api, fieldVisitor);
			this.fieldMods = fieldMods;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if (fieldMods.removed().contains(descriptor)) {
				return null;
			}

			return super.visitAnnotation(descriptor, visible);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			if (fieldMods.typeRemoved().contains(new AnnotationMods.TypeRemovedEntry(typeRef, typePath.toString(), descriptor))) {
				return null;
			}

			return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
		}
	}

	private static class AnnotationModifierMethodVisitor extends MethodVisitor {
		private final AnnotationMods methodMods;

		AnnotationModifierMethodVisitor(int api, MethodVisitor methodVisitor, AnnotationMods methodMods) {
			super(api, methodVisitor);
			this.methodMods = methodMods;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			if (methodMods.removed().contains(descriptor)) {
				return null;
			}

			return super.visitAnnotation(descriptor, visible);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			if (methodMods.typeRemoved().contains(new AnnotationMods.TypeRemovedEntry(typeRef, typePath.toString(), descriptor))) {
				return null;
			}

			return super.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
		}
	}
}
