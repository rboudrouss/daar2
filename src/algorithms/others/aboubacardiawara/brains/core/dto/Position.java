package algorithms.others.aboubacardiawara.brains.core.dto;

/**
 * Position
 */
public class Position {

    private double x;
    private double y;

    public Position(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public static Position of(double x, double y) {
        return new Position(x, y);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double distanceTo(Position position) {
        return Math.sqrt(Math.pow(position.getX() - x, 2) + Math.pow(position.getY() - y, 2));
    }

    public double distanceToLine(double x1, double y1, double x2, double y2) {
        // calculer la pente
        double m = (y2 - y1) / (x2 - x1);
        // calculer l'ordonnée à l'origine
        double b = y1 - m * x1;
        // calculer la distance entre le point (x, y) et la ligne y = mx + b
        return Math.abs(m * x - y + b) / Math.sqrt(Math.pow(m, 2) + 1);
    }

    public boolean pointBelongToLine(double x1, double y1, double x2, double y2, double epsilon) {
        return distanceToLine(x1, y1, x2, y2) < epsilon;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof Position))
            return false;
        Position position = (Position) obj;
        return position.getX() == x && position.getY() == y;
    }

}