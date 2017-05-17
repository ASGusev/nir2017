import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * A enumeration of supported deconvolution programs.
 */
public enum DeconvolutionProgram {
    MSDeconv {
        private final String SCAN_BEGINNING = "BEGIN IONS";
        private final String SCAN_END = "END IONS";
        private final String ID_PREF = "ID=";
        private final String PRECURSOR_MASS_PREF = "PRECURSOR_MASS=";
        private final String PRECURSOR_CHARGE_PREF = "PRECURSOR_CHARGE=";

        @Override
        public Iterator<ExperimentalScan> getOutputIterator(final Path filePath) throws IOException {
            return new Iterator<ExperimentalScan>() {
                private ExperimentalScan nextScan;
                private BufferedReader resultsReader;

                {
                    resultsReader = Files.newBufferedReader(filePath);
                    nextScan = readScan();
                }

                @Override
                public boolean hasNext() {
                    return nextScan != null;
                }

                @Override
                public ExperimentalScan next() {
                    if (nextScan == null) {
                        return null;
                    }

                    ExperimentalScan curScan = nextScan;
                    nextScan = readScan();
                    return curScan;
                }

                private ExperimentalScan readScan() {
                    String line = null;
                    ExperimentalScan scan = null;
                    int id = 0;
                    int prsmId = 0;
                    int charge = 0;
                    double precursorMass = 0;
                    double[] peaks;
                    List<Double> peaksList = new ArrayList<>();

                    try {
                        while (scan == null && (line = resultsReader.readLine()) != null) {
                            if (line.startsWith(ID_PREF)) {
                                id = Integer.valueOf(line.substring(ID_PREF.length()));
                                continue;
                            }
                            if (line.startsWith(PRECURSOR_MASS_PREF)) {
                                precursorMass = Double.valueOf(
                                        line.substring(PRECURSOR_MASS_PREF.length()));
                                continue;
                            }
                            if (line.startsWith(PRECURSOR_CHARGE_PREF)) {
                                charge = Integer.valueOf(
                                        line.substring(PRECURSOR_CHARGE_PREF.length()));
                                continue;
                            }
                            if (!line.isEmpty() && Character.isDigit(line.charAt(0))) {
                                String peak = line.substring(0, line.indexOf('\t'));
                                peaksList.add(Double.valueOf(peak));
                                continue;
                            }

                            if (line.equals(SCAN_END)) {
                                peaks = new double[peaksList.size()];
                                for (int i = 0; i < peaks.length; i++) {
                                    peaks[i] = peaksList.get(i);
                                }
                                scan = new ExperimentalScan(id, prsmId, charge, precursorMass,
                                        peaks);
                            }
                        }
                    } catch (IOException e) {
                        try {
                            resultsReader.close();
                        } catch (IOException e1) {}
                        throw new ScanReadError(e);
                    }
                    if (line == null) {
                        try {
                            resultsReader.close();
                        } catch (IOException e) {}
                    }
                    return scan;
                }
            };
        }
    },

    ThermoXtract {
        private final String SCAN_BEGINNING = "BEGIN IONS";
        private final String SCAN_END = "END IONS";
        private final String TITLE_PREF = "TITLE=";
        private final String PEPMASS_PREF = "PEPMASS=";

        @Override
        public Iterator<ExperimentalScan> getOutputIterator(final Path filePath) throws IOException {
            return new Iterator<ExperimentalScan>() {
                private ExperimentalScan nextScan;
                private BufferedReader resultsReader;

                {
                    resultsReader = Files.newBufferedReader(filePath);
                    nextScan = readScan();
                }

                @Override
                public boolean hasNext() {
                    return nextScan != null;
                }

                @Override
                public ExperimentalScan next() {
                    if (nextScan == null) {
                        return null;
                    }

                    ExperimentalScan curScan = nextScan;
                    nextScan = readScan();
                    return curScan;
                }

                private ExperimentalScan readScan() {
                    String line = null;
                    ExperimentalScan scan = null;
                    int id = 0;
                    int prsmId = 0;
                    int charge = 0;
                    double precursorMass = 0;
                    double[] peaks;
                    List<Double> peaksList = new ArrayList<>();

                    try {
                        while (scan == null && (line = resultsReader.readLine()) != null) {
                            if (line.startsWith(TITLE_PREF)) {
                                String[] tokens = line.split(" ");
                                String idToken = tokens[tokens.length - 1];
                                id = Integer.valueOf(idToken.substring(5,
                                        idToken.length() - 1));
                            }
                            if (line.startsWith(PEPMASS_PREF)) {
                                line = line.substring(PEPMASS_PREF.length());
                                if (line.contains(" ")) {
                                    line = line.substring(0, line.indexOf(" "));
                                }
                                precursorMass = Double.valueOf(line);
                                continue;
                            }
                            if (!line.isEmpty() && Character.isDigit(line.charAt(0))) {
                                String peak = line.substring(0, line.indexOf(' '));
                                peaksList.add(Double.valueOf(peak));
                                continue;
                            }

                            if (line.equals(SCAN_END)) {
                                peaks = new double[peaksList.size()];
                                for (int i = 0; i < peaks.length; i++) {
                                    peaks[i] = peaksList.get(i);
                                }
                                scan = new ExperimentalScan(id, prsmId, charge,
                                        precursorMass, peaks);
                            }
                        }
                    } catch (IOException e) {
                        try {
                            resultsReader.close();
                        } catch (IOException e1) {}
                        throw new ScanReadError(e);
                    }
                    if (line == null) {
                        try {
                            resultsReader.close();
                        } catch (IOException e) {}
                    }
                    return scan;
                }
            };
        }
    },

    Hardklor {
        @Override
        public Iterator<ExperimentalScan> getOutputIterator(Path filePath) throws IOException {
            return new Iterator<ExperimentalScan>() {
                private String nextLine;
                private BufferedReader scansReader;

                {
                    scansReader = Files.newBufferedReader(filePath);
                    nextLine = scansReader.readLine();
                }

                @Override
                public boolean hasNext() {
                    return nextLine != null;
                }

                @Override
                public ExperimentalScan next() {
                    String[] tokens = nextLine.split("\t");
                    int scanNumber = Integer.valueOf(tokens[1]);
                    double mass = Double.valueOf(tokens[4]);
                    int charge = Integer.valueOf(tokens[5]);
                    List<Double> peaksList = new ArrayList<>();

                    try {
                        nextLine = scansReader.readLine();
                        while (nextLine != null && nextLine.charAt(0) == 'P') {
                            tokens = nextLine.split("\t");
                            peaksList.add(Double.valueOf(tokens[1]));
                            nextLine = scansReader.readLine();
                        }
                    } catch (IOException e) {
                        throw new ScanReadError(e);
                    }

                    double[] peaks = new double[peaksList.size()];
                    for (int i = 0; i < peaks.length; i++) {
                        peaks[i] = peaksList.get(i);
                    }
                    return new ExperimentalScan(scanNumber, 0, charge, mass, peaks);
                }
            };
        }
    };

    /**
     * Makes an iterator over the output of the program.
     * @param filePath the output file to read.
     * @return an Iterator<ExperimentalScan> containing all the scans
     * described in the file.
     * @throws IOException if an error during reading the file occurs.
     */
    public abstract Iterator<ExperimentalScan> getOutputIterator(Path filePath)
            throws IOException;

    /**
     * Reads a file with output of the program and collects all the scans
     * in a map from the number of the scan to tis ExperimentalScan
     * representation.
     */
    public Map<Integer, ExperimentalScan> getOutputMap(Path path)
            throws IOException {
        Map<Integer, ExperimentalScan> outputMap = new HashMap<>();
        Iterator<ExperimentalScan> outputIterator = getOutputIterator(path);
        outputIterator.forEachRemaining(scan ->
            outputMap.put(scan.getId(), scan)
        );
        return outputMap;
    }

    /**
     * A error thrown in case of an error reading a scan.
     */
    public static class ScanReadError extends Error {
        private ScanReadError() {
            super();
        }

        private ScanReadError(Throwable cause) {
            super(cause);
        }
    }
}
