package space.shakalizator.camera;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class CameraCadenceTest {
    private List<Long> render(int level,long seed,boolean moving){CameraCadence c=new CameraCadence(level,seed);List<Long> pts=new ArrayList<>();for(long t=0;t<60_000_000;t+=16_667){FrameMotion.Sample sample=moving&&t>1_000_000?new FrameMotion.Sample(.65f,.15f,0,new float[]{.5f,.5f,0,0},false):FrameMotion.Sample.still();if(c.keep(t,sample))pts.add(t);}return pts;}
    @Test public void noMoreThanTwentyFreshFramesPerSecondAtEveryLevel(){for(int l:new int[]{1,2,3,4,5,6,7,8,9,10,12}){List<Long> pts=render(l,42,false);assertTrue(pts.size()>100);for(int i=1;i<pts.size();i++)assertTrue(pts.get(i)-pts.get(i-1)>=50_000);}}
    @Test public void containsNormalDipsAndHalfSecondFreezes(){CameraCadence c=new CameraCadence(6,32);EnumSet<CameraCadence.Phase> phases=EnumSet.noneOf(CameraCadence.Phase.class);long previous=-1,max=0;Set<Long> deltas=new HashSet<>();for(long t=0;t<60_000_000;t+=16_667){boolean kept=c.keep(t,FrameMotion.Sample.still());phases.add(c.phase());if(kept){if(previous>=0){long d=t-previous;deltas.add(d);max=Math.max(max,d);}previous=t;}}assertEquals(3,phases.size());assertTrue(max>=430_000);assertTrue(max<900_000);assertTrue(deltas.size()>=4);}
    @Test public void seededAndNotAConstantRepeatingPattern(){assertEquals(render(5,123,false),render(5,123,false));assertNotEquals(render(5,123,false),render(5,124,false));}
    @Test public void motionAndLowLightChangeTheSchedule(){assertNotEquals(render(5,123,false),render(5,123,true));}
    @Test public void flushReproducesOriginalSchedule(){CameraCadence c=new CameraCadence(5,123);List<Long> a=new ArrayList<>(),b=new ArrayList<>();for(long t=0;t<10_000_000;t+=33_333)if(c.keep(t,FrameMotion.Sample.still()))a.add(t);c.reset();for(long t=0;t<10_000_000;t+=33_333)if(c.keep(t,FrameMotion.Sample.still()))b.add(t);assertEquals(a,b);}
    @Test public void sceneCutCausesEarlyDip(){CameraCadence c=new CameraCadence(1,42);for(long t=0;t<1_000_000;t+=33_333)c.keep(t,FrameMotion.Sample.still());c.keep(1_000_000,new FrameMotion.Sample(1,.9f,0,new float[4],true));assertNotEquals(CameraCadence.Phase.NORMAL,c.phase());}
}
