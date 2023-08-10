package cuchaz.enigma.translation.annotations;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import cuchaz.enigma.translation.mapping.tree.EntryTree;
import cuchaz.enigma.translation.mapping.tree.EntryTreeNode;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.TypeDescriptor;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.Entry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.utils.Pair;

public final class AnnotationModsIo {
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.registerTypeAdapter(AnnotationMods.class, new AnnotationModsSerializer())
			.registerTypeAdapter(AnnotationMods.AddedEntry.class, new AddedEntrySerializer())
			.registerTypeAdapter(AnnotationMods.TypeAddedEntry.class, new TypeAddedEntrySerializer())
			.create();

	private AnnotationModsIo() {
	}

	public static void toJsonDirectory(Path dir, EntryTree<AnnotationMods> tree) throws IOException {
		List<IOException> exceptions = new ArrayList<>();

		tree.getRootNodes().parallel().forEach(node -> {
			String[] parts = node.getEntry().getName().split("\\.");
			Path jsonFile = dir;

			for (int i = 0; i < parts.length - 1; i++) {
				jsonFile = jsonFile.resolve(parts[i]);
			}

			try {
				Files.createDirectories(jsonFile);

				jsonFile = jsonFile.resolve(parts[parts.length - 1] + ".json");

				try (BufferedWriter writer = Files.newBufferedWriter(jsonFile)) {
					GSON.toJson(toJson(node), writer);
				}
			} catch (IOException e) {
				exceptions.add(e);
			}
		});

		if (!exceptions.isEmpty()) {
			if (exceptions.size() == 1) {
				throw exceptions.get(0);
			} else {
				exceptions.forEach(IOException::printStackTrace);
				throw new IOException(exceptions.size() + " errors occurred writing annotations. See log for details.");
			}
		}
	}

	private static JsonObject toJson(EntryTreeNode<AnnotationMods> node) {
		JsonObject result = new JsonObject();
		Entry<?> entry = node.getEntry();

		if (entry instanceof ClassEntry classEntry) {
			result.addProperty("type", "class");
			result.addProperty("name", classEntry.getName());
		} else if (entry instanceof FieldEntry fieldEntry) {
			result.addProperty("type", "field");
			result.addProperty("name", fieldEntry.getName());
			result.addProperty("desc", fieldEntry.getDesc().toString());
		} else if (entry instanceof MethodEntry methodEntry) {
			result.addProperty("type", "method");
			result.addProperty("name", methodEntry.getName());
			result.addProperty("desc", methodEntry.getDesc().toString());
		} else {
			throw new IllegalArgumentException("Illegal entry type in annotation modifier tree: " + entry.getClass().getName());
		}

		AnnotationMods mods = node.getValue();

		if (mods != null) {
			result.add("mods", GSON.toJsonTree(mods));
		}

		Collection<? extends EntryTreeNode<AnnotationMods>> children = node.getChildNodes();

		if (!children.isEmpty()) {
			JsonArray childrenArray = new JsonArray(children.size());

			for (EntryTreeNode<AnnotationMods> child : children) {
				childrenArray.add(toJson(child));
			}

			result.add("children", childrenArray);
		}

		return result;
	}

	public static void fromJsonDirectory(Path dir, EntryTree<AnnotationMods> tree) throws Exception {
		List<Exception> exceptions = new ArrayList<>();

		try (Stream<Path> files = Files.walk(dir)) {
			files.parallel().forEach(file -> {
				if (file.getFileName().toString().endsWith(".json")) {
					try (BufferedReader reader = Files.newBufferedReader(file)) {
						fromJson(GSON.fromJson(reader, JsonObject.class), tree, null);
					} catch (Exception e) {
						exceptions.add(e);
					}
				}
			});
		}

		if (!exceptions.isEmpty()) {
			if (exceptions.size() == 1) {
				throw exceptions.get(0);
			} else {
				exceptions.forEach(Exception::printStackTrace);
				throw new IOException(exceptions.size() + " errors occurred reading annotations. See log for details.");
			}
		}
	}

	private static void fromJson(JsonObject json, EntryTree<AnnotationMods> tree, @Nullable ClassEntry parent) throws JsonParseException {
		String type = json.get("type").getAsString();
		Entry<?> entry = switch (type) {
		case "class" -> new ClassEntry(parent, json.get("name").getAsString());
		case "field" -> new FieldEntry(parent, json.get("name").getAsString(), new TypeDescriptor(json.get("desc").getAsString()));
		case "method" -> new MethodEntry(parent, json.get("name").getAsString(), new MethodDescriptor(json.get("desc").getAsString()));
		default -> throw new JsonParseException("Unknown entry type " + type);
		};

		JsonArray children;

		if (entry instanceof ClassEntry classEntry && (children = json.getAsJsonArray("children")) != null) {
			for (JsonElement child : children) {
				fromJson(child.getAsJsonObject(), tree, classEntry);
			}
		}

		JsonElement mods;

		if ((mods = json.get("mods")) != null) {
			tree.insert(entry, GSON.fromJson(mods, AnnotationMods.class));
		}
	}

	private static List<Pair<String, Object>> deserializeArgs(JsonObject json) throws JsonParseException {
		List<Pair<String, Object>> result = new ArrayList<>(json.size());

		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			result.add(new Pair<>(entry.getKey(), deserializeValue(entry.getValue().getAsJsonObject())));
		}

		return result;
	}

	private static JsonObject serializeArgs(List<Pair<String, Object>> args) {
		JsonObject result = new JsonObject();

		for (Pair<String, Object> entry : args) {
			result.add(entry.a, serializeValue(entry));
		}

		return result;
	}

	private static JsonObject serializeValue(Object annotationValue) {
		JsonObject value = new JsonObject();

		if (annotationValue instanceof Number n) {
			value.addProperty("value", n);

			if (annotationValue instanceof Byte) {
				value.addProperty("type", "byte");
			} else if (annotationValue instanceof Short) {
				value.addProperty("type", "short");
			} else if (annotationValue instanceof Integer) {
				value.addProperty("type", "int");
			} else if (annotationValue instanceof Long) {
				value.addProperty("type", "long");
			} else if (annotationValue instanceof Float) {
				value.addProperty("type", "float");
			} else if (annotationValue instanceof Double) {
				value.addProperty("type", "double");
			} else {
				throw new IllegalArgumentException("Illegal annotation value type " + annotationValue.getClass().getName());
			}
		} else if (annotationValue instanceof Boolean b) {
			value.addProperty("value", b);
			value.addProperty("type", "boolean");
		} else if (annotationValue instanceof Character c) {
			value.addProperty("value", c);
			value.addProperty("type", "char");
		} else if (annotationValue instanceof String s) {
			value.addProperty("value", s);
			value.addProperty("type", "string");
		} else if (annotationValue instanceof ClassValue c) {
			value.addProperty("value", c.name());
			value.addProperty("type", "class");
		} else if (annotationValue instanceof EnumValue e) {
			JsonArray array = new JsonArray(2);
			array.add(e.owner());
			array.add(e.name());
			value.add("value", array);
			value.addProperty("type", "enum");
		} else if (annotationValue instanceof AnnotationValue a) {
			value.addProperty("type", "annotation");
			value.addProperty("desc", a.desc());

			if (!a.args().isEmpty()) {
				value.add("args", serializeArgs(a.args()));
			}
		} else if (annotationValue instanceof List<?> l) {
			value.addProperty("type", "array");
			JsonArray array = new JsonArray(l.size());

			for (Object o : l) {
				array.add(serializeValue(o));
			}

			value.add("value", array);
		}

		return value;
	}

	private static Object deserializeValue(JsonObject value) throws JsonParseException {
		String type = value.get("type").getAsString();
		return switch (type) {
		case "byte" -> value.get("value").getAsByte();
		case "short" -> value.get("value").getAsShort();
		case "int" -> value.get("value").getAsInt();
		case "long" -> value.get("value").getAsLong();
		case "float" -> value.get("value").getAsFloat();
		case "double" -> value.get("value").getAsDouble();
		case "boolean" -> value.get("value").getAsBoolean();
		case "char" -> value.get("value").getAsString().charAt(0);
		case "string" -> value.get("value").getAsString();
		case "class" -> new ClassValue(value.get("value").getAsString());
		case "enum" -> {
			JsonArray array = value.getAsJsonArray("value");

			if (array.size() != 2) {
				throw new JsonParseException("Enum value does not have size 2");
			}

			yield new EnumValue(array.get(0).getAsString(), array.get(1).getAsString());
		}
		case "annotation" -> {
			String desc = value.get("desc").getAsString();
			JsonElement argsElement = value.get("args");
			List<Pair<String, Object>> args = argsElement == null ? Collections.emptyList() : deserializeArgs(argsElement.getAsJsonObject());
			yield new AnnotationValue(desc, args);
		}
		case "array" -> {
			JsonArray array = value.getAsJsonArray("value");
			List<Object> list = new ArrayList<>(array.size());

			for (JsonElement element : array) {
				list.add(deserializeValue(element.getAsJsonObject()));
			}

			yield list;
		}
		default -> throw new JsonParseException("Unknown annotation value type " + type);
		};
	}

	private static final class AnnotationModsSerializer implements JsonDeserializer<AnnotationMods>, JsonSerializer<AnnotationMods> {
		private static final Type STRING_LIST = new TypeToken<List<String>>() { }.getType();
		private static final Type ADDED_LIST = new TypeToken<List<AnnotationMods.AddedEntry>>() { }.getType();
		private static final Type TYPE_REMOVED_LIST = new TypeToken<List<AnnotationMods.TypeRemovedEntry>>() { }.getType();
		private static final Type TYPE_ADDED_LIST = new TypeToken<List<AnnotationMods.TypeAddedEntry>>() { }.getType();

		@Override
		public AnnotationMods deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();
			JsonElement removedElt = obj.get("removed");
			List<String> removed = removedElt == null ? Collections.emptyList() : context.deserialize(removedElt, STRING_LIST);
			JsonElement addedElt = obj.get("added");
			List<AnnotationMods.AddedEntry> added = addedElt == null ? Collections.emptyList() : context.deserialize(addedElt, ADDED_LIST);
			JsonElement typeRemovedElt = obj.get("typeRemoved");
			List<AnnotationMods.TypeRemovedEntry> typeRemoved = typeRemovedElt == null ? Collections.emptyList() : context.deserialize(typeRemovedElt, TYPE_REMOVED_LIST);
			JsonElement typeAddedElt = obj.get("typeAdded");
			List<AnnotationMods.TypeAddedEntry> typeAdded = typeAddedElt == null ? Collections.emptyList() : context.deserialize(typeAddedElt, TYPE_ADDED_LIST);
			return new AnnotationMods(removed, added, typeRemoved, typeAdded);
		}

		@Override
		public JsonElement serialize(AnnotationMods src, Type typeOfSrc, JsonSerializationContext context) {
			JsonObject obj = new JsonObject();

			if (!src.removed().isEmpty()) {
				obj.add("removed", context.serialize(src.removed(), STRING_LIST));
			}

			if (!src.added().isEmpty()) {
				obj.add("added", context.serialize(src.added(), ADDED_LIST));
			}

			if (!src.typeRemoved().isEmpty()) {
				obj.add("typeRemoved", context.serialize(src.typeRemoved(), TYPE_REMOVED_LIST));
			}

			if (!src.typeAdded().isEmpty()) {
				obj.add("typeAdded", context.serialize(src.typeAdded(), TYPE_ADDED_LIST));
			}

			return obj;
		}
	}

	private static final class AddedEntrySerializer implements JsonDeserializer<AnnotationMods.AddedEntry>, JsonSerializer<AnnotationMods.AddedEntry> {
		@Override
		public AnnotationMods.AddedEntry deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();
			String desc = obj.get("desc").getAsString();
			boolean visible = obj.get("visible").getAsBoolean();
			JsonElement argsElt = obj.get("args");
			List<Pair<String, Object>> args = argsElt == null ? Collections.emptyList() : deserializeArgs(argsElt.getAsJsonObject());
			return new AnnotationMods.AddedEntry(desc, visible, args);
		}

		@Override
		public JsonElement serialize(AnnotationMods.AddedEntry src, Type typeOfSrc, JsonSerializationContext context) {
			JsonObject obj = new JsonObject();
			obj.addProperty("desc", src.annotationDesc());
			obj.addProperty("visible", src.visible());

			if (!src.annotationArgs().isEmpty()) {
				obj.add("args", serializeArgs(src.annotationArgs()));
			}

			return obj;
		}
	}

	private static final class TypeAddedEntrySerializer implements JsonDeserializer<AnnotationMods.TypeAddedEntry>, JsonSerializer<AnnotationMods.TypeAddedEntry> {
		@Override
		public AnnotationMods.TypeAddedEntry deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();
			int typeRef = obj.get("typeRef").getAsInt();
			String path = obj.get("path").getAsString();
			String desc = obj.get("desc").getAsString();
			boolean visible = obj.get("visible").getAsBoolean();
			JsonElement argsElt = obj.get("args");
			List<Pair<String, Object>> args = argsElt == null ? Collections.emptyList() : deserializeArgs(argsElt.getAsJsonObject());
			return new AnnotationMods.TypeAddedEntry(typeRef, path, desc, visible, args);
		}

		@Override
		public JsonElement serialize(AnnotationMods.TypeAddedEntry src, Type typeOfSrc, JsonSerializationContext context) {
			JsonObject obj = new JsonObject();
			obj.addProperty("typeRef", src.typeRef());
			obj.addProperty("path", src.path());
			obj.addProperty("desc", src.annotationDesc());
			obj.addProperty("visible", src.visible());

			if (!src.annotationArgs().isEmpty()) {
				obj.add("args", serializeArgs(src.annotationArgs()));
			}

			return obj;
		}
	}
}
