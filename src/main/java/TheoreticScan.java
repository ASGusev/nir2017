import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TheoreticScan extends Scan {

    private AminoAcid[] sequence;
    private Ion[] ions;
    private double eValue;
    private String stringSequence;

    public TheoreticScan(int id, int prsmId, int charge, double precursorMass,
                         double eValue, String stringSequence) {
        super(id, prsmId, charge, precursorMass);
        this.eValue = eValue;
        this.stringSequence = stringSequence;
    }

    public Ion[] getIons() {
        if (ions == null) {
            makeIons();
        }
        return ions;
    }

    public double getEValue() {
        return eValue;
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

    public static class Ion {
        private final char type;
        private final int number;
        private final double mass;

        public Ion(char type, int number, double mass) {
            this.type = type;
            this.number = number;
            this.mass = mass;
        }

        public char getType() {
            return type;
        }

        public int getNumber() {
            return number;
        }

        public double getMass() {
            return mass;
        }

        public static Comparator<Ion> MASS_ASCENDING_ORDER = (Ion ion1, Ion ion2) -> {
            if (ion1.mass < ion2.mass) {
                return -1;
            }
            if (ion1.mass == ion2.mass) {
                return 0;
            }
            return 1;
        };
    }

    private static TheoreticScan parseTableLine(String line) {
        String[] data = line.split("\t");
        Integer id  = Integer.valueOf(data[2]);
        Integer prsmId = Integer.valueOf(data[1]);
        Integer charge = Integer.valueOf(data[5]);
        double precursorMass = Double.valueOf(data[6]);
        double eValue = Double.valueOf(data[18]);

        String sequenceStr = data[13];
        sequenceStr = sequenceStr.substring(sequenceStr.indexOf(".") + 1,
                sequenceStr.lastIndexOf("."));


        return new TheoreticScan(id, prsmId, charge, precursorMass, eValue,
                sequenceStr);
    }

    private void makeSequence() {
        List<AminoAcid> aminoSequence = new ArrayList<>();
        for (char c: stringSequence.toCharArray()) {
            if (Character.isLetter(c)) {
                aminoSequence.add(AminoAcid.valueOf(String.valueOf(c)));
            }
        }
        sequence = new AminoAcid[aminoSequence.size()];
        for (int i = 0; i < sequence.length; i++) {
            sequence[i] = aminoSequence.get(i);
        }
    }

    private void makeIons() {
        final double DELTA_Y = 18.01528;
        int pos = 0;
        double prefMass = 0.0;
        boolean modified = false;
        double[] acidMasses = AminoAcid.getMasses();
        List<Ion> ionsBList = new ArrayList<>();
        List<Ion> ionsYList = new ArrayList<>();
        int acidsNumber = 0;
        for (; pos < stringSequence.length() - 1; pos++) {
            if (Character.isLetter(stringSequence.charAt(pos))) {
                acidsNumber++;
                prefMass += acidMasses[stringSequence.charAt(pos) - 'A'];
                if (!modified) {
                    ionsBList.add(new Ion('B', acidsNumber, prefMass));
                }
            } else {
                switch (stringSequence.charAt(pos)) {
                    case '(': {
                        modified = true;
                        break;
                    } case ')': {
                        pos += 2;
                        int closingPos = stringSequence.indexOf(']', pos);
                        prefMass += Double.valueOf(stringSequence.substring(pos, closingPos));
                        pos = closingPos + 1;
                        modified = false;
                        ionsBList.add(new Ion('B', acidsNumber, prefMass));
                        break;
                    }
                }
            }
        }
        double totalMass = prefMass;
        if (Character.isLetter(stringSequence.charAt(stringSequence.length() - 1))) {
            acidsNumber++;
            totalMass += acidMasses[
                    stringSequence.charAt(stringSequence.length() - 1) - 'A'];
        }
        for (Ion ionB: ionsBList) {
            ionsYList.add(new Ion('Y', acidsNumber - ionB.getNumber(),
                    totalMass - ionB.getMass() + DELTA_Y));
        }
        ions = new Ion[ionsBList.size() * 2];
        for (int i = 0; i < ionsBList.size(); i++) {
            ions[i] = ionsBList.get(i);
        }
        for (int i = 0; i < ionsYList.size(); i++) {
            ions[i + ionsBList.size()] = ionsYList.get(i);
        }
        Arrays.sort(ions, Ion.MASS_ASCENDING_ORDER);
    }
}
