import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class Analyzer {
    private static final String ION = "ION";
    private static final String BEGIN = "BEGIN ";
    private static final String END = "END ";
    private static final String PRISM = "PRISM";
    private static final String SPECTRUM_ID_PREF = "SPECTRUM_ID=%d\n";

    /**
     * Annotates deconvolution results.
     * @param experimentalScans iterator over the deconvolution results.
     * @param theoreticScans map from theoretical scan number to its
     *                       TheoreticalScan representation.
     * @param outputPath the path to put the results.
     * @param maxEValue the maximum acceptable eValue of a theoretical
     *                  scan.
     * @throws IOException in case of an output writing error.
     */
    public static void annotate(Iterator<ExperimentalScan> experimentalScans,
                                Map<Integer, TheoreticScan> theoreticScans,
                                Path outputPath,
                                double maxEValue) throws IOException {
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

    /**
     * For each theoretic peak lists all the programs that have found it.
     * @param table a table of theoretic scans.
     * @param outputPath the path to put results at.
     * @param streams a list of ScanStreams for all the programs to use.
     * @throws IOException if a read/write error occurs.
     */
    public static void searchPeaks(Path table, Path outputPath,
                                   ScanStream... streams)
            throws IOException {
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
                            if (contains(peaks, ion.getMass(), eps)) {
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

    /**
     * Counts peaks that were found by one set of programs and were not
     * found by another.
     * @param table the table with theoretical scans.
     * @param finders a list of maps of scans found by programs that
     *                should have found the peaks to count.
     * @param nonFinders list of maps of scans programs that shouldn't
     *                   have found the peaks.
     * @param accuracy the accuracy of peaks comparison.
     * @return the number of the peaks that were found only by the
     * required programs.
     * @throws IOException in case of a table reading error.
     */
    public static int countExclusivelyFound(Path table,
                                  List<Map<Integer, ExperimentalScan>> finders,
                                  List<Map<Integer, ExperimentalScan>> nonFinders,
                                            double accuracy)
            throws IOException {
        Counter findings = new Counter();
        TheoreticScan.readTable(table).forEach(theoreticScan -> {
            TheoreticScan.Ion[] theoreticIons = theoreticScan.getIons();
            List<double[]> foundScanIons = new ArrayList<>();
            List<double[]> nonFoundScanIons = new ArrayList<>();
            for (Map<Integer, ExperimentalScan> finder : finders) {
                ExperimentalScan foundScan = finder.get(theoreticScan.getId());
                if (foundScan == null) {
                    return;
                } else {
                    double[] ions = Arrays.copyOf(foundScan.getPeaks(),
                            foundScan.getPeaks().length);
                    Arrays.sort(ions);
                    foundScanIons.add(ions);
                }
            }
            for (Map<Integer, ExperimentalScan> finder : nonFinders) {
                ExperimentalScan foundScan = finder.get(theoreticScan.getId());
                if (foundScan == null) {
                    nonFoundScanIons.add(null);
                } else {
                    double[] ions = Arrays.copyOf(foundScan.getPeaks(),
                            foundScan.getPeaks().length);
                    Arrays.sort(ions);
                    nonFoundScanIons.add(ions);
                }
            }

            Stream.of(theoreticIons).forEach(ion -> {
                double eps = accuracy * ion.getMass();
                for (double[] foundScanIon : foundScanIons) {
                    if (!contains(foundScanIon, ion.getMass(), eps)) {
                        return;
                    }
                }
                for (double[] nonFoundPeaks: nonFoundScanIons) {
                    if (nonFoundPeaks != null &&
                            contains(nonFoundPeaks, ion.getMass(), eps)) {
                        return;
                    }
                }
                findings.inc();
            });
        });
        return findings.get();
    }

    /**
     * Looks for peaks that were found by one set of programs and were
     * not found by another.
     * @param table the table with theoretical scans.
     * @param finders a list of maps of scans found by programs that
     *                should have found the peaks to count.
     * @param nonFinders list of maps of scans programs that shouldn't
     *                   have found the peaks.
     * @param accuracy the accuracy of peaks comparison.
     * @return a list of the peaks that were found only by the
     * required programs.
     * @throws IOException in case of a table reading error.
     */
    public static List<Peak> searchExclusivelyFound(Path table,
                                            List<Map<Integer, ExperimentalScan>> finders,
                                            List<Map<Integer, ExperimentalScan>> nonFinders,
                                            double accuracy)
            throws IOException {
        List<Peak> exclusivelyFound = new ArrayList<>();
        TheoreticScan.readTable(table).forEach(theoreticScan -> {
            TheoreticScan.Ion[] theoreticIons = theoreticScan.getIons();
            List<double[]> foundScanIons = new ArrayList<>();
            List<double[]> nonFoundScanIons = new ArrayList<>();
            for (Map<Integer, ExperimentalScan> finder : finders) {
                ExperimentalScan foundScan = finder.get(theoreticScan.getId());
                if (foundScan == null) {
                    return;
                } else {
                    double[] ions = Arrays.copyOf(foundScan.getPeaks(),
                            foundScan.getPeaks().length);
                    Arrays.sort(ions);
                    foundScanIons.add(ions);
                }
            }
            for (Map<Integer, ExperimentalScan> finder : nonFinders) {
                ExperimentalScan foundScan = finder.get(theoreticScan.getId());
                if (foundScan == null) {
                    nonFoundScanIons.add(null);
                } else {
                    double[] ions = Arrays.copyOf(foundScan.getPeaks(),
                            foundScan.getPeaks().length);
                    Arrays.sort(ions);
                    nonFoundScanIons.add(ions);
                }
            }

            Stream.of(theoreticIons).forEach(ion -> {
                double eps = accuracy * ion.getMass();
                for (double[] foundScanIon : foundScanIons) {
                    if (!contains(foundScanIon, ion.getMass(), eps)) {
                        return;
                    }
                }
                for (double[] nonFoundPeaks: nonFoundScanIons) {
                    if (nonFoundPeaks != null &&
                            contains(nonFoundPeaks, ion.getMass(), eps)) {
                        return;
                    }
                }
                exclusivelyFound.add(new Peak(theoreticScan, ion));
            });
        });
        return exclusivelyFound;
    }

    /**
     * Checks if the array contains the value with the given precision.
     */
    private static boolean contains(double[] arr, double key, double eps) {
        int ind = Arrays.binarySearch(arr, key);
        if (ind < 0) {
            ind = -1 - ind;
        }
        return ind > 0 && Math.abs(arr[ind - 1] - key) < eps ||
                ind < arr.length && Math.abs(arr[ind] - key) < eps;
    }

    private static class Counter {
        private int counter = 0;

        private void inc() {
            counter++;
        }

        private int get() {
            return counter;
        }
    }

    /**
     * Represents a stream of scans defined by a program and an
     * iterator of the scans.
     */
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

    /**
     * Represents a match between a theoretical b- or y-ion and a peak
     * from an experiment.
     */
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
