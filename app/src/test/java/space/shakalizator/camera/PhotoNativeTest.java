package space.shakalizator.camera;
import android.graphics.*;
import org.robolectric.RobolectricTestRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.*;
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class PhotoNativeTest {
    private Bitmap fixture(int w,int h){Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint();p.setShader(new LinearGradient(0,0,w,h,new int[]{Color.CYAN,Color.YELLOW,Color.MAGENTA},null,Shader.TileMode.CLAMP));c.drawPaint(p);p.setShader(null);p.setColor(Color.BLACK);for(int x=0;x<w;x+=37)c.drawRect(x,h/3f,x+8,h*2/3f,p);p.setTextSize(w/12f);c.drawText("SHAKAL 123",30,h/4f,p);return b;}
    private long fingerprint(Bitmap b){int[] p=new int[b.getWidth()*b.getHeight()];b.getPixels(p,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());return Arrays.hashCode(p);}
    @Test public void everyLevelAndResamplingProducesNonBlackPhoto() throws Exception {
        Bitmap source=fixture(640,480);try{for(String mode:new String[]{"paint","nearest","clean"}){
            Set<Long> hashes=new HashSet<>();for(int level:new int[]{1,2,3,4,5,6,7,8,9,10,12}){Bitmap out=PhotoEngine.process(source,level,mode,123,n->{});assertEquals(640,out.getWidth());assertEquals(480,out.getHeight());assertNotEquals(Color.BLACK,out.getPixel(320,240));assertTrue("distinct level "+level+" "+mode,hashes.add(fingerprint(out)));if(mode.equals("paint") && (level==1||level==5||level==10||level==12)){java.io.File directory=new java.io.File("build/test-visuals");directory.mkdirs();try(java.io.OutputStream stream=new java.io.FileOutputStream(new java.io.File(directory,"level-"+level+".png"))){out.compress(Bitmap.CompressFormat.PNG,100,stream);}}out.recycle();}
        }}finally{source.recycle();}
    }
    @Test public void repeatedProcessingDoesNotAccumulateWorkingBitmaps() throws Exception {
        Bitmap b=fixture(640,480);try{for(int i=0;i<8;i++){Bitmap next=PhotoEngine.process(b,i%2==0?12:10,"paint",234,n->{});b.recycle();b=next;assertNotEquals(Color.BLACK,b.getPixel(320,240));}}finally{b.recycle();}
    }
    @Test public void seededNoiseIsDeterministic() throws Exception {
        Bitmap source=fixture(640,480),a=PhotoEngine.process(source,10,"nearest",33,n->{}),b=PhotoEngine.process(source,10,"nearest",33,n->{});assertEquals(fingerprint(a),fingerprint(b));source.recycle();a.recycle();b.recycle();
    }
    @Test public void largeSourceIsBoundedAndOriented() throws Exception {
        java.io.File f=new java.io.File(org.robolectric.RuntimeEnvironment.getApplication().getCacheDir(),"test-source.jpg");
        for(int[] size:new int[][]{{4000,3000},{4032,3024}}){Bitmap b=fixture(size[0],size[1]);try(java.io.OutputStream out=new java.io.FileOutputStream(f)){b.compress(Bitmap.CompressFormat.JPEG,94,out);}b.recycle();Bitmap loaded=PhotoEngine.load(f);assertEquals(2048,Math.max(loaded.getWidth(),loaded.getHeight()));Bitmap result=PhotoEngine.process(loaded,12,"clean",42,n->{});assertNotEquals(Color.BLACK,result.getPixel(result.getWidth()/2,result.getHeight()/2));loaded.recycle();result.recycle();}
        f.delete();
    }
}
