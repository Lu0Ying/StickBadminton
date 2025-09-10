package org.stickbadminton;

import javafx.scene.Node;

public abstract class UIObject {
    public abstract Node getUINode();
    private Room parentRoom;
    private int x;
    private int y;
    public int getX() { return x; }
    public int getY() { return y; }
    public Room getParentRoom() { return parentRoom; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setParentRoom(Room parentRoom) { this.parentRoom = parentRoom; }
}
