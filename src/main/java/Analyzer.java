import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Analyzer {
    private static final String ION = "ION";
    private static final String BEGIN = "BEGIN ";
    private static final String END = "END ";
    private static final String PRISM = "PRISM";
    private static final String SPECTRUM_ID_PREF = "SPECTRUM_ID=%d\n";

    public static void annotate(Iterator<ExperimentalScan> experimentalScans,
                                Map<Integer, TheoreticScan> theoreticScans,
                                Path outputPath) throws IOException {
        final double maxEValue = 1e-10;
        final double precision = 1e-5;
        final String MATCH_PAIR = "MATCH_PAIR";
        final String MASS_SHIFT = "MASS_SHIFT";
        final String UNMATCHED_PEAKS_TEMPLATE = "UNMATCHED_PEAKS=%d\n";

        BufferedWriter annotationWriter = Files.newBufferedWriter(outputPath);

        try {
            experimentalScans.forEachRemaining(scan -> {
                TheoreticScan theoreticScan = theoreticScans.get(scan.getId());
                if (theoreticScan == null || theoreticScan.getEValue() > maxEValue) {
                    return;
                }

                try {
                    annotationWriter.write(BEGIN + PRISM + "\n");
                    annotationWriter.write(String.format(SPECTRUM_ID_PREF,
                            scan.getId()));

                    annotationWriter.write(BEGIN + MASS_SHIFT + "\n");
                    List<TheoreticScan.MassShift> modifications =
                            theoreticScan.getModifications();
                    for (int i = 0; i < modifications.size(); i++) {
                        annotationWriter.write(String.format("%-3d %-3d %-3d %f\n",
                                i, modifications.get(i).getStart(),
                                modifications.get(i).getEnd(),
                                modifications.get(i).getMass()));
                    }
                    annotationWriter.write(END + MASS_SHIFT + "\n");

                    annotationWriter.write(BEGIN + MATCH_PAIR + "\n");
                    TheoreticScan.Ion[] theoreticIons = theoreticScan.getIons();
                    List<IonMatch> matches = new ArrayList<>();
                    int unmatchedPeaks = 0;
                    for (double peak : scan.getPeaks()) {
                        double eps = peak * precision;
                        double minMass = peak - eps;
                        int left = -1;
                        int right = theoreticIons.length - 1;
                        while (right - left > 1) {
                            int mid = (right + left) / 2;
                            if (theoreticIons[mid].getMass() < minMass) {
                                left = mid;
                            } else {
                                right = mid;
                            }
                        }
                        int pos = right;
                        boolean matched = false;
                        while (pos < theoreticIons.length &&
                                theoreticIons[pos].getMass() > peak - eps &&
                                theoreticIons[pos].getMass() < peak + eps) {
                            matches.add(new IonMatch(theoreticIons[pos], peak));
                            pos++;
                            matched = true;
                        }
                        if (!matched) {
                            unmatchedPeaks++;
                        }
                    }
                    matches.sort(IonMatch.MASS_ASCENDING_ORDER);
                    for (int i = 0; i < matches.size(); i++) {
                        annotationWriter.write(String.format("%-3d %s\n", i,
                                matches.get(i).toString()));
                    }
                    annotationWriter.write(END + MATCH_PAIR + "\n");

                    annotationWriter.write(String.format(UNMATCHED_PEAKS_TEMPLATE,
                            unmatchedPeaks));

                    annotationWriter.write(END + PRISM + "\n");
                    annotationWriter.write("\n");
                } catch (IOException e) {
                    throw new Error(e);
                }
            });
        } catch (Error e) {
            throw (IOException)e.getCause();
        }

        annotationWriter.close();
    }

    public static void searchPeaks(Path table, Path outputPath,
                                   ScanStream... streams) throws IOException {
        final double ACCURACY = 1e-5;
        Map<DeconvolutionProgram, Map<Integer,ExperimentalScan>> programResults =
                new HashMap<>();
        for (ScanStream stream: streams) {
            Map<Integer,ExperimentalScan> scans =
                    programResults.computeIfAbsent(stream.getProgram(),
                    program -> new HashMap<>());
            stream.getScans().forEachRemaining(scan ->
                    scans.put(scan.getId(), scan));
        }

        try (BufferedWriter resWriter = Files.newBufferedWriter(outputPath)) {
            TheoreticScan.readTable(table).forEach(theoreticScan -> {
                try {
                    resWriter.write(BEGIN + PRISM + '\n');
                    resWriter.write(String.format(SPECTRUM_ID_PREF,
                            theoreticScan.getId()));

                    Map<DeconvolutionProgram,double[]> findings =
                            new HashMap<>();
                    programResults.forEach((program, scans) -> {
                        if (scans.containsKey(theoreticScan.getId())) {
                            double[] peaks = scans.get(theoreticScan.getId())
                                    .getPeaks();
                            Arrays.sort(peaks);
                            findings.put(program, peaks);
                        }
                    });
                    for (TheoreticScan.Ion ion: theoreticScan.getIons()) {
                        double eps = ion.getMass() * ACCURACY;
                        StringBuilder titleBuilder = new StringBuilder();
                        titleBuilder.append(ION);
                        titleBuilder.append(' ');
                        titleBuilder.append(ion.getType());
                        titleBuilder.append(ion.getNumber());
                        titleBuilder.append(' ');
                        titleBuilder.append(String.valueOf(ion.getMass()));
                        titleBuilder.append('\n');
                        resWriter.write(titleBuilder.toString());
                        List<DeconvolutionProgram> finders = new ArrayList<>();
                        findings.forEach(((program, peaks) -> {
                            int ind = -1 - Arrays.binarySearch(peaks, ion.getMass());
                            if (ind > 0 && Math.abs(peaks[ind - 1] - ion.getMass()) < eps ||
                                    ind < peaks.length &&
                                            Math.abs(peaks[ind] - ion.getMass()) < eps) {
                                finders.add(program);
                            }
                        }));
                        for (DeconvolutionProgram program: finders) {
                            resWriter.write(program.toString() + '\n');
                        }
                    }

                    resWriter.write(END + PRISM + '\n');
                    resWriter.write('\n');
                } catch (IOException e) {
                    throw new Error(e);
                }
            });
        } catch (Error e) {
            throw (IOException) e.getCause();
        }
    }

    public static class ScanStream {
        private final DeconvolutionProgram program;
        private final Iterator<ExperimentalScan> scans;

        public ScanStream(DeconvolutionProgram program, Path file) throws IOException {
            this.program = program;
            scans = program.getOutputIterator(file);
        }

        public DeconvolutionProgram getProgram() {
            return program;
        }

        public Iterator<ExperimentalScan> getScans() {
            return scans;
        }
    }

    private static class IonMatch {
        private final TheoreticScan.Ion ion;
        private final double peakMass;

        private IonMatch(TheoreticScan.Ion ion, double peakMass) {
            this.ion = ion;
            this.peakMass = peakMass;
        }

        @Override
        public String toString() {
            return String.format("%-18f %c%-2d %-18f",
                    peakMass,
                    ion.getType(),
                    ion.getNumber(),
                    ion.getMass());
        }

        private static final Comparator<IonMatch> MASS_ASCENDING_ORDER = (match1, match2) ->
            TheoreticScan.Ion.MASS_ASCENDING_ORDER.
                    compare(match1.ion, match2.ion);
    }
}
