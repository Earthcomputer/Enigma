package cuchaz.enigma.translation.annotations;

import java.util.List;

import cuchaz.enigma.utils.Pair;

public record AnnotationValue(String desc, List<Pair<String, Object>> args) {
}
