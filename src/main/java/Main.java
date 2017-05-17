import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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
                double maxEValue = Double.valueOf(args[5]);

                try {
                    Iterator<ExperimentalScan> outputIterator =
                            format.getOutputIterator(deconvolutionResultsPath);
                    Map<Integer, TheoreticScan> theoreticScans =
                            TheoreticScan.mapFromTable(theoreticScansTablePath);
                    Analyzer.annotate(outputIterator, theoreticScans,
                            outputPath, maxEValue);
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
                        System.out.println("File reading error.");
                    }
                }
            }
        },

        searchPeaks {
            @Override
            protected void exec(String[] args) {
                try {
                    Path theoreticScansPath = Paths.get(args[1]);
                    Path outputPath = Paths.get(args[2]);
                    int pos = 3;
                    List<Analyzer.ScanStream> programRes = new ArrayList<>();
                    while (pos < args.length) {
                        DeconvolutionProgram program =
                                DeconvolutionProgram.valueOf(args[pos++]);
                        Path file = Paths.get(args[pos++]);
                        programRes.add(new Analyzer.ScanStream(program, file));
                    }
                    Analyzer.ScanStream[] scanStreams =
                            new Analyzer.ScanStream[programRes.size()];
                    for (int i = 0; i < programRes.size(); i++) {
                        scanStreams[i] = programRes.get(i);
                    }
                    Analyzer.searchPeaks(theoreticScansPath, outputPath,
                            scanStreams);
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        },

        countFound {
            @Override
            protected void exec(String[] args) {
                int pos = 1;
                boolean excluding = false;
                double accuracy = Double.valueOf(args[pos++]);
                Path theoreticTable = Paths.get(args[pos++]);
                List <Map<Integer, ExperimentalScan>> foundBy =
                        new ArrayList<>();
                List <Map<Integer, ExperimentalScan>> notFoundBy =
                        new ArrayList<>();
                while (pos < args.length) {
                    if (args[pos].equals("-exclude")) {
                        excluding= true;
                        pos++;
                    } else {
                        try {
                            DeconvolutionProgram program =
                                    DeconvolutionProgram.valueOf(args[pos++]);
                            Map<Integer, ExperimentalScan> map =
                                    program.getOutputMap(Paths.get(args[pos++]));
                            if (excluding) {
                                notFoundBy.add(map);
                            } else {
                                foundBy.add(map);
                            }
                        } catch (IOException e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
                try {
                    System.out.println(Analyzer.countExclusivelyFound(
                            theoreticTable, foundBy, notFoundBy, accuracy));
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        },

        searchFound {
            @Override
            protected void exec(String[] args) {
                int pos = 1;
                boolean excluding = false;
                double accuracy = Double.valueOf(args[pos++]);
                Path theoreticTable = Paths.get(args[pos++]);
                List <Map<Integer, ExperimentalScan>> foundBy =
                        new ArrayList<>();
                List <Map<Integer, ExperimentalScan>> notFoundBy =
                        new ArrayList<>();
                while (pos < args.length) {
                    if (args[pos].equals("-exclude")) {
                        excluding= true;
                        pos++;
                    } else {
                        try {
                            DeconvolutionProgram program =
                                    DeconvolutionProgram.valueOf(args[pos++]);
                            Map<Integer, ExperimentalScan> map =
                                    program.getOutputMap(Paths.get(args[pos++]));
                            if (excluding) {
                                notFoundBy.add(map);
                            } else {
                                foundBy.add(map);
                            }
                        } catch (IOException e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
                try {
                    List<Peak> exclusivelyFound =
                            Analyzer.searchExclusivelyFound(theoreticTable,
                                    foundBy, notFoundBy, accuracy);
                    exclusivelyFound.forEach(peak -> {
                        System.out.printf("%d %c%d\n", peak.getScan().getId(),
                                peak.getIon().getType(), peak.getIon().getNumber());
                    });
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }
        },

        spectrum {
            @Override
            protected void exec(String[] args) {
                Path path = Paths.get(args[1]);
                int id = Integer.valueOf(args[2]);
                try {
                    Optional<TheoreticScan> requiredScan =
                            TheoreticScan.readTable(path)
                            .filter(scan -> scan.getId() == id)
                            .findFirst();
                    if (requiredScan.isPresent()) {
                        System.out.println(requiredScan.get().getStringSequence());
                        System.out.println(requiredScan.get().getPrecursorMass());
                        for (TheoreticScan.Ion ion: requiredScan.get().getIons()) {
                            System.out.printf("%c%d %f\n",  ion.getType(),
                                    ion.getNumber(), ion.getMass());
                        }
                    } else {
                        System.out.println("No such scan found.");
                    }
                } catch (IOException e) {
                    System.out.println("Error reading table.");
                }
            }
        };

        protected abstract void exec(String[] args);
    }
}
