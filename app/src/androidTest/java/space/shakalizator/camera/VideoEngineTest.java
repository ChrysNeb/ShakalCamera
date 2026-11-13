package space.shakalizator.camera;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class VideoEngineTest {
    @Test public void nativeVideoHasDecodableAvcAndAacAtEveryLevel() throws Exception {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        File input=new File(context.getCacheDir(),"motion.mp4");try(InputStream in=instrumentation.getContext().getAssets().open("motion.mp4");OutputStream out=new FileOutputStream(input)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
        for(int level:new int[]{1,2,3,4,5,6,7,8,9,10,12}){
            CountDownLatch latch=new CountDownLatch(1);AtomicReference<Exception> error=new AtomicReference<>();AtomicReference<VideoEngine> engine=new AtomicReference<>();File output=new File(context.getCacheDir(),"video-test-"+level+".mp4");
            instrumentation.runOnMainSync(()->{VideoEngine e=new VideoEngine(context);engine.set(e);e.start(input,level,42,output,new VideoEngine.Listener(){public void progress(int n,String s){}public void done(File f){latch.countDown();}public void error(Exception ex){error.set(ex);latch.countDown();}});});
            boolean complete=latch.await(120,TimeUnit.SECONDS);if(!complete)instrumentation.runOnMainSync(()->engine.get().cancel());assertTrue("timeout level "+level,complete);assertNull("export level "+level+": "+error.get(),error.get());assertTrue(output.length()>100);
            MediaExtractor extractor=new MediaExtractor();try{extractor.setDataSource(output.getPath());boolean avc=false,aac=false;for(int t=0;t<extractor.getTrackCount();t++){MediaFormat format=extractor.getTrackFormat(t);String mime=format.getString(MediaFormat.KEY_MIME);if("video/avc".equals(mime)){avc=true;assertEquals(480,Math.max(format.getInteger(MediaFormat.KEY_WIDTH),format.getInteger(MediaFormat.KEY_HEIGHT)));}if("audio/mp4a-latm".equals(mime))aac=true;}assertTrue("AVC",avc);assertTrue("AAC",aac);}finally{extractor.release();}
            try(MediaMetadataRetriever r=new MediaMetadataRetriever()){r.setDataSource(output.getPath());Bitmap frame=r.getFrameAtTime(0);assertNotNull("decoded frame",frame);assertNotEquals(0xff000000,frame.getPixel(frame.getWidth()/2,frame.getHeight()/2));frame.recycle();}
            output.delete();
        }
        input.delete();
    }
}
