/**
 * A class representing an amino acid with letter code and mass.
 */
public enum AminoAcid {
    A ('A', 71.03711),
    R ('R', 156.10111),
    N ('N', 114.04293),
    D ('D', 115.02694),
    C ('C', 103.00919),
    E ('E', 129.04259),
    Q ('Q', 128.05858),
    G ('G', 57.02146),
    H ('H', 137.05891),
    I ('I', 113.08406),
    L ('L', 113.08406),
    K ('K', 128.09496),
    M ('M', 131.04049),
    F ('F', 147.06841),
    P ('P', 97.05276),
    S ('S', 87.03203),
    T ('T', 101.04768),
    W ('W', 186.07931),
    Y ('Y', 163.06333),
    V ('V', 99.06841);

    private final Character letterCode;
    private final double mass;
    private static double[] masses;

    AminoAcid(Character letterCode, double mass) {
        this.letterCode = letterCode;
        this.mass = mass;
    }

    /**
     * Gets the letter code off the acid.
     * @return the letter code off the acid.
     */
    public Character getLetterCode() {
        return letterCode;
    }

    /**
     * Gets the mass of the acid.
     * @return the mass of the acid.
     */
    public double getMass() {
        return mass;
    }

    /**
     * Makes an array containing masses of the acids. Each mass is
     * stored at the position of its letter in the alphabet
     * (letter - 'A').
     * @return an array with all amino acid masses.
     */
    public static double[] getMasses() {
        if (masses == null) {
            masses = new double[26];
            for (AminoAcid acid: AminoAcid.values()) {
                masses[acid.getLetterCode() - 'A'] = acid.getMass();
            }
        }
        return masses;
    }
}
