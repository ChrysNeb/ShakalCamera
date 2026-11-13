package space.shakalizator.camera;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class FrameMotionTest {
    @Test public void staticFrameHasNoMotion(){FrameMotion m=new FrameMotion();byte[] p=new byte[32*24];new Random(42).nextBytes(p);m.measure(p);FrameMotion.Sample s=m.measure(p);assertEquals(0,s.change,0);assertFalse(s.cut);for(float row:s.rows)assertEquals(0,row,0);}
    @Test public void cutDoesNotProduceFakeRollingShutter(){FrameMotion m=new FrameMotion();byte[] black=new byte[768],white=new byte[768];Arrays.fill(white,(byte)255);m.measure(black);FrameMotion.Sample s=m.measure(white);assertTrue(s.cut);assertEquals(1,s.brightness,.001);for(float row:s.rows)assertEquals(0,row,0);}
    @Test public void movingTextureHasSignedHorizontalFlow(){FrameMotion m=new FrameMotion();byte[] before=new byte[768],after=new byte[768];new Random(42).nextBytes(before);for(int y=0;y<24;y++)for(int x=0;x<32;x++)after[y*32+x]=before[y*32+Math.max(0,x-2)];m.measure(before);FrameMotion.Sample s=m.measure(after);assertFalse(s.cut);assertTrue(s.change>0);for(float row:s.rows)assertTrue("rightward flow",row>.3f);}
    @Test public void resetRemovesHistory(){FrameMotion m=new FrameMotion();byte[] p=new byte[768];m.measure(p);m.reset();Arrays.fill(p,(byte)255);FrameMotion.Sample s=m.measure(p);assertFalse(s.cut);assertEquals(0,s.change,0);}
}
