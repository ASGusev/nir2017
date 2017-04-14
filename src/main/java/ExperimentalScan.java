public class ExperimentalScan extends Scan {
    private double[] peaks;

    public ExperimentalScan(int id, int prsmId, int charge,
                            double precursorMass, double[] peaks) {
        super(id, prsmId, charge, precursorMass);
        this.peaks = peaks;
    }

    public double[] getPeaks() {
        return peaks;
    }
}
