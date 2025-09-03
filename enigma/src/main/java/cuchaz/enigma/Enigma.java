/*******************************************************************************
* Copyright (c) 2015 Jeff Martin.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the GNU Lesser General Public
* License v3.0 which accompanies this distribution, and is available at
* http://www.gnu.org/licenses/lgpl.html
*
* <p>Contributors:
* Jeff Martin - initial API and implementation
******************************************************************************/

package cuchaz.enigma;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import org.objectweb.asm.Opcodes;

import cuchaz.enigma.api.EnigmaPlugin;
import cuchaz.enigma.api.EnigmaPluginContext;
import cuchaz.enigma.api.Ordering;
import cuchaz.enigma.api.service.ClassTransformerService;
import cuchaz.enigma.api.service.EnigmaService;
import cuchaz.enigma.api.service.EnigmaServiceFactory;
import cuchaz.enigma.api.service.EnigmaServiceType;
import cuchaz.enigma.api.service.ProjectService;
import cuchaz.enigma.classprovider.CachingClassProvider;
import cuchaz.enigma.classprovider.ClassProvider;
import cuchaz.enigma.classprovider.CombiningClassProvider;
import cuchaz.enigma.classprovider.JarClassProvider;
import cuchaz.enigma.classprovider.TransformingClassProvider;
import cuchaz.enigma.translation.mapping.serde.MappingFormat;
import cuchaz.enigma.translation.mapping.serde.MappingParseException;
import cuchaz.enigma.utils.I18n;
import cuchaz.enigma.utils.OrderingImpl;
import cuchaz.enigma.utils.Utils;

public class Enigma {
	public static final String NAME = "Enigma";
	public static final String VERSION;
	public static final String URL = "https://fabricmc.net";
	public static final int ASM_VERSION = Opcodes.ASM9;

	private final EnigmaProfile profile;
	private final EnigmaServices services;

	private Enigma(EnigmaProfile profile, EnigmaServices services) {
		this.profile = profile;
		this.services = services;
	}

	public static Enigma create() {
		return new Builder().build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public EnigmaProject openJar(Path path, ClassProvider libraryClassProvider, ProgressListener progress) throws IOException {
		return openJars(List.of(path), libraryClassProvider, progress);
	}

	public EnigmaProject openJars(List<Path> paths, ClassProvider libraryClassProvider, ProgressListener progress) throws IOException {
		try {
			return openJarsAndMappings(paths, libraryClassProvider, null, null, progress);
		} catch (MappingParseException e) {
			throw new AssertionError("No mappings, so no parse exception should happen", e);
		}
	}

	public EnigmaProject openJarsAndMappings(
			List<Path> paths,
			ClassProvider libraryClassProvider,
			MappingFormat mappingFormat,
			Path mappingsPath,
			ProgressListener progress
	) throws IOException, MappingParseException {
		return openJarsAndMappings(paths, libraryClassProvider, mappingFormat, mappingsPath, progress, true);
	}

	public EnigmaProject openJarsAndMappings(
			List<Path> paths,
			ClassProvider libraryClassProvider,
			MappingFormat mappingFormat,
			Path mappingsPath,
			ProgressListener progress,
			boolean callServices
	) throws IOException, MappingParseException {
		ClassProvider jarClassProvider = getJarClassProvider(paths);
		ClassProvider allClassesProvider = new CombiningClassProvider(jarClassProvider, libraryClassProvider);

		ClassProvider classProvider = new CachingClassProvider(allClassesProvider);

		EnigmaProject project = new EnigmaProject(this, paths, classProvider, jarClassProvider.getClassNames(), Utils.zipSha1(paths.toArray(new Path[0])));
		IOException[] mappingsIOException = { null };
		MappingParseException[] mappingsParseException = { null };
		project.invalidateClasses(reducedIndex -> {
			if (mappingsPath != null) {
				try {
					project.setMappings(mappingFormat.read(mappingsPath, progress.fork(), profile.getMappingSaveParameters(), reducedIndex), reducedIndex);
				} catch (IOException e) {
					mappingsIOException[0] = e;
					return allClassesProvider;
				} catch (MappingParseException e) {
					mappingsParseException[0] = e;
					return allClassesProvider;
				}
			}

			ClassProvider transformedProvider = allClassesProvider;

			for (ClassTransformerService transformerService : services.get(ClassTransformerService.TYPE)) {
				transformedProvider = new TransformingClassProvider(transformedProvider, transformerService, project.getMapper());
			}

			return transformedProvider;
		}, progress, Runnable::run);

		if (mappingsIOException[0] != null) {
			throw mappingsIOException[0];
		} else if (mappingsParseException[0] != null) {
			throw mappingsParseException[0];
		}

		if (callServices) {
			for (ProjectService projectService : services.get(ProjectService.TYPE)) {
				projectService.onProjectOpen(project);
			}
		}

		return project;
	}

	private ClassProvider getJarClassProvider(List<Path> jars) throws IOException {
		if (jars.size() == 1) {
			return new JarClassProvider(jars.get(0));
		}

		var classProviders = new ClassProvider[jars.size()];

		for (int i = 0; i < jars.size(); i++) {
			classProviders[i] = new JarClassProvider(jars.get(i));
		}

		return new CombiningClassProvider(classProviders);
	}

	public EnigmaProfile getProfile() {
		return profile;
	}

	public EnigmaServices getServices() {
		return services;
	}

	public static class Builder {
		private EnigmaProfile profile = EnigmaProfile.EMPTY;

		private Builder() {
		}

		public Builder setProfile(EnigmaProfile profile) {
			Preconditions.checkNotNull(profile, "profile cannot be null");
			this.profile = profile;
			return this;
		}

		public Enigma build() {
			PluginContext pluginContext = new PluginContext();

			ServiceLoader.load(EnigmaPlugin.class).stream()
					.filter(plugin -> !profile.getDisabledPlugins().contains(plugin.type().getName()))
					.forEach(plugin -> plugin.get().init(pluginContext));

			EnigmaServices services = pluginContext.buildServices();

			I18n.initialize(services);

			return new Enigma(profile, services);
		}
	}

	private static class PluginContext implements EnigmaPluginContext {
		private final Map<EnigmaServiceType<?>, PendingServices<?>> pendingServices = new HashMap<>();

		@Override
		public <T extends EnigmaService> void registerService(String id, EnigmaServiceType<T> serviceType, EnigmaServiceFactory<T> factory, Ordering... ordering) {
			@SuppressWarnings("unchecked")
			PendingServices<T> pending = (PendingServices<T>) pendingServices.computeIfAbsent(serviceType, k -> new PendingServices<>());
			pending.factories.put(id, factory);
			pending.orderings.put(id, Arrays.asList(ordering));
		}

		@Override
		public void disableService(String id, EnigmaServiceType<?> serviceType) {
			pendingServices.computeIfAbsent(serviceType, k -> new PendingServices<>()).disabled.add(id);
		}

		EnigmaServices buildServices() {
			ImmutableListMultimap.Builder<EnigmaServiceType<?>, EnigmaService> services = ImmutableListMultimap.builder();

			pendingServices.forEach((serviceType, pending) -> {
				pending.orderings.keySet().removeAll(pending.disabled);
				List<String> orderedServices = OrderingImpl.sort(serviceType.key, pending.orderings);
				orderedServices.forEach(serviceId -> {
					services.put(serviceType, pending.factories.get(serviceId).create());
				});
			});

			return new EnigmaServices(services.build());
		}

		private record PendingServices<T extends EnigmaService>(
				Map<String, EnigmaServiceFactory<T>> factories,
				Map<String, List<Ordering>> orderings,
				Set<String> disabled
		) {
			PendingServices() {
				this(new HashMap<>(), new LinkedHashMap<>(), new HashSet<>());
			}
		}
	}

	static {
		String version = null;

		try {
			version = Utils.readResourceToString("/version.txt");
		} catch (Throwable t) {
			version = "Unknown Version";
		}

		VERSION = version;
	}
}
