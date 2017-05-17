public class Peak {
    private final TheoreticScan scan;
    private final TheoreticScan.Ion ion;

    public Peak(TheoreticScan scan, TheoreticScan.Ion ion) {
        this.scan = scan;
        this.ion = ion;
    }

    public TheoreticScan getScan() {
        return scan;
    }

    public TheoreticScan.Ion getIon() {
        return ion;
    }
}
