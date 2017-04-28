import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Analyzer {
    public static void annotate(Iterator<ExperimentalScan> experimentalScans,
                                Map<Integer, TheoreticScan> theoreticScans,
                                Path outputPath) throws IOException {
        final double maxEValue = 1e-10;
        final double precision = 1e-5;
        final String BEGIN_PRISM = "BEGIN PRISM\n";
        final String END_PRISM = "END PRISM\n";
        final String SPECTRUM_ID_PREF = "SPECTRUM_ID=";
        final String BEGIN_MATCH_PAIR = "BEGIN_MATCH_PAIR\n";
        final String END_MATCH_PAIR = "END MATCH_PAIR\n";

        BufferedWriter annotationWriter = Files.newBufferedWriter(outputPath);

        try {
            experimentalScans.forEachRemaining(scan -> {
                TheoreticScan theoreticScan = theoreticScans.get(scan.getId());
                if (theoreticScan == null || theoreticScan.getEValue() > maxEValue) {
                    return;
                }

                try {
                    annotationWriter.write(BEGIN_PRISM);
                    annotationWriter.write(SPECTRUM_ID_PREF +
                            String.valueOf(scan.getId()) + '\n');
                    annotationWriter.write(BEGIN_MATCH_PAIR);
                    TheoreticScan.Ion[] theoreticIons = theoreticScan.getIons();
                    List<IonMatch> matches = new ArrayList<>();
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
                        while (pos < theoreticIons.length &&
                                theoreticIons[pos].getMass() > peak - eps &&
                                theoreticIons[pos].getMass() < peak + eps) {
                            matches.add(new IonMatch(theoreticIons[pos], peak));
                            pos++;
                        }
                    }
                    matches.sort(IonMatch.MASS_ASCENDING_ORDER);
                    for (int i = 0; i < matches.size(); i++) {
                        annotationWriter.write(String.format("%-3d %s\n", i,
                                matches.get(i).toString()));
                    }
                    annotationWriter.write(END_MATCH_PAIR);
                    annotationWriter.write(END_PRISM);
                    annotationWriter.write("\n");
                } catch (IOException e) {
                    Error error = new Error();
                    error.addSuppressed(e);
                    throw error;
                }
            });
        } catch (Error e) {
            throw (IOException)e.getSuppressed()[0];
        }

        annotationWriter.close();
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
