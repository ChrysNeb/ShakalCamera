package space.shakalizator.camera;
import org.junit.Test;import static org.junit.Assert.*;
public class PreviewBudgetTest {
    @Test public void keepsThirtyFpsCameraWithoutAccidentalHalving(){PreviewBudget b=new PreviewBudget();int count=0;for(int i=0;i<300;i++)if(b.accept(i*33_333_333L+(i%2==0?0:500_000))){count++;b.record(5_000_000L,i*33_333_333L);}assertEquals(300,count);assertEquals(30,b.fps());}
    @Test public void sixtyFpsInputIsCappedNearThirty(){PreviewBudget b=new PreviewBudget();int count=0;for(int i=0;i<600;i++)if(b.accept(i*16_666_667L))count++;assertTrue(count>=295&&count<=305);}
    @Test public void weakDeviceStepsDownAndRecoversWithHysteresis(){PreviewBudget b=new PreviewBudget();for(int i=0;i<600;i++){long t=i*33_333_333L;if(b.accept(t))b.record(70_000_000L,t);}assertEquals(15,b.fps());for(int i=600;i<1500;i++){long t=i*33_333_333L;if(b.accept(t))b.record(4_000_000L,t);}assertEquals(30,b.fps());}
    @Test public void noCatchUpBurstAfterPause(){PreviewBudget b=new PreviewBudget();assertTrue(b.accept(0));assertTrue(b.accept(10_000_000_000L));assertFalse(b.accept(10_001_000_000L));}
}
