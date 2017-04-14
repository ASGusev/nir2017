import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        Command requestedCommand = Command.valueOf(args[0]);
        requestedCommand.exec(args);
    }

    private enum Command {
        annotate {
            @Override
            protected void exec(String[] args) {
                DeconvolutionProgram format = DeconvolutionProgram.valueOf(args[1]);
                Path deconvolutionResultsPath = Paths.get(args[2]);
                Path theoreticScansTablePath = Paths.get(args[3]);
                Path outputPath = Paths.get(args[4]);

                try {
                    Iterator<ExperimentalScan> outputIterator =
                            format.getOutputIterator(deconvolutionResultsPath);
                    Map<Integer, TheoreticScan> theoreticScans =
                            TheoreticScan.mapFromTable(theoreticScansTablePath);
                    Analyzer.annotate(outputIterator, theoreticScans, outputPath);
                } catch (IOException e) {
                    System.out.println("File read/write error.");
                }
            }
        },

        count {
            @Override
            protected void exec(String[] args) {
                int pos = 1;
                List<Predicate<TheoreticScan>> filters = new ArrayList<>();

                while (args[pos].startsWith("-")) {
                    switch (args[pos++]) {
                        case "-evalue_under": {
                            double value = Double.valueOf(args[pos++]);
                            filters.add(scan -> scan.getEValue() < value);
                            break;
                        }
                        case "-evalue_over": {
                            double value = Double.valueOf(args[pos++]);
                            filters.add(scan -> scan.getEValue() > value);
                            break;
                        }
                        case "-modified": {
                            filters.add(scan -> scan.getStringSequence().contains("("));
                            break;
                        }
                        case "-not_modified": {
                            filters.add(scan -> !scan.getStringSequence().contains("("));
                            break;
                        }
                    }
                }
                for (; pos < args.length; pos++) {
                    Path file = Paths.get(args[pos]);
                    try {
                        Stream<TheoreticScan> scans = TheoreticScan.readTable(file);
                        for (Predicate<TheoreticScan> filter : filters) {
                            scans = scans.filter(filter);
                        }
                        System.out.println(scans.count());
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.out.println("File reading error.");
                    }
                }
            }
        };

        protected abstract void exec(String[] args);
    }
}