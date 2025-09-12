package org.stickbadminton;

import com.almasb.fxgl.dsl.FXGL;
import javafx.scene.Node;
import javafx.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Room {
    private List<GameObject> objects = new ArrayList<>();
    private List<UIObject> uiObjects = new ArrayList<>();
    private List<Pair<Node, Pair<Integer, Integer>>> uiNodes =  new ArrayList<>();
    private boolean isActive = false;

    public Room() {}

    public GameObject addObject(GameObject gameObject) {
        gameObject.inRoom = this;
        objects.add(gameObject);
        if (isActive) {
            FXGL.getGameWorld().addEntity(gameObject.entity);
        }
        return gameObject;
    }

    public GameObject addObject(GameObject gameObject, String name) {
        gameObject.name = name;
        gameObject.inRoom = this;
        objects.add(gameObject);
        if (isActive) {
            gameObject.activate();
        }
        return gameObject;
    }

    public void removeObject(GameObject gameObject) {
        objects.remove(gameObject);
        if (isActive) {
            gameObject.deactivate();
        }
        gameObject.inRoom = null;
    }

    public void removeObjectFromList(GameObject gameObject) {
        objects.remove(gameObject);
    }

    public void addUiNode(Node node, int x, int y) {
        uiNodes.add(new Pair(node, new Pair<>(x, y)));
        if (isActive) {
            FXGL.addUINode(node, x, y);
        }
    }

    public void removeUiNode(Node node) {
        uiNodes.forEach( pack -> {
            if (pack.getKey() == node) {
                if (isActive) {
                    FXGL.removeUINode(node);
                }
                uiNodes.remove(pack);
            }
        });
    }

    public UIObject addUiObject(UIObject uiObject, int x, int y) {
        addUiNode(uiObject.getUINode(), x, y);
        uiObject.setX(x);
        uiObject.setY(y);
        uiObject.setParentRoom(this);
        uiObjects.add(uiObject);
        return uiObject;
    }

    public void removeUiObject(UIObject uiObject) {
        removeUiNode(uiObject.getUINode());
        uiObjects.remove(uiObject);
    }

    public void enter() {
        objects.forEach(o -> o.activate());
        isActive = true;
        uiNodes.forEach( pack -> FXGL.addUINode(pack.getKey(),
                pack.getValue().getKey(), pack.getValue().getValue()));
    }

    public void leave() {
        objects.forEach(o -> o.deactivate());
        isActive = false;
        uiNodes.forEach( pack -> FXGL.removeUINode(pack.getKey()));
    }

    @Nullable
    public GameObject getObject(String name) {
        for (GameObject o : objects) {
            if (o.name != null && o.name.equals(name)) {
                return o;
            }
        }
        return null;
    }

    @Nullable
    public UIObject getUiObject(String name) {
        for (UIObject uiObject : uiObjects) {
            if (uiObject.getName() != null && uiObject.getName().equals(name)) {
                return uiObject;
            }
        }
        return null;
    }

    public List<GameObject> getObjectList(String name) {
        List<GameObject> result = new ArrayList<>();
        for (GameObject o : objects) {
            if (o.name.equals(name)) {
                result.add(o);
            }
        }
        return result;
    }
}
