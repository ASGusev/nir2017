public class Scan {
    private int id;
    private int prsmId;
    private int charge;
    private double precursorMass;

    public Scan(int id, int prsmId, int charge, double precursorMass) {
        this.id = id;
        this.prsmId = prsmId;
        this.charge = charge;
        this.precursorMass = precursorMass;
    }

    public int getId() {
        return id;
    }

    public int getPrsmId() {
        return prsmId;
    }

    public int getCharge() {
        return charge;
    }

    public double getPrecursorMass() {
        return precursorMass;
    }
}
