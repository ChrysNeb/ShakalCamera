package space.shakalizator.camera;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import java.nio.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class AudioNativeTest {
    private byte[] render(int level,double frequency) throws Exception {
        ShakalAudioProcessor processor=new ShakalAudioProcessor(level,123);
        processor.configure(new AudioProcessor.AudioFormat(48000,2,C.ENCODING_PCM_16BIT));processor.flush();
        ByteBuffer input=ByteBuffer.allocateDirect(48000*4).order(ByteOrder.nativeOrder());
        for(int i=0;i<48000;i++){short s=(short)(Math.sin(2*Math.PI*frequency*i/48000)*8000);input.putShort(s);input.putShort(s);}input.flip();processor.queueInput(input);ByteBuffer output=processor.getOutput();byte[] bytes=new byte[output.remaining()];output.get(bytes);processor.reset();return bytes;
    }
    private double rms(byte[] bytes){ShortBuffer s=ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer();double sum=0;while(s.hasRemaining()){double v=s.get();sum+=v*v;}return Math.sqrt(sum/(bytes.length/2));}
    @Test public void allLevelsKeepDurationAndDualMono() throws Exception {for(int level:new int[]{1,2,3,4,5,6,7,8,9,10,12}){byte[] bytes=render(level,1000);assertEquals(192000,bytes.length);assertTrue(rms(bytes)>100);ShortBuffer s=ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asShortBuffer();while(s.hasRemaining())assertEquals(s.get(),s.get());}}
    @Test public void deterministicAudioAndTelephoneBand() throws Exception {assertArrayEquals(render(10,1000),render(10,1000));assertTrue(rms(render(10,12000))<rms(render(10,1000))*.5);}
    @Test public void silenceDoesNotExplode() throws Exception {assertEquals(0,rms(render(10,0)),0);}
}
