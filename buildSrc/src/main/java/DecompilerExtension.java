import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.languages.java.JavaFormattingOptions;
import java.io.File;

public class DecompilerExtension {
	public static class Options extends DecompilerSettings {
	
		private File _inputJar;
	
		public final void setInputJar(File inputJar) {
			_inputJar = inputJar;
		}
	
		public final File getInputJar() {
			return _inputJar;
		}
	
		public static Options javaDefaults() {
			final Options options = new Options();
			options.setJavaFormattingOptions(JavaFormattingOptions.createDefault());
			return options;
		}
	}

	Options options = Options.javaDefaults();
}
