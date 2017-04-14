import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

public class Analyzer {
    public static void annotate(Iterator<ExperimentalScan> experimentalScans,
                                Map<Integer, TheoreticScan> theoreticScanMap,
                                Path outputPath) throws IOException {
        final double maxEValue = 1e-5;
        final double precision = 1e-5;
        final String BEGIN_PRISM = "BEGIN PRISM\n";
        final String END_PRISM = "END PRISM\n";
        final String SPECTRUM_ID_PREF = "SPECTRUM_ID=";
        final String BEGIN_MATCH_PAIR = "BEGIN_MATCH_PAIR\n";
        final String END_MATCH_PAIR = "END MATCH_PAIR\n";

        BufferedWriter annotationWriter = Files.newBufferedWriter(outputPath);

        try {
            experimentalScans.forEachRemaining(scan -> {
                TheoreticScan theoreticScan = theoreticScanMap.get(scan.getId());
                if (theoreticScan == null || theoreticScan.getEValue() > maxEValue) {
                    return;
                }

                try {
                    annotationWriter.write(BEGIN_PRISM);
                    annotationWriter.write(BEGIN_MATCH_PAIR);
                    annotationWriter.write(SPECTRUM_ID_PREF +
                            String.valueOf(scan.getId()) + '\n');
                    double[] ionsB = theoreticScan.getIonsB();
                    double[] ionsY = theoreticScan.getIonsY();
                    for (double peak : scan.getPeaks()) {
                        double eps = peak * precision;
                        for (int j = 1; j < theoreticScan.getLength(); j++) {
                            if (Math.abs(peak - ionsB[j]) < eps) {
                                annotationWriter.write(String.valueOf(peak) + "B" +
                                        String.valueOf(j) + "\n");
                            }
                            if (Math.abs(peak - ionsY[j]) < eps) {
                                annotationWriter.write(String.valueOf(peak) + "Y" +
                                        String.valueOf(j) + "\n");
                            }
                        }
                    }
                    annotationWriter.write(END_MATCH_PAIR);
                    annotationWriter.write(END_PRISM);
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
}
