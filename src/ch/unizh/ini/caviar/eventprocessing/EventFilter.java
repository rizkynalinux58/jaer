/*
 * AbstractEventFilter.java
 *
 * Created on October 30, 2005, 4:58 PM
 *
 * To change this template, choose Tools | Options and locate the template under
 * the Source Creation and Management node. Right-click the template and choose
 * Open. You can then make changes to the template in the Source Editor.
 */

package ch.unizh.ini.caviar.eventprocessing;

import ch.unizh.ini.caviar.chip.*;
import java.beans.*;
import java.io.File;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.prefs.*;

/**
 * An abstract class that all filters should subclass.
 Subclasses are introspected to build a GUI to control the filter in {@link FilterPanel}.
 Filters that are enclosed inside another filter are given a
 preferences node that is derived from
 the enclosing filter class name.
 *<p>
 *@see FilterPanel
 * @author tobi
 */
public abstract class EventFilter {
    
    public EventProcessingPerformanceMeter  perf;
    
    /** The preferences for this filter, by default in the EventFilter package node
     @see setEnclosed
     */
    private Preferences prefs=Preferences.userNodeForPackage(EventFilter.class);
    
    /** Can be used to provide change support, e.g. for enabled state */
    protected PropertyChangeSupport support=new PropertyChangeSupport(this);
    
    /** All filters can log to this logger */
    protected Logger log=Logger.getLogger("EventFilter");
    
    /** true if filter is enclosed by another filter */
    private boolean enclosed=false;
    
    /** Used by filterPacket to say whether to filter events; default false */
    protected boolean filterEnabled=false;
    
//    /** true means the events are filtered in place, replacing the contents of the input packet and more
//     *efficiently using memory. false means a new event packet is created and populated for the output of the filter.
//     *<p>
//     *default is false
//     */
//    protected boolean filterInPlaceEnabled=false;
    
    /** chip that we are filtering for */
    protected AEChip chip;
    
    /** default constructor
     @deprecated - all filters need an AEChip object
     */
    public EventFilter(){
//        perf=new EventProcessingPerformanceMeter(this);
//        setFilterEnabled(prefs.getBoolean(prefsKey(),false)); // this cannot easily be called here because it will be called during init of subclasses which have
        // not constructed themselves fully yet, e.g. field objects will not have been constructed. therefore, we set initial active states of all filters in FilterFrame after they are
        // all constructed with a Chip object.
    }
    
    /** Creates a new instance of AbstractEventFilter but does not enable it.
     *@param chip the chip to filter for
     @see #setPreferredEnabledState
     */
    public EventFilter(AEChip chip){
        this.chip=chip;
        try{
            prefs=Preferences.userNodeForPackage(getClass());
            // are we being constructed by the initializer of an enclosing filter?
            // if so, we should set up our preferences node so that we use a preferences node
            // that is unique for the enclosing filter
            checkEnclosed();
        }catch(Exception e){
            log.warning(e.getMessage());
        }
    }
    
    /** Returns the prefernces key for the filter
     * @return "<SimpleClassName>.filterEnabled" e.g. DirectionSelectiveFilter.filterEnabled
     */
    public String prefsEnabledKey(){
        String key=this.getClass().getSimpleName()+".filterEnabled";
        return key;
    }
    
//    /**
//     * filters in to out. if filtering is enabled, the number of out may be less
//     * than the number put in
//     *@param in input events can be null or empty.
//     *@return the processed events, may be fewer in number. filtering may occur in place in the in packet.
//     */
//    abstract public AEPacket filter(AEPacket in) ;
    
//    abstract public ch.unizh.ini.caviar.aemonitor.AEPacket2D filter(ch.unizh.ini.caviar.aemonitor.AEPacket2D in);
    /** should return the filter state in some useful form
     * @deprecated - no one uses this
     */
    abstract public Object getFilterState() ;
    
    /** should reset the filter to initial state */
    abstract public void resetFilter() ;
    
    /** this should allocate and initialize memory: it may be called when the chip e.g. size parameters are changed after creation of the filter */
    abstract public void initFilter();
    
    /** Filters can be enabled for processing.
     * @return true if filter is enabled */
    synchronized public boolean isFilterEnabled() {
        return filterEnabled;
    }
    
    /** Filters can be enabled for processing. Setting enabled also sets an enclosed filter to the same state.
     Setting filter enabled state only stores the preference value for enabled state
     *if the filter is not enclosed inside another filter, to avoid setting global preferences for the filter enabled state.
     Fires a property change event so that GUIs can be updated.
     * @param enabled true to enable filter. false means output events are the same as input
     @see #setPreferredEnabledState
     */
    synchronized public void setFilterEnabled(boolean enabled) {
        boolean wasEnabled=this.filterEnabled;
        this.filterEnabled=enabled;
        if(getEnclosedFilter()!=null){
            getEnclosedFilter().setFilterEnabled(filterEnabled);
        }
        if(getEnclosedFilterChain()!=null){
            for(EventFilter f:getEnclosedFilterChain()) {
                f.setFilterEnabled(enabled);
            }
        }
//        log.info(getClass().getName()+".setFilterEnabled("+filterEnabled+")");
        if(!isEnclosed()){
            String key=prefsEnabledKey();
            prefs.putBoolean(key, enabled);
        }
        support.firePropertyChange("filterEnabled",new Boolean(wasEnabled),new Boolean(enabled));
    }
    
    /** Sets the filter enabled according to the preference for enabled */
    public void setPreferredEnabledState(){
        setFilterEnabled( prefs.getBoolean(prefsEnabledKey(),filterEnabled) );
    }
    
    /** @return the chip this filter is filtering for */
    public AEChip getChip() {
        return chip;
    }
    
    /** @param chip the chip to filter */
    public void setChip(AEChip chip) {
        this.chip = chip;
    }
    
    public PropertyChangeSupport getPropertyChangeSupport(){
        return support;
    }
    
//    /** @deprecated - no one uses this */
//    public boolean isFilterInPlaceEnabled() {
//        return this.filterInPlaceEnabled;
//    }
//
//    /** @deprecated - not used */
//    public void setFilterInPlaceEnabled(final boolean filterInPlaceEnabled) {
//        support.firePropertyChange("filterInPlaceEnabled",new Boolean(this.filterInPlaceEnabled),new Boolean(filterInPlaceEnabled));
//        this.filterInPlaceEnabled = filterInPlaceEnabled;
//    }
    
    /** The enclosed single filter. This object is used for GUI building - any processing must be handled in filterPacket */
    protected EventFilter enclosedFilter;
    
    /** An enclosed filterChain - these filters must be applied in the filterPacket method but a GUI for them is automagically built */
    private FilterChain enclosedFilterChain;
    
    
    /** Gets the enclosed filter
     * @return the enclosed filter
     */
    public EventFilter getEnclosedFilter() {
        return this.enclosedFilter;
    }
    
    /** Sets another filter to be enclosed inside this one - this enclosed filter should be applied first and must be applied by the filter.
     *This enclosed filter is displayed hierarchically in the FilterPanel used in FilterFrame.
     * @param enclosedFilter the filter to enclose
     * @see #setEnclosed
     */
    public void setEnclosedFilter(final EventFilter enclosedFilter) {
        this.enclosedFilter = enclosedFilter;
        if(enclosedFilter!=null) enclosedFilter.setEnclosed(true);
    }
    
    protected boolean annotationEnabled=true;
    
    public boolean isAnnotationEnabled() {
        return annotationEnabled;
    }
    
    /**@param annotationEnabled true to draw annotations */
    public void setAnnotationEnabled(boolean annotationEnabled) {
        this.annotationEnabled = annotationEnabled;
    }
    
    /** Is filter enclosed inside another filter?
     * @return true if this filter is enclosed inside another */
    public boolean isEnclosed() {
        return enclosed;
    }
    
    /** Sets flag to show this instance is enclosed. If this flag is set to true, then
     preferences node is changed to a node unique for the enclosing filter class.
     *
     * @param enclosed true if this filter is enclosed
     */
    public void setEnclosed(boolean enclosed) {
        this.enclosed = enclosed;
        // find class of calling filter
        
//        if(enclosed){
//            StackTraceElement[] ste=Thread.currentThread().getStackTrace();
//            final int stackNum=3;
//            if(ste.length>stackNum+1 && ste[stackNum]!=null){
//            }
//        }
    }
    
    /** The key,value table of property tooltip strings */
    protected HashMap<String,String> propertyTooltipMap=null;
    
    /** Developers can use this to add an optional tooltip for a filter property so that the tip is shown
     * as the tooltip for the label or checkbox property in the generated GUI
     * @param propertyName the name of the property (e.g. an int, float, or boolean, e.g. "dt")
     * @param tooltip the tooltip String to display
     */
    protected void setPropertyTooltip(String propertyName, String tooltip){
        if(propertyTooltipMap==null) propertyTooltipMap=new HashMap<String,String>();
        propertyTooltipMap.put(propertyName, tooltip);
    }
    
    /** @return the tooltip for the property */
    protected String getPropertyTooltip(String propertyName){
        if(propertyTooltipMap==null) return null;
        return propertyTooltipMap.get(propertyName);
    }
    
    /** Returns the enclosed filter chain
     *@return the chain
     **/
    public FilterChain getEnclosedFilterChain() {
        return enclosedFilterChain;
    }
    
    /** Sets an enclosed filter chain which should by convention be processed first by the filter (but need not be).
     Also flags all the filters in the chain as enclosed.
     *@param enclosedFilterChain the chain
     **/
    public void setEnclosedFilterChain(FilterChain enclosedFilterChain) {
        this.enclosedFilterChain = enclosedFilterChain;
        if(enclosedFilterChain!=null){
            for(EventFilter f:enclosedFilterChain){
                f.setEnclosed(true);
            }
        }
    }
    
    /** Checks if we are being constucted by another filter's initializer. If so, make a new
     prefs node that is derived from the enclosing filter class name.
     */
    private void checkEnclosed() {
        // if we are being constucted inside another filter's init, then after we march
        // down the stack trace and find ourselves, the next element should be another
        // filter's init
        
//        Thread.currentThread().dumpStack();
        StackTraceElement[] trace=Thread.currentThread().getStackTrace();
        boolean next=false;
        String enclClassName=null;
        for(StackTraceElement e:trace){
            if(e.getMethodName().contains("<init>")){
                if(next){
                    enclClassName=e.getClassName();
                    break;
                }
                if(e.getClassName().equals(getClass().getName())){
                    next=true;
                }
            }
        }
//        System.out.println("enclClassName="+enclClassName);
        try{
            if(enclClassName!=null){
                Class enclClass=Class.forName(enclClassName);
                if(EventFilter.class.isAssignableFrom(enclClass)){
                    prefs=getPrefsForEnclosedFilter(enclClassName);
                    log.info("This filter "+this.getClass()+" is enclosed in "+enclClass+" and has new Preferences node="+prefs);
                }
            }
        }catch(ClassNotFoundException e){
            e.printStackTrace();
        }
    }
    
    Preferences getPrefsForEnclosedFilter(String enclClassName){
        int clNaInd=enclClassName.lastIndexOf(".");
        enclClassName=enclClassName.substring(clNaInd,enclClassName.length());
        String prefsPath=prefs.absolutePath()+enclClassName.replace(".","/");
        Preferences prefs=Preferences.userRoot().node(prefsPath);
        return prefs;
    }
    
    /** Returns the Preferences node for this filter. This node is based on the filter class package
     but may be modified to a sub-node if the filter is enclosed inside another filter.
     @return the preferences node
     @see #setEnclosed
     */
    public Preferences getPrefs() {
        return prefs;
    }
    
    /** Sets the preferences node for this filter
     @param prefs the node
     */
    public void setPrefs(Preferences prefs) {
        this.prefs = prefs;
    }
    
}
