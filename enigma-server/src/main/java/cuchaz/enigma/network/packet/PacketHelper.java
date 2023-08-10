package cuchaz.enigma.network.packet;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cuchaz.enigma.translation.annotations.AnnotationMods;
import cuchaz.enigma.translation.annotations.AnnotationValue;
import cuchaz.enigma.translation.annotations.ClassValue;
import cuchaz.enigma.translation.annotations.EnumValue;
import cuchaz.enigma.translation.mapping.AccessModifier;
import cuchaz.enigma.translation.mapping.EntryChange;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.LocalVariableEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.Pair;
import cuchaz.enigma.utils.TristateChange;

public class PacketHelper {
	private static final int ENTRY_CLASS = 0, ENTRY_FIELD = 1, ENTRY_METHOD = 2, ENTRY_LOCAL_VAR = 3;
	private static final int MAX_STRING_LENGTH = 65535;

	private static final int TYPE_BYTE = 0, TYPE_SHORT = 1, TYPE_INT = 2, TYPE_LONG = 3, TYPE_FLOAT = 4, TYPE_DOUBLE = 5,
			TYPE_BOOLEAN = 6, TYPE_CHAR = 7, TYPE_STRING = 8, TYPE_CLASS = 9, TYPE_ENUM = 10, TYPE_ANNOTATION = 11, TYPE_ARRAY = 12;

	public static Entry<?> readEntry(DataInput input) throws IOException {
		return readEntry(input, null, true);
	}

	public static Entry<?> readEntry(DataInput input, Entry<?> parent, boolean includeParent) throws IOException {
		int type = input.readUnsignedByte();

		if (includeParent && input.readBoolean()) {
			parent = readEntry(input, null, true);
		}

		String name = readString(input);

		String javadocs = null;

		if (input.readBoolean()) {
			javadocs = readString(input);
		}

		switch (type) {
		case ENTRY_CLASS: {
			if (parent != null && !(parent instanceof ClassEntry)) {
				throw new IOException("Class requires class parent");
			}

			return new ClassEntry((ClassEntry) parent, name, javadocs);
		}
		case ENTRY_FIELD: {
			if (!(parent instanceof ClassEntry parentClass)) {
				throw new IOException("Field requires class parent");
			}

			TypeDescriptor desc = new TypeDescriptor(readString(input));
			return new FieldEntry(parentClass, name, desc, javadocs);
		}
		case ENTRY_METHOD: {
			if (!(parent instanceof ClassEntry parentClass)) {
				throw new IOException("Method requires class parent");
			}

			MethodDescriptor desc = new MethodDescriptor(readString(input));
			return new MethodEntry(parentClass, name, desc, javadocs);
		}
		case ENTRY_LOCAL_VAR: {
			if (!(parent instanceof MethodEntry parentMethod)) {
				throw new IOException("Local variable requires method parent");
			}

			int index = input.readUnsignedShort();
			boolean parameter = input.readBoolean();
			return new LocalVariableEntry(parentMethod, index, name, parameter, javadocs);
		}
		default:
			throw new IOException("Received unknown entry type " + type);
		}
	}

	public static void writeEntry(DataOutput output, Entry<?> entry) throws IOException {
		writeEntry(output, entry, true);
	}

	public static void writeEntry(DataOutput output, Entry<?> entry, boolean includeParent) throws IOException {
		// type
		if (entry instanceof ClassEntry) {
			output.writeByte(ENTRY_CLASS);
		} else if (entry instanceof FieldEntry) {
			output.writeByte(ENTRY_FIELD);
		} else if (entry instanceof MethodEntry) {
			output.writeByte(ENTRY_METHOD);
		} else if (entry instanceof LocalVariableEntry) {
			output.writeByte(ENTRY_LOCAL_VAR);
		} else {
			throw new IOException("Don't know how to serialize entry of type " + entry.getClass().getSimpleName());
		}

		// parent
		if (includeParent) {
			output.writeBoolean(entry.getParent() != null);

			if (entry.getParent() != null) {
				writeEntry(output, entry.getParent(), true);
			}
		}

		// name
		writeString(output, entry.getName());

		// javadocs
		output.writeBoolean(entry.getJavadocs() != null);

		if (entry.getJavadocs() != null) {
			writeString(output, entry.getJavadocs());
		}

		// type-specific stuff
		if (entry instanceof FieldEntry) {
			writeString(output, ((FieldEntry) entry).getDesc().toString());
		} else if (entry instanceof MethodEntry) {
			writeString(output, ((MethodEntry) entry).getDesc().toString());
		} else if (entry instanceof LocalVariableEntry) {
			LocalVariableEntry localVar = (LocalVariableEntry) entry;
			output.writeShort(localVar.getIndex());
			output.writeBoolean(localVar.isArgument());
		}
	}

	public static String readString(DataInput input) throws IOException {
		int length = input.readUnsignedShort();
		byte[] bytes = new byte[length];
		input.readFully(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	public static void writeString(DataOutput output, String str) throws IOException {
		byte[] bytes = str.getBytes(StandardCharsets.UTF_8);

		if (bytes.length > MAX_STRING_LENGTH) {
			throw new IOException("String too long, was " + bytes.length + " bytes, max " + MAX_STRING_LENGTH + " allowed");
		}

		output.writeShort(bytes.length);
		output.write(bytes);
	}

	public static EntryChange<?> readEntryChange(DataInput input) throws IOException {
		Entry<?> e = readEntry(input);
		EntryChange<?> change = EntryChange.modify(e);

		int flags = input.readUnsignedByte();
		TristateChange.Type deobfNameT = TristateChange.Type.values()[flags & 0x3];
		TristateChange.Type accessT = TristateChange.Type.values()[flags >> 2 & 0x3];
		TristateChange.Type javadocT = TristateChange.Type.values()[flags >> 4 & 0x3];

		switch (deobfNameT) {
		case RESET:
			change = change.clearDeobfName();
			break;
		case SET:
			change = change.withDeobfName(readString(input));
			break;
		}

		switch (accessT) {
		case RESET:
			change = change.clearAccess();
			break;
		case SET:
			change = change.withAccess(AccessModifier.values()[flags >> 6 & 0x3]);
			break;
		}

		switch (javadocT) {
		case RESET:
			change = change.clearJavadoc();
			break;
		case SET:
			change = change.withJavadoc(readString(input));
			break;
		}

		return change;
	}

	public static void writeEntryChange(DataOutput output, EntryChange<?> change) throws IOException {
		writeEntry(output, change.getTarget());
		int flags = change.getDeobfName().getType().ordinal() | change.getAccess().getType().ordinal() << 2 | change.getJavadoc().getType().ordinal() << 4;

		if (change.getAccess().isSet()) {
			flags |= change.getAccess().getNewValue().ordinal() << 6;
		}

		output.writeByte(flags);

		if (change.getDeobfName().isSet()) {
			writeString(output, change.getDeobfName().getNewValue());
		}

		if (change.getJavadoc().isSet()) {
			writeString(output, change.getJavadoc().getNewValue());
		}
	}

	private static AnnotationMods readAnnotationMods(DataInput input) throws IOException {
		int numRemoved = input.readUnsignedByte();
		List<String> removed = new ArrayList<>(numRemoved);

		for (int i = 0; i < numRemoved; i++) {
			removed.add(readString(input));
		}

		int numAdded = input.readUnsignedByte();
		List<AnnotationMods.AddedEntry> added = new ArrayList<>(numAdded);

		for (int i = 0; i < numAdded; i++) {
			String desc = readString(input);
			boolean visible = input.readBoolean();
			int numArgs = input.readUnsignedByte();
			List<Pair<String, Object>> args = new ArrayList<>(numArgs);

			for (int j = 0; j < numArgs; j++) {
				args.add(new Pair<>(readString(input), readAnnotationValue(input)));
			}

			added.add(new AnnotationMods.AddedEntry(desc, visible, args));
		}

		int numTypeRemoved = input.readUnsignedByte();
		List<AnnotationMods.TypeRemovedEntry> typeRemoved = new ArrayList<>(numTypeRemoved);

		for (int i = 0; i < numTypeRemoved; i++) {
			typeRemoved.add(new AnnotationMods.TypeRemovedEntry(input.readUnsignedByte(), readString(input), readString(input)));
		}

		int numTypeAdded = input.readUnsignedByte();
		List<AnnotationMods.TypeAddedEntry> typeAdded = new ArrayList<>(numTypeAdded);

		for (int i = 0; i < numTypeAdded; i++) {
			int typeRef = input.readUnsignedByte();
			String path = readString(input);
			String desc = readString(input);
			boolean visible = input.readBoolean();
			int numArgs = input.readUnsignedByte();
			List<Pair<String, Object>> args = new ArrayList<>(numArgs);

			for (int j = 0; j < numArgs; j++) {
				args.add(new Pair<>(readString(input), readAnnotationValue(input)));
			}

			typeAdded.add(new AnnotationMods.TypeAddedEntry(typeRef, path, desc, visible, args));
		}

		return new AnnotationMods(removed, added, typeRemoved, typeAdded);
	}

	private static void writeAnnotationMods(DataOutput output, AnnotationMods mods) throws IOException {
		int numRemoved = Math.min(0xff, mods.removed().size());
		output.writeByte(numRemoved);

		for (int i = 0; i < numRemoved; i++) {
			writeString(output, mods.removed().get(i));
		}

		int numAdded = Math.min(0xff, mods.added().size());
		output.writeByte(numAdded);

		for (int i = 0; i < numAdded; i++) {
			writeString(output, mods.added().get(i).annotationDesc());
			output.writeBoolean(mods.added().get(i).visible());
			List<Pair<String, Object>> args = mods.added().get(i).annotationArgs();
			int numArgs = Math.min(0xff, args.size());
			output.writeByte(numArgs);

			for (int j = 0; j < numArgs; j++) {
				writeString(output, args.get(j).a);
				writeAnnotationValue(output, args.get(j).b);
			}
		}

		int numTypeRemoved = Math.min(0xff, mods.typeRemoved().size());
		output.writeByte(numTypeRemoved);

		for (int i = 0; i < numTypeRemoved; i++) {
			output.writeByte(mods.typeRemoved().get(i).typeRef());
			writeString(output, mods.typeRemoved().get(i).path());
			writeString(output, mods.typeRemoved().get(i).annotationDesc());
		}

		int numTypeAdded = Math.min(0xff, mods.typeAdded().size());
		output.writeByte(numTypeAdded);

		for (int i = 0; i < numTypeAdded; i++) {
			output.writeByte(mods.typeAdded().get(i).typeRef());
			writeString(output, mods.typeAdded().get(i).path());
			writeString(output, mods.typeAdded().get(i).annotationDesc());
			output.writeBoolean(mods.typeAdded().get(i).visible());
			List<Pair<String, Object>> args = mods.typeAdded().get(i).annotationArgs();
			int numArgs = Math.min(0xff, args.size());
			output.writeByte(numArgs);

			for (int j = 0; j < numArgs; j++) {
				writeString(output, args.get(j).a);
				writeAnnotationValue(output, args.get(j).b);
			}
		}
	}

	private static Object readAnnotationValue(DataInput input) throws IOException {
		int type = input.readUnsignedByte();
		return switch (type) {
		case TYPE_BYTE -> input.readByte();
		case TYPE_SHORT -> input.readShort();
		case TYPE_INT -> input.readInt();
		case TYPE_LONG -> input.readLong();
		case TYPE_FLOAT -> input.readFloat();
		case TYPE_DOUBLE -> input.readDouble();
		case TYPE_BOOLEAN -> input.readBoolean();
		case TYPE_CHAR -> input.readChar();
		case TYPE_STRING -> readString(input);
		case TYPE_CLASS -> new ClassValue(readString(input));
		case TYPE_ENUM -> new EnumValue(readString(input), readString(input));
		case TYPE_ANNOTATION -> {
			String desc = readString(input);
			int argCount = input.readUnsignedByte();
			List<Pair<String, Object>> args = new ArrayList<>(argCount);

			for (int i = 0; i < argCount; i++) {
				args.add(new Pair<>(readString(input), readAnnotationValue(input)));
			}

			yield new AnnotationValue(desc, args);
		}
		case TYPE_ARRAY -> {
			int length = input.readUnsignedByte();
			List<Object> list = new ArrayList<>(length);

			for (int i = 0; i < length; i++) {
				list.add(readAnnotationValue(input));
			}

			yield list;
		}
		default -> throw new IOException("Unknown annotation value type " + type);
		};
	}

	private static void writeAnnotationValue(DataOutput output, Object value) throws IOException {
		if (value instanceof Number) {
			if (value instanceof Byte b) {
				output.writeByte(TYPE_BYTE);
				output.writeByte(b);
			} else if (value instanceof Short s) {
				output.writeByte(TYPE_SHORT);
				output.writeShort(s);
			} else if (value instanceof Integer i) {
				output.writeByte(TYPE_INT);
				output.writeInt(i);
			} else if (value instanceof Long l) {
				output.writeByte(TYPE_LONG);
				output.writeLong(l);
			} else if (value instanceof Float f) {
				output.writeByte(TYPE_FLOAT);
				output.writeFloat(f);
			} else if (value instanceof Double d) {
				output.writeByte(TYPE_DOUBLE);
				output.writeDouble(d);
			} else {
				throw new IOException("Unknown annotation value class " + value.getClass().getName());
			}
		} else if (value instanceof Boolean b) {
			output.writeByte(TYPE_BOOLEAN);
			output.writeBoolean(b);
		} else if (value instanceof Character c) {
			output.writeByte(TYPE_CHAR);
			output.writeChar(c);
		} else if (value instanceof String s) {
			output.writeByte(TYPE_STRING);
			writeString(output, s);
		} else if (value instanceof ClassValue c) {
			output.writeByte(TYPE_CLASS);
			writeString(output, c.name());
		} else if (value instanceof EnumValue e) {
			output.writeByte(TYPE_ENUM);
			writeString(output, e.owner());
			writeString(output, e.name());
		} else if (value instanceof AnnotationValue a) {
			output.writeByte(TYPE_ANNOTATION);
			writeString(output, a.desc());
			int argCount = Math.min(0xff, a.args().size());
			output.writeByte(argCount);

			for (int i = 0; i < argCount; i++) {
				writeString(output, a.args().get(i).a);
				writeAnnotationValue(output, a.args().get(i).b);
			}
		} else if (value instanceof List<?> l) {
			output.writeByte(TYPE_ARRAY);
			int length = Math.min(0xff, l.size());
			output.writeByte(length);

			for (int i = 0; i < length; i++) {
				writeAnnotationValue(output, l.get(i));
			}
		} else {
			throw new IOException("Unknown annotation value class " + value.getClass().getName());
		}
	}
}
