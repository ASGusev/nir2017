import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            Command.help.exec(args);
            return;
        }
        try {
            Command requestedCommand = Command.valueOf(args[0]);
            requestedCommand.exec(args);
        } catch (EnumConstantNotPresentException e) {
            Command.help.exec(args);
        }
    }

    private enum Command {
        annotate {
            @Override
            protected void exec(String[] args) {
                if (args.length < 7) {
                    Command.help.exec(args);
                    return;
                }
                DeconvolutionProgram format =
                        DeconvolutionProgram.valueOf(args[1]);
                Path deconvolutionResultsPath = Paths.get(args[2]);
                Path theoreticScansTablePath = Paths.get(args[3]);
                Path outputPath = Paths.get(args[4]);
                double maxEValue = Double.valueOf(args[5]);
                double accuracy = Double.valueOf(args[6]);

                try {
                    Iterator<ExperimentalScan> outputIterator =
                            format.getOutputIterator(deconvolutionResultsPath);
                    Map<Integer, TheoreticScan> theoreticScans =
                            TheoreticScan.mapFromTable(theoreticScansTablePath);
                    Analyzer.annotate(outputIterator, theoreticScans,
                            outputPath, maxEValue, accuracy);
                } catch (IOException e) {
                    System.out.println("File read/write error.");
                }
            }

            @Override
            protected String getDescription() {
                return name() + "<program name> <deconvolution file>" +
                        " <table path> <output path> <max eValue> " +
                        "<accuracy> - annotate deconvolution results" +
                        "using the given theoretic scans table. Scans" +
                        "with eValue above the given maximum are " +
                        "ignored. The supplied accuracy is used for " +
                        "mass comparision.";
            }
        },

        count {
            @Override
            protected void exec(String[] args) {
                if (args.length < 2) {
                    Command.help.exec(args);
                    return;
                }

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

            @Override
            protected String getDescription() {
                return name() + "<filters> <table path> - count scans " +
                        "with certain properties in a table. Filters:" +
                        "-evalue_over <value>, -evalue_under <value>," +
                        "-modified, -not_modified.";
            }
        },

        searchPeaks {
            @Override
            protected void exec(String[] args) {
                try {
                    if (args.length < 4) {
                        Command.help.exec(args);
                        return;
                    }
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

            @Override
            protected String getDescription() {
                return name() + "<theoretic table path> <output path>" +
                        "<program outputs> - for each peak list " +
                        "programs that have found it. Program description" +
                        "format: <name> <output filepath>.";
            }
        },

        countFound {
            @Override
            protected void exec(String[] args) {
                if (args.length < 2) {
                    Command.help.exec(args);
                    return;
                }

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

            @Override
            protected String getDescription() {
                return name() + " <accuracy> <table path> " +
                        "<deconvolution output paths> - to list " +
                        "peaks that are present in the listed output" +
                        "files. Output file format: <program> <path>. " +
                        "Use -exclude option to exclude peaks in some " +
                        "files.";
            }
        },

        searchFound {
            @Override
            protected void exec(String[] args) {
                if (args.length < 2) {
                    Command.help.exec(args);
                    return;
                }

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
                    exclusivelyFound.forEach(peak ->
                        System.out.printf("%d %c%d\n",
                                peak.getScan().getId(),
                                peak.getIon().getType(),
                                peak.getIon().getNumber())
                    );
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
            }

            @Override
            protected String getDescription() {
                return name() + " <accuracy> <table path> " +
                        "<deconvolution output paths> - to search for " +
                        "peaks that are present in the listed output" +
                        "files. Output file format: <program> <path>. " +
                        "Use -exclude option to exclude peaks in some " +
                        "files.";
            }
        },

        spectrum {
            @Override
            protected void exec(String[] args) {
                if (args.length < 3) {
                    Command.help.exec(args);
                    return;
                }

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

            @Override
            protected String getDescription() {
                return name() + " <table path> <scan id>  - to print " +
                        "a spectrum of a scan.";
            }
        },

        help {
            @Override
            protected void exec(String[] args) {
                System.out.println("Commands:");
                for (Command command: Command.values()) {
                    System.out.println(command.getDescription());
                }
            }

            @Override
            protected String getDescription() {
                return name() + " - show this message.";
            }
        };

        protected abstract void exec(String[] args);

        protected abstract String getDescription();
    }
}
