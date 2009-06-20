package net.sf.jaer.chip;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.jaer.eventprocessing.FilterFrame;
import net.sf.jaer.graphics.*;
import net.sf.jaer.graphics.ChipCanvas;

/**
 * A Chip with a 2D (or 1D) array of pixels.
 *
 * @author tobi
 */
public class Chip2D extends Chip {

    /** Creates a new instance of Chip2D */
    public Chip2D() {
        super();
        setRenderer(new Chip2DRenderer(this));
        setCanvas(new ChipCanvas(this));
    }
    protected int sizeX = 0;
    protected int sizeY = 0;
    protected int numCellTypes = 0;
    protected ChipCanvas canvas = null;
    private Chip2DRenderer renderer = null;
    /** the filter frame holding filters that can be applied to the events */
    protected FilterFrame filterFrame = null;

    /** Size of chip in x (horizontal) direction.
     *
     * @return number of pixels.
     */
    public int getSizeX() {
        return sizeX;
    }

    /** Updates the chip size and calls Observers with the string "sizeX".
    @param sizeX the horizontal dimension
     */
    public void setSizeX(int sizeX) {
        this.sizeX = sizeX;
        setChanged();
        notifyObservers("sizeX");
    }

    /** Size of chip in y (vertical) direction.
     *
     * @return number of pixels.
     */
    public int getSizeY() {
        return sizeY;
    }

    /** Updates the chip size and calls Observers with the string "sizeY".
    @param sizeY the vertical dimension
     */
    public void setSizeY(int sizeY) {
        this.sizeY = sizeY;
        setChanged();
        notifyObservers("sizeY");
    }

    public int getMaxSize() {
        return (int) Math.max(sizeX, sizeY);
    }

    public int getMinSize() {
        return (int) Math.min(sizeX, sizeY);
    }

    /** Total number of cells on the chip; sizeX*sizeY*numCellTypes.
     *
     * @return number of cells.
     * @see #getNumPixels
     */
    public int getNumCells() {
        return sizeX * sizeY * numCellTypes;
    }

    /** Number of pixels; sizeX*sizeY
     *
     * @return number of pixels.
     * @see #getNumCells
     */
    public int getNumPixels() {
        return sizeX * sizeY;
    }

    /** The ChipCanvas that renders this Chip2D's output.
     *
     * @return the ChipCanvas.
     */
    public ChipCanvas getCanvas() {
        return canvas;
    }

    /** sets the ChipCanvas for this AEChip. 
     * Notifies observers (e.g. EventFilter2D) of this chip with the new ChipCanvas object
     * in case they need to do anything in response, e.g.
    add FrameAnnotater.
     */
    public void setCanvas(ChipCanvas canvas) {
        this.canvas = canvas;
        setChanged();
        notifyObservers(canvas);
    }

    /** Sets the name of the chip and sets the FilterFrame (if there is one) with a new title */
    public void setName(String name) {
        super.setName(name);
        if (filterFrame != null) {
            filterFrame.setTitle(getName() + " filters");
        }
    }

//    public FilterChain getRealTimeFilterChain() {
//        return realTimeFilterChain;
//    }
//
//    public void setRealTimeFilterChain(FilterChain realTimeFilterChain) {
//        this.realTimeFilterChain = realTimeFilterChain;
//    }
    public float getPixelWidthUm() {
        return pixelWidthUm;
    }

    public void setPixelWidthUm(float pixelWidthUm) {
        this.pixelWidthUm = pixelWidthUm;
    }

    public float getPixelHeightUm() {
        return pixelHeightUm;
    }

    public void setPixelHeightUm(float pixelHeightUm) {
        this.pixelHeightUm = pixelHeightUm;
    }
    private float pixelWidthUm = 10;
    private float pixelHeightUm = 10;

    public Chip2DRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(Chip2DRenderer renderer) {
        this.renderer = renderer;
    }

    /** This string key is where the chip's preferred display method is stored.
     *
     * @return the key
     */
    private String preferredDisplayMethodKey() { // TODO shouldn't need this public method, should put display method inside chip not ChipCanvas maybe
        return getClass() + ".preferredDisplayMethod";
    }

    /** Sets the preferrred DisplayMethod for this Chip2D. This method is the one intially used after startup.
     *
     * @param clazz the method.
     */
    public void setPreferredDisplayMethod(Class<? extends DisplayMethod> clazz) {
        if (clazz == null) {
            log.warning("null class name, not storing preference");
            return;
        }
        // store the preferred method
        getPrefs().put(preferredDisplayMethodKey(), clazz.getName());
        log.info("set preferred diplay method to be "+clazz.getName());
    }

    /** Returns the preferred DisplayMethod, or ChipRendererDisplayMethod if null preference.
     *
     * @return the method, or null.
     * @see #setPreferredDisplayMethod
     */
    public Class getPreferredDisplayMethod(){
        try {
            String className = getPrefs().get(preferredDisplayMethodKey(), null);
            if (className == null) {
                return ChipRendererDisplayMethod.class;
            }
            Class c = Class.forName(className);
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            log.warning("couldn't find preferred display method, returning default ChipRendererDisplayMethod");
            return ChipRendererDisplayMethod.class;
        }
    }
}
