package algorithms.BoudroussDurbin;

import java.util.Objects;

import characteristics.IRadarResult.Types;

public class UtilsBoudroussDurbin {

}

class Bullet {
    double x, y;
    double vx, vy;
    boolean hasVelocity;

    Bullet(double x, double y) {
        this.x = x;
        this.y = y;
        this.hasVelocity = false;
    }
}

class BotState {
    private PositionDurbin positionDurbin = new PositionDurbin(0, 0);
    private boolean isAlive = true;

    public BotState() {
    }

    public BotState(double x, double y, boolean alive) {
        positionDurbin.setX(x);
        positionDurbin.setY(y);
        isAlive = alive;
    }

    public void setPosition(double x, double y) {
        positionDurbin.setX(x);
        positionDurbin.setY(y);
    }

    public PositionDurbin getPosition() {
        return positionDurbin;
    }

    public void setAlive(boolean alive) {
        isAlive = alive;
    }

    public boolean isAlive() {
        return isAlive;
    }
}

class PositionDurbin {
    private double x;
    private double y;

    public PositionDurbin(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public String toString() {
        return "X : " + x + "; Y : " + y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        PositionDurbin other = (PositionDurbin) obj;
        return Double.doubleToLongBits(x) == Double.doubleToLongBits(other.x)
                && Double.doubleToLongBits(y) == Double.doubleToLongBits(other.y);
    }
}

class Enemy {
    // xHistory/yHistory[0]=current, [1]=previous, [2]=prevPrevious
    double[] xHistory = new double[3];
    double[] yHistory = new double[3];
    double distance, direction;
    Types type;
    double speedX, speedY;
    boolean hasVelocityData;
    double predictedX, predictedY;

    public Enemy(double x, double y, double distance, double direction, Types type) {
        xHistory[0] = xHistory[1] = xHistory[2] = x;
        yHistory[0] = yHistory[1] = yHistory[2] = y;
        this.distance = distance;
        this.direction = direction;
        this.type = type;
        this.speedX = 0;
        this.speedY = 0;
        this.hasVelocityData = false;
        this.predictedX = x;
        this.predictedY = y;
    }

    public void updatePosition(double newX, double newY, double newDistance, double newDirection) {
        xHistory[2] = xHistory[1];
        yHistory[2] = yHistory[1];
        xHistory[1] = xHistory[0];
        yHistory[1] = yHistory[0];

        xHistory[0] = newX;
        yHistory[0] = newY;
        this.distance = newDistance;
        this.direction = newDirection;

        if (hasVelocityData) {
            this.speedX = xHistory[0] - xHistory[1];
            this.speedY = yHistory[0] - yHistory[1];
        } else if (xHistory[0] != xHistory[1] || yHistory[0] != yHistory[1]) {
            hasVelocityData = true;
        }
    }

    public void predictPosition(double bulletTravelTime) {
        if (!hasVelocityData) {
            this.predictedX = xHistory[0];
            this.predictedY = yHistory[0];
        } else {
            double prevDx = xHistory[1] - xHistory[2];
            double prevDy = yHistory[1] - yHistory[2];
            double currentDx = xHistory[0] - xHistory[1];
            double currentDy = yHistory[0] - yHistory[1];

            boolean isOscillatingX = (prevDx * currentDx < 0);
            boolean isOscillatingY = (prevDy * currentDy < 0);

            if (isOscillatingX || isOscillatingY) {
                this.predictedX = (xHistory[0] + xHistory[1]) / 2;
                this.predictedY = (yHistory[0] + yHistory[1]) / 2;
            } else {
                this.predictedX = xHistory[0] + speedX * bulletTravelTime;
                this.predictedY = yHistory[0] + speedY * bulletTravelTime;
            }
        }
    }

    public double getX() {
        return xHistory[0];
    }

    public double getY() {
        return yHistory[0];
    }

    public double getPredictedX() {
        return predictedX;
    }

    public double getPredictedY() {
        return predictedY;
    }
}
