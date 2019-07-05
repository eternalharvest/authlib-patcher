import org.gradle.api.Plugin;
import org.gradle.api.Project;

import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.AnsiTextOutput;
import com.strobel.decompiler.languages.BytecodeLanguage;
import com.strobel.decompiler.languages.TypeDecompilationResults;
import com.strobel.decompiler.languages.java.JavaFormattingOptions;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.assembler.metadata.DeobfuscationUtilities;
import com.strobel.assembler.metadata.MetadataParser;
import com.strobel.assembler.metadata.IMetadataResolver;
import com.strobel.assembler.metadata.JarTypeLoader;
import com.strobel.assembler.metadata.CompositeTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.InputTypeLoader;
import com.strobel.core.StringUtilities;
import com.strobel.io.PathHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.FileNotFoundException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.Set;
import java.util.HashSet;


public class DecompilerPlugin implements Plugin<Project> {

	private Project _project;

	@Override
	public void apply(Project project) {
		_project = project;
		_project.getExtensions().create("decompileJava", DecompilerExtension.class);
		_project.task("decompile").doLast(task -> {
			DecompilerExtension.Options settings = ((DecompilerExtension)project.getExtensions().getByName("decompileJava")).options;

			settings.setTypeLoader(new InputTypeLoader());
			settings.setJavaFormattingOptions(JavaFormattingOptions.createDefault());

			final DecompilationOptions decompilationOptions = new DecompilationOptions();
			decompilationOptions.setSettings(settings);
			decompilationOptions.setFullDecompilation(true);
			
			final File file = settings.getInputJar();
			if (!file.exists()) {
				throw new RuntimeException(new FileNotFoundException("File not found: " + file.getPath()));
			}

			final boolean oldShowSyntheticMembers = settings.getShowSyntheticMembers();
			final ITypeLoader oldTypeLoader = settings.getTypeLoader();

			try {
				final JarFile jar = new JarFile(file);
				final Enumeration<JarEntry> entries = jar.entries();

				settings.setShowSyntheticMembers(false);
				settings.setTypeLoader(new CompositeTypeLoader(new JarTypeLoader(jar), oldTypeLoader));

				final MetadataSystem metadataSystem = new NoRetryMetadataSystem(settings.getTypeLoader());


				while (entries.hasMoreElements()) {
					final JarEntry entry = entries.nextElement();
					final String name = entry.getName();

					if (!name.endsWith(".class")) {
						continue;
					}

					final String internalName = StringUtilities.removeRight(name, ".class");
					decompileType(metadataSystem, internalName, decompilationOptions, false);
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			} finally {
				settings.setShowSyntheticMembers(oldShowSyntheticMembers);
				settings.setTypeLoader(oldTypeLoader);
			}
		});
	}

	private void decompileType(
		final MetadataSystem metadataSystem,
		final String typeName,
		final DecompilationOptions options,
		final boolean includeNested) throws IOException {

		final TypeReference type;
		final DecompilerSettings settings = options.getSettings();

		if (typeName.length() == 1) {
			//
			// Hack to get around classes whose descriptors clash with primitive types.
			//

			final MetadataParser parser = new MetadataParser(IMetadataResolver.EMPTY);
			final TypeReference reference = parser.parseTypeDescriptor(typeName);

			type = metadataSystem.resolve(reference);
		} else {
			type = metadataSystem.lookupType(typeName);
		}

		final TypeDefinition resolvedType;

		if (type == null || (resolvedType = type.resolve()) == null) {
			throw new RuntimeException(String.format("!!! ERROR: Failed to load class %s.", typeName));
		}

		DeobfuscationUtilities.processType(resolvedType);

		if (!includeNested && (resolvedType.isNested() || resolvedType.isAnonymous() || resolvedType.isSynthetic())) {
			return;
		}

		final Writer writer = createWriter(resolvedType, settings);
		final boolean writeToFile = writer instanceof FileOutputWriter;
		final PlainTextOutput output;

		if (writeToFile) {
			output = new PlainTextOutput(writer);
		} else {
			output = new AnsiTextOutput(writer, AnsiTextOutput.ColorScheme.LIGHT);
		}

		output.setUnicodeOutputEnabled(settings.isUnicodeOutputEnabled());

		if (settings.getLanguage() instanceof BytecodeLanguage) {
			output.setIndentToken("  ");
		}

		if (writeToFile) {
			System.out.printf("Decompiling %s...\n", typeName);
		}

		final TypeDecompilationResults results = settings.getLanguage().decompileType(resolvedType, output, options);

		writer.flush();

		if (writeToFile) {
			writer.close();
		}
	}


	private Writer createWriter(final TypeDefinition type, final DecompilerSettings settings)
		throws IOException
	{
		if (StringUtilities.isNullOrWhitespace(settings.getOutputDirectory())) {
			return new OutputStreamWriter(System.out, settings.isUnicodeOutputEnabled() ? Charset.forName("UTF-8") : Charset.defaultCharset());
		}

		final String outputDirectory;
		if ((new File(settings.getOutputDirectory())).isAbsolute()) {
			outputDirectory = settings.getOutputDirectory();
		} else {
			outputDirectory = PathHelper.combine(_project.getProjectDir().getPath(), settings.getOutputDirectory());
		}

		final String outputPath;
		final String fileName = type.getName() + settings.getLanguage().getFileExtension();
		final String packageName = type.getPackageName();
		
		if (StringUtilities.isNullOrWhitespace(packageName)) {
			outputPath = PathHelper.combine(outputDirectory, fileName);
		} else {
			outputPath = PathHelper.combine(
				outputDirectory,
				type.getPackageName().replace('.', PathHelper.DirectorySeparator),
				fileName
			);
		}

		final File outputFile = new File(outputPath);
		final File parentFile = outputFile.getParentFile();

		if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs()) {
			throw new IllegalStateException(
				String.format(
					"Could not create output directory for file \"%s\".",
					outputPath
				)
			);
		}

		if (!outputFile.exists() && !outputFile.createNewFile()) {
			throw new IllegalStateException(
				String.format(
					"Could not create output file \"%s\".",
					outputPath
				)
			);
		}

		return new FileOutputWriter(outputFile, settings);
	}
}

final class FileOutputWriter extends OutputStreamWriter {
	public FileOutputWriter(final File file, final DecompilerSettings settings) throws IOException {
		super(new FileOutputStream(file), settings.isUnicodeOutputEnabled() ? Charset.forName("UTF-8") : Charset.defaultCharset());
	}
}

final class NoRetryMetadataSystem extends MetadataSystem {
	private final Set<String> _failedTypes = new HashSet<>();

	NoRetryMetadataSystem() {
	}

	NoRetryMetadataSystem(final ITypeLoader typeLoader) {
		super(typeLoader);
	}

	@Override
	protected TypeDefinition resolveType(final String descriptor, final boolean mightBePrimitive) {
		if (_failedTypes.contains(descriptor)) {
			return null;
		}

		final TypeDefinition result = super.resolveType(descriptor, mightBePrimitive);

		if (result == null) {
			_failedTypes.add(descriptor);
		}

		return result;
	}
}
