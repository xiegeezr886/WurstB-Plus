import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import com.sun.source.util.JavacTask;

/** Parses .java files only (no type resolution) and reports syntax errors. */
public class ParseCheck
{
	public static void main(String[] args) throws Exception
	{
		List<String> names = new ArrayList<>();
		for(String arg : args)
		{
			if(!arg.startsWith("@"))
			{
				names.add(arg);
				continue;
			}

			for(String line : Files.readAllLines(Path.of(arg.substring(1))))
			{
				line = line.trim();
				if(line.length() >= 2 && line.startsWith("\"")
					&& line.endsWith("\""))
					line = line.substring(1, line.length() - 1);
				if(!line.isEmpty())
					names.add(line);
			}
		}

		boolean preview = names.remove("--preview");

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics =
			new DiagnosticCollector<>();
		StandardJavaFileManager fm =
			compiler.getStandardFileManager(diagnostics, null, null);
		Iterable<? extends JavaFileObject> units =
			fm.getJavaFileObjects(names.toArray(String[]::new));

		List<String> options = new ArrayList<>();
		options.add("-encoding");
		options.add("UTF-8");
		if(preview)
		{
			options.add("--enable-preview");
			options.add("--release");
			options.add("21");
		}

		JavacTask task = (JavacTask)compiler.getTask(null, fm, diagnostics,
			options, null, units);
		task.parse();

		int errors = 0;
		List<Diagnostic<? extends JavaFileObject>> list =
			diagnostics.getDiagnostics();
		for(Diagnostic<? extends JavaFileObject> d : list)
		{
			if(d.getKind() != Diagnostic.Kind.ERROR)
				continue;
			errors++;
			System.out.println(d.getSource().getName() + ":" + d.getLineNumber()
				+ " " + d.getMessage(null));
		}
		System.out.println("FILES=" + names.size() + " PARSE_ERRORS=" + errors);
		System.exit(errors == 0 ? 0 : 1);
	}
}
