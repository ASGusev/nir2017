import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class for a scan prediction based on theoretical data.
 */
public class TheoreticScan extends Scan {
    private AminoAcid[] sequence;
    private Ion[] ions;
    private double eValue;
    private String stringSequence;
    private List<MassShift> modifications;

    public TheoreticScan(int id, int prsmId, int charge, double precursorMass,
                         double eValue, String stringSequence) {
        super(id, prsmId, charge, precursorMass);
        this.eValue = eValue;
        this.stringSequence = stringSequence;
    }

    /**
     * Gets all the b- and y-ions of the scan.
     * @return an array containing all the b- and y- ions that may
     * appear as peaks in a experimental range of the scan in ascending
     * order.
     */
    public Ion[] getIons() {
        if (ions == null) {
            makeIons();
        }
        return ions;
    }

    public double getEValue() {
        return eValue;
    }

    /**
     * Gets the amino acid sequence of the scan.
     * @return an array with all the amino acids of the peptide.
     */
    public AminoAcid[] getSequence() {
        return sequence;
    }

    /**
     * Gets the amino acid sequence in the string form.
     * @return a string representing the acid sequence of the peptide.
     */
    public String getStringSequence() {
        return stringSequence;
    }

    /**
     * Makes a stream containing all the scans from a table.
     * @param tablePath the path to the file with the table.
     * @return a stream with all the scans presented in the table.
     * @throws IOException if an error occurs during reading the table.
     */
    public static Stream<TheoreticScan> readTable(Path tablePath)
            throws IOException {
        return Files.lines(tablePath)
                .skip(1)
                .map(TheoreticScan::parseTableLine);
    }

    /**
     * Reads a table of theoretic scans and makes a map from the scan
     * ids to the TheoreticScan representations of them.
     * @param tablePath the path to the table to read.
     * @return map from the scan
     * ids to the TheoreticScan representations of them.
     * @throws IOException if an error during reading the table file
     * occurs.
     */
    public static Map<Integer, TheoreticScan> mapFromTable(Path tablePath)
            throws IOException {
        return readTable(tablePath).collect(Collectors.toMap(Scan::getId,
                scan -> scan));
    }

    /**
     * Gets a list of amino acid mass modifications.
     * @return a list containing MassShift representations of all the
     * modifications of this scan.
     */
    public List<MassShift> getModifications() {
        if (modifications == null) {
            modifications = new ArrayList<>();
            int acids = 0;
            int start = 0, end = 0;
            double mass = 0;
            for (int pos = 0; pos < stringSequence.length(); pos++) {
                if (stringSequence.charAt(pos) == '(') {
                    start = acids;
                } else if (stringSequence.charAt(pos) == ')') {
                    end = acids + 1;
                    pos += 2;
                    int massEndIndex = stringSequence.indexOf(']', pos);
                    mass = Double.valueOf(stringSequence.substring(pos,
                            massEndIndex));
                    modifications.add(new MassShift(start, end, mass));
                    pos = massEndIndex;
                } else {
                    acids++;
                }
            }
        }
        return modifications;
    }

    /**
     * A class representing a b- or y-ion of the peptide.
     */
    public static class Ion {
        private final char type;
        private final int number;
        private final double mass;

        public Ion(char type, int number, double mass) {
            this.type = type;
            this.number = number;
            this.mass = mass;
        }

        /**
         * Gets the type of the ion('B' or 'Y')
         * @return the type of the ion.
         */
        public char getType() {
            return type;
        }

        public int getNumber() {
            return number;
        }

        public double getMass() {
            return mass;
        }

        /**
         * A comparator for sorting ions in order of increasing mass.
         */
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

    /**
     * A class representing a modification of the amino sequence.
     */
    public class MassShift {
        private final int start;
        private final int end;
        private final double mass;

        public MassShift(int start, int end, double mass) {
            this.start = start;
            this.end = end;
            this.mass = mass;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public double getMass() {
            return mass;
        }
    }

    /**
     * Parses a line of a scan table.
     * @return a TheoreticScan representation of the scan described in
     * the line.
     */
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
                        prefMass += Double.valueOf(
                                stringSequence.substring(pos, closingPos));
                        pos = closingPos;
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
