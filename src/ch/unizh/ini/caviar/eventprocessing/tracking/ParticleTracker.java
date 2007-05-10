/*
 * ParticleTracker.java
 *
 * Created on 30. april 2007, 16:18
 *
 */

package ch.unizh.ini.caviar.eventprocessing.tracking;
//import ch.unizh.ini.caviar.aemonitor.AEConstants;
import ch.unizh.ini.caviar.chip.*;
import ch.unizh.ini.caviar.eventprocessing.EventFilter2D;
import ch.unizh.ini.caviar.event.*;
import ch.unizh.ini.caviar.event.EventPacket;
import ch.unizh.ini.caviar.graphics.*;
import com.sun.opengl.util.*;
import java.awt.*;
//import ch.unizh.ini.caviar.util.PreferencesEditor;
import java.awt.geom.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;
import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;


/**
 *
 * @author Philipp <hafliger@ifi.uio.no>
 */
public class ParticleTracker extends EventFilter2D implements FrameAnnotater, Observer{

    private java.util.List<Cluster> clusters=new LinkedList<Cluster>();
    protected AEChip chip;
    private AEChipRenderer renderer;
    private int[][] lastCluster= new int[128][128];
    private int[][] lastEvent= new int[128][128];
    private final int CLUSTER_UNSUPPORTED_LIFETIME=100000;
    private final int CLUSTER_MINLIFEFORCE_4_DISPLAY=10;
    private int next_cluster_id=1;
    protected Random random=new Random();

   
    /** Creates a new instance of ParticleTracker */
    public ParticleTracker(AEChip chip) {
        super(chip);
        this.chip=chip;
        renderer=(AEChipRenderer)chip.getRenderer();
        chip.getRenderer().addAnnotator(this); // to draw the clusters
        chip.getCanvas().addAnnotator(this);
        chip.addObserver(this);
//        prefs.addPreferenceChangeListener(this);
//        Cluster newCluster=new Cluster(40,60,-1,-1);
//        clusters.add(newCluster);
        initFilter();
    }

    // the method that actually does the tracking
    synchronized private void track(EventPacket<BasicEvent> ae){
        int n=ae.getSize();
        if(n==0) return;
        int l,k,i,ir,il,j,jr,jl;
        //int most_recent;
        //LinkedList<Cluster> pruneList=new LinkedList<Cluster>();
        int[] cluster_ids=new int[9];
        Cluster thisCluster=null;
        int[] merged_ids=null;
        float thisClusterWeight;
        float thatClusterWeight;
        ListIterator listScanner;
        Cluster c=null;
        //int maxNumClusters=getMaxNumClusters();
        
        // for each event, see which cluster it is closest to and add it to this cluster.
        // if its too far from any cluster, make a new cluster if we can
//        for(int i=0;i<n;i++){
        for(BasicEvent ev:ae){
            // check if any neigbours are assigned to a cluster already
            if (ev.x==0){il=0;}else{il=-1;}
            if (ev.x==127){ir=0;}else{ir=1;}
            if (ev.y==0){jl=0;}else{jl=-1;}
            if (ev.y==127){jr=0;}else{jr=1;}
            //most_recent=-1;
            k=0;
            for (i=il;i<=ir;i++){
                for(j=jl;j<=jr;j++){
                    if (lastEvent[ev.x+i][ev.y+j] != -1){
                        //if (lastEvent[ev.x+i][ev.y+j]>most_recent){
                            //most_recent=lastEvent[ev.x+i][ev.y+j];
                            //lastCluster[ev.x][ev.y]=lastCluster[ev.x+i][ev.y+j];
                        //}
                        if (lastEvent[ev.x+i][ev.y+j] >= ev.timestamp-CLUSTER_UNSUPPORTED_LIFETIME){
                            lastCluster[ev.x][ev.y]=lastCluster[ev.x+i][ev.y+j];
                            cluster_ids[k]=lastCluster[ev.x+i][ev.y+j]; // an existing cluster id at or around the event
                            k++;
                            for (l=0;l<(k-1);l++){ // checking if its a doublicate
                                if (cluster_ids[k-1]==cluster_ids[l]){
                                    k--;
                                    break;
                                } 
                            }
                        }
                    }
                }
            }
            lastEvent[ev.x][ev.y]=ev.timestamp;
/***************************************************************************************************************/
            if (k==0){// new cluster
                //if (next_cluster_id<200){
                    lastCluster[ev.x][ev.y]=next_cluster_id;
                    thisCluster=new Cluster(ev.x,ev.y,ev.timestamp);
                    clusters.add(thisCluster);
                //}
/***************************************************************************************************************/
            }else{// existing cluster: new event of one or several existing cluster
                listScanner=clusters.listIterator();
                while (listScanner.hasNext()){ // add new event to cluster
                    c=(Cluster)listScanner.next();
                    if ((c.last < ev.timestamp- CLUSTER_UNSUPPORTED_LIFETIME)||(c.last > ev.timestamp)){ //check if cluster is dead or if time has moved backwards
                        listScanner.remove();
                    }else{ //if cluster is still alive
                        for (l=0;l<c.id.length;l++){
                            if (c.id[l]==lastCluster[ev.x][ev.y]){// check if this event belongs to this cluster
                                c.addEvent(ev);
                                thisCluster=c;
                                //break;
                            }
                        }
                    }
                }
/***************************************************************************************************************/
                if (k>1){ //merge clusters if there has been more alive clusters in neighbourhood
                    mergeClusters(thisCluster, cluster_ids, k, ev.timestamp);
                }
                /************************************/
                //clusters.removeAll(pruneList);
                //pruneList.clear();
            }
        }
    }
    
/**************************************************************************************************************************************/
    
    public int mergeClusters(Cluster thisCluster, int[] cluster_ids, int n_ids, int now){
        ListIterator listScanner;
        Cluster c=null;
        int i,j,l;
        int c_count=1;
        int[] merged_ids;
        float thisClusterWeight,thatClusterWeight;

                        if (thisCluster!=null){
                        listScanner=clusters.listIterator();
                        while (listScanner.hasNext()){ // look for the clusters to be merged
                            c=(Cluster)listScanner.next();
                            if (c!=thisCluster){
                                for (i=0;i<c.id.length;i++){
                                    for (j=0;j<n_ids;j++){
                                        if (c.id[i]== cluster_ids[j]){
                                            c_count++;
                                            merged_ids=new int[c.id.length + thisCluster.id.length];
                                            System.out.println("******cluster merging: "+(c.id.length+ thisCluster.id.length));
                                            for (l=0;l<thisCluster.id.length;l++){
                                                merged_ids[l]=thisCluster.id[l];
                                            }
                                            for (l=0;l<(c.id.length);l++){
                                                merged_ids[l+thisCluster.id.length]= c.id[l];
                                            }
                                            for (l=0;l<(c.id.length+ thisCluster.id.length);l++){
                                                System.out.print(" "+merged_ids[l]);
                                            }
                                            System.out.println();
                                            thisCluster.id=merged_ids;
                                            c.lifeForce= c.lifeForce*(float)Math.exp(-(float)(now- c.last)/CLUSTER_UNSUPPORTED_LIFETIME);
                                            thisClusterWeight=thisCluster.lifeForce/ (thisCluster.lifeForce + c.lifeForce);
                                            thatClusterWeight=1-thisClusterWeight;
                                            thisCluster.location.x=thisClusterWeight*thisCluster.location.x+thatClusterWeight*c.location.x;
                                            thisCluster.location.y=thisClusterWeight*thisCluster.location.y+thatClusterWeight*c.location.y;
                                            thisCluster.velocity.x=thisClusterWeight*thisCluster.velocity.x+thatClusterWeight*c.velocity.x;
                                            thisCluster.velocity.y=thisClusterWeight*thisCluster.velocity.y+thatClusterWeight*c.velocity.y;
                                            thisCluster.lifeForce=thisCluster.lifeForce + c.lifeForce;
                                            j=n_ids;
                                            i=c.id.length;
                                            listScanner.remove();
                                        }
                                    }
                                }
                            }
                        }
                    }else{
                        log.warning("null thisCluster in ParticleTracker.mergeClusters");
                    }
        return(c_count);

    }
/**************************************************************************************************************************************/
    public int diffuseCluster(int x, int y, int id, int time_limit,int lowest_id){
        int i,j,t,most_recent;
        
        if ((x>=0)&&(x<128)&&(y>=0)&&(y<128)&&(lastCluster[x][y]<lowest_id)&&(lastEvent[x][y]>=time_limit)){
            lastCluster[x][y]=id;
            most_recent=lastEvent[x][y];
            for (i=-1;i<2;i++){
                for(j=-1;j<2;j++){
                    t=diffuseCluster(x+i,y+j,id,time_limit,lowest_id);
                    if (t>most_recent){
                        most_recent=t;
                    }
                }
            }
            return(most_recent);
        }else{
            return(-1);
        }
    }
/**************************************************************************************************************************************/
    
    public int splitClusters(){
        int split_count=0;
        int local_split_count=0;
        ListIterator clusterScanner,old_new_id_scanner;
        Cluster c,new_c;
        int now=-1;
        int new_clusters_from;
        int t=-1;
        int x,y,time_limit,i,new_id,old_id;
        int[] old_cluster_id = new int[1];
        int[] old_ids,new_ids;
        class OldNewId{
            int o;
            int n;
            int t; //last event timestamp in this cluster
        }
        java.util.List<OldNewId> old_new_id_list= new java.util.LinkedList<OldNewId>();
        OldNewId old_new_id;
        int this_pixel_old_id;
        
        clusterScanner=clusters.listIterator();
        while (clusterScanner.hasNext()){ // check for the most recent event timestamp
            c=(Cluster)clusterScanner.next();
            if (c.last> now) now=c.last;
        }
        time_limit=now-CLUSTER_UNSUPPORTED_LIFETIME;
        if (time_limit<0) time_limit=0;
        new_clusters_from=next_cluster_id;
        for (x=0;x<128;x++){
            for (y=0;y<128;y++){
                this_pixel_old_id=lastCluster[x][y];
                t=diffuseCluster(x,y,next_cluster_id,time_limit,new_clusters_from);
                if (t>-1){
                    old_new_id= new OldNewId();
                    old_new_id.o = this_pixel_old_id;
                    old_new_id.n = next_cluster_id;
                    old_new_id.t = t;
                    old_new_id_list.add(old_new_id);
                    next_cluster_id++;                    
                }else{
                    //lastCluster[x][y]=-1;
                }
            }
        }
        clusterScanner=clusters.listIterator();
        while (clusterScanner.hasNext()){ // get all old clusters, assign new ids and split if necessary
            c=(Cluster)clusterScanner.next();
            if ((c.last < time_limit)||(c.last > now)){ //check if cluster is dead or if time has moved backwards
                clusterScanner.remove();
            }else{
                old_ids=c.id;
                local_split_count=0;
                new_id=new_clusters_from;
                old_new_id_scanner=old_new_id_list.listIterator();
                while (old_new_id_scanner.hasNext()){
                    old_new_id= (OldNewId)old_new_id_scanner.next();
                    for (i=0;i<old_ids.length;i++){
                        if (old_ids[i]==old_new_id.o){
                            if(local_split_count>0){
                                new_c = new Cluster(old_new_id.n, c.location.x, c.location.y, c.velocity.x, c.velocity.y, old_new_id.t);
                                clusterScanner.add(new_c);
                            }else{
                                new_ids=new int[1];
                                c.id=new_ids;
                                c.id[0]= old_new_id.n;
                                c.last = old_new_id.t;
                            }
                            local_split_count++;
                        }
                    }
                }
                if (local_split_count<1){ 
                    log.warning("could not associate an existing cluster with one of the still valid diffused clusters");
                    clusterScanner.remove();
                }
                split_count+=(local_split_count-1);
            }
        }
        return(split_count);
    }
    
/**************************************************************************************************************************************/
    
    public class Cluster{
        public Point2D.Float location=new Point2D.Float(); // location in chip pixels
        
        /** velocity of cluster in pixels/tick, where tick is timestamp tick (usually microseconds) */
        public Point2D.Float velocity=new Point2D.Float(); // velocity in chip pixels/sec
        //protected float distanceToLastEvent=Float.POSITIVE_INFINITY;
        public int[] id=null;
        int last=-1;
        public float lifeForce=(float)0;
        public Color color=null;

//        public Cluster(){
            
//            location.x=(float)20.5;
//            location.y=(float)10.9;
//            id=-1;
//        }

        public Cluster(float x, float y, int first_event_time){
            //System.out.println("**** constructed "+this);
            
            location.x=x;
            location.y=y;
            id=new int[1];            
            id[0]=next_cluster_id++;
            last=first_event_time;
            velocity.x=(float)0.0;
            velocity.y=(float)0.0;
            lifeForce=1;
            float hue=random.nextFloat();
            color=Color.getHSBColor(hue,1f,1f);

        }
        public Cluster(int identity, float x, float y, float vx, float vy, int first_event_time){
            location.x=x;
            location.y=y;
            id=new int[1];            
            id[0]=identity;
            last=first_event_time;
            velocity.x=vx;
            velocity.y=vy;
            lifeForce=1;
            float hue=random.nextFloat();
            color=Color.getHSBColor(hue,1f,1f);
            
        }
       
        public void addEvent(BasicEvent ev){
                                
                                int interval=ev.timestamp - this.last;
                                if (interval==0) interval=1;
                                float event_weight=(float)interval/CLUSTER_UNSUPPORTED_LIFETIME;
                                this.last= ev.timestamp;
                                this.lifeForce= this.lifeForce*(float)Math.exp(-(float)interval/CLUSTER_UNSUPPORTED_LIFETIME) +1;
                                float predicted_x=this.location.x + this.velocity.x * (interval);
                                float predicted_y=this.location.y + this.velocity.y * (interval);
                                float new_x=(1-event_weight)*predicted_x + event_weight*ev.x ;
                                float new_y=(1-event_weight)*predicted_y + event_weight*ev.y ;
                                this.velocity.x= (1-event_weight)*this.velocity.x + event_weight*(new_x - this.location.x)/interval;
                                this.velocity.y= (1-event_weight)*this.velocity.y + event_weight*(new_y - this.location.y)/interval;
                                this.location.x= new_x;
                                this.location.y= new_y;
        }
        
        public Point2D.Float getLocation() {
            return location;
        }
        public void setLocation(Point2D.Float l){
            this.location = l;
        }

    }

    public java.util.List<ParticleTracker.Cluster> getClusters() {
        return this.clusters;
    }
    
//    private LinkedList<ParticleTracker.Cluster> getPruneList(){
//        return this.pruneList;
//    }

    private final void drawCluster(final Cluster c, float[][][] fr){
        int x=(int)c.getLocation().x;
        int y=(int)c.getLocation().y;
        int i;
        if(y<0 || y>fr.length-1 || x<0 || x>fr[0].length-1) return;
//        for (x=20;x<(fr[0].length-20);x++){
//            for(y=20;y<(fr[1].length-20);y++){
                fr[x][y][0]=(float)0.2;
                fr[x][y][1]=(float)1.0;
                fr[x][y][2]=(float)1.0;
//            }
//        }
    }
    
    public void initFilter() {
        initDefaults();
        resetFilter();
//        defaultClusterRadius=(int)Math.max(chip.getSizeX(),chip.getSizeY())*getClusterSize();
    }
    
    private void initDefaults(){
//        initDefault("ParticleTracker.clusterLifetimeWithoutSupport","10000");
//        initDefault("ParticleTracker.maxNumClusters","10");
//        initDefault("ParticleTracker.clusterSize","0.15f");
//        initDefault("ParticleTracker.numEventsStoredInCluster","100");
//        initDefault("ParticleTracker.thresholdEventsForVisibleCluster","30");
        
//        initDefault("ParticleTracker.","");
    }
    synchronized public void resetFilter() {
        int i,j;
        clusters.clear();
        next_cluster_id=1;
        for (i=0;i<128;i++){
            for (j=0;j<128;j++){
                lastCluster[i][j]=-1;
                lastEvent[i][j]=-1;
            }
        }
    }
    
    public Object getFilterState() {
        return null;
    }
    
    private boolean isGeneratingFilter() {
        return false;
    }

    
    public EventPacket filterPacket(EventPacket in) {
        if(in==null) return null;
        if(!filterEnabled) return in;
        if(enclosedFilter!=null) in=enclosedFilter.filterPacket(in);
        track(in);
        return in;
    }
    
    
    public void annotate(Graphics2D g) {
    }
    
    protected void drawBox(GL gl, int x, int y, int sx, int sy){
        gl.glBegin(GL.GL_LINE_LOOP);
        {
            gl.glVertex2i(x-sx,y-sy);
            gl.glVertex2i(x+sx,y-sy);
            gl.glVertex2i(x+sx,y+sy);
            gl.glVertex2i(x-sx,y+sy);
        }
        gl.glEnd();
    }

    
    synchronized public void annotate(GLAutoDrawable drawable) {
        final float BOX_LINE_WIDTH=5f; // in pixels
        final float PATH_LINE_WIDTH=3f;
        float[] rgb;
        int sx,sy;

        if(!isFilterEnabled()) return;
        GL gl=drawable.getGL(); // when we get this we are already set up with scale 1=1 pixel, at LL corner
        if(gl==null){
            log.warning("null GL in ClassTracker.annotate");
            return;
        }
        gl.glPushMatrix();
        splitClusters();
        for(Cluster c:clusters){
            rgb=c.color.getRGBComponents(null);
            if (c.lifeForce>CLUSTER_MINLIFEFORCE_4_DISPLAY){
                sx=4;
                sy=4;
                gl.glLineWidth((float)1);
                gl.glColor3fv(rgb,0);
                //gl.glColor3f(.5f,.7f,.1f);
            }else{
                sx=(int)(4.0*c.lifeForce/CLUSTER_MINLIFEFORCE_4_DISPLAY);
                sy=(int)(4.0*c.lifeForce/CLUSTER_MINLIFEFORCE_4_DISPLAY);
                gl.glLineWidth((float).2);
                //gl.glColor3fv(rgb,0);
                gl.glColor3f(.1f,.2f,.1f);
            }
                drawBox(gl,(int)c.location.x,(int)c.location.y,sx,sy);
        }
        gl.glPopMatrix();
    }
    
        /** annotate the rendered retina frame to show locations of clusters */
    synchronized public void annotate(float[][][] frame) {
        //System.out.println("******drawing ouooo yeah!");
        if(!isFilterEnabled()) return;
        // disable for now TODO
        if(chip.getCanvas().isOpenGLEnabled()) return; // done by open gl annotator
        //System.out.println("******really ouooo yeah!");
        for(Cluster c:clusters){
        //    if(c.isVisible()){
                //System.out.println("******really true!");
                drawCluster(c, frame);
//            }
        }
    }

    public void update(Observable o, Object arg) {
        initFilter();
    }

}
