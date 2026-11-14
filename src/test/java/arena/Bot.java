package arena;

import java.util.Random;

public abstract class Bot {
    public final String name;
    public Random rnd;
    /** Skin and hair sent as LOOK on the first turn, or null. */
    public int[] look;
    protected Bot(String n) { name = n; }
    public abstract String act(Obs o);
}
