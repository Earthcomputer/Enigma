package cuchaz.enigma.translation.annotations;

import java.util.List;

import com.google.gson.annotations.SerializedName;

import cuchaz.enigma.utils.Pair;

public record AnnotationMods(
		List<String> removed,
		List<AddedEntry> added,
		List<TypeRemovedEntry> typeRemoved,
		List<TypeAddedEntry> typeAdded
) {
	public record AddedEntry(
			String annotationDesc,
			boolean visible,
			List<Pair<String, Object>> annotationArgs
	) {
	}

	public record TypeRemovedEntry(
			int typeRef,
			String path,
			@SerializedName("desc")
			String annotationDesc
	) {
	}

	public record TypeAddedEntry(
			int typeRef,
			String path,
			String annotationDesc,
			boolean visible,
			List<Pair<String, Object>> annotationArgs
	) {
	}
}
