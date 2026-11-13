package space.shakalizator.camera;
import android.Manifest;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;
import org.junit.*;import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class GpuPreviewTest {
    @Rule public GrantPermissionRule permission=GrantPermissionRule.grant(Manifest.permission.CAMERA);
    @Test public void actualCameraFramesFlowThroughGpuWithoutBitmapPolling()throws Exception{
        try(ActivityScenario<MainActivity> activity=ActivityScenario.launch(MainActivity.class)){
            AtomicReference<GpuPreview> ref=new AtomicReference<>();long end=System.currentTimeMillis()+30_000;
            while(System.currentTimeMillis()<end){activity.onActivity(a->ref.set(a.gpuPreviewForTests()));if(ref.get()!=null&&ref.get().rendered.get()>=5)break;Thread.sleep(200);}
            AtomicReference<String> error=new AtomicReference<>();activity.onActivity(a->error.set(a.gpuFailureForTests()));GpuPreview processor=ref.get();assertNotNull("GPU path must bind: "+error.get(),processor);assertTrue("camera frames rendered",processor.rendered.get()>=5);
            long before=processor.rendered.get();Thread.sleep(1800);assertTrue("preview continues",processor.rendered.get()>before+4);
            for(int level:new int[]{1,6,10,12}){processor.settings(level,true);long frames=processor.rendered.get();Thread.sleep(500);assertTrue("level "+level,processor.rendered.get()>frames);}
            long frames=processor.rendered.get();processor.settings(6,false);Thread.sleep(600);assertTrue("raw toggle continues without rebind",processor.rendered.get()>frames);
            assertTrue(processor.targetFps==15||processor.targetFps==20||processor.targetFps==30);
            AtomicReference<android.graphics.Rect> area=new AtomicReference<>();activity.onActivity(a->area.set(a.previewBoundsForTests()));android.graphics.Bitmap screen=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(screen);android.graphics.Rect bounds=area.get();double brightness=0;int count=0;for(int y=1;y<8;y++)for(int x=1;x<8;x++){int px=Math.max(0,Math.min(screen.getWidth()-1,bounds.left+bounds.width()*x/8));int py=Math.max(0,Math.min(screen.getHeight()-1,bounds.top+bounds.height()*y/8));int c=screen.getPixel(px,py);brightness+=android.graphics.Color.red(c)+android.graphics.Color.green(c)+android.graphics.Color.blue(c);count++;}screen.recycle();assertTrue("live surface is not black",brightness/count>25);
        }
    }
}
