import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TheoreticScan extends Scan {
    private final double DELTA_Y = 18.01528;

    private AminoAcid[] sequence;
    private double[] ionsB;
    private double[] ionsY;
    private int length;
    private double eValue;
    private String stringSequence;

    public TheoreticScan(int id, int prsmId, int charge, double precursorMass,
                         double eValue, String stringSequence) {
        super(id, prsmId, charge, precursorMass);
        this.eValue = eValue;
        this.stringSequence = stringSequence;
        /*
        this.sequence = sequence;
        length = sequence.length;

        ionsB = new double[length + 1];
        ionsB[0] = 0;
        for (int i = 1; i <= length; i++) {
            ionsB[i] = ionsB[i - 1] + sequence[i - 1].getMass();
        }

        ionsY = new double[length + 1];
        ionsY[0] = DELTA_Y;
        for (int i = 1; i <= length; i++) {
            ionsY[i] = ionsY[i - 1] + sequence[length - i].getMass();
        }
        */
    }

    public double getEValue() {
        return eValue;
    }

    public int getLength() {
        return length;
    }

    public double[] getIonsB() {
        return ionsB;
    }

    public double[] getIonsY() {
        return ionsY;
    }

    public AminoAcid[] getSequence() {
        return sequence;
    }

    public String getStringSequence() {
        return stringSequence;
    }

    public static Stream<TheoreticScan> readTable(Path tablePath) throws IOException {
        return Files.lines(tablePath)
                .skip(1)
                .map(TheoreticScan::parseTableLine);
    }

    public static Map<Integer, TheoreticScan> mapFromTable(Path tablePath)
            throws IOException {
        return readTable(tablePath).collect(Collectors.toMap(Scan::getId, scan -> scan));
    }

    private static TheoreticScan parseTableLine(String line) {
        String[] data = line.split("\t");
        Integer id  = Integer.valueOf(data[2]);
        Integer prsmId = Integer.valueOf(data[1]);
        Integer charge = Integer.valueOf(data[5]);
        double precursorMass = Double.valueOf(data[6]);
        double eValue = Double.valueOf(data[18]);

        String sequenceStr = data[13];
        /*
        if (sequenceStr.length() > 3 && sequenceStr.lastIndexOf(".") ==
                sequenceStr.length() - 2) {
            sequenceStr = sequenceStr.substring(0, sequenceStr.lastIndexOf("."));
        }
        if (sequenceStr.contains(".")) {
            sequenceStr = sequenceStr.substring(sequenceStr.indexOf("."));
        }
        if (sequenceStr.contains("]")) {
            sequenceStr = sequenceStr.substring(sequenceStr.indexOf(']') + 1);
        }
        */


        return new TheoreticScan(id, prsmId, charge, precursorMass, eValue,
                sequenceStr);
    }
}
