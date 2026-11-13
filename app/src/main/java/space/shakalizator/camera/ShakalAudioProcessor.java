package space.shakalizator.camera;

import androidx.media3.common.C;
import androidx.media3.common.audio.BaseAudioProcessor;
import java.nio.ByteBuffer;

/** Streaming telephone-band, sample-hold, bitcrush and seeded dropouts. No whole-track buffers. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class ShakalAudioProcessor extends BaseAudioProcessor {
    private final int level; private final long seed;
    private long frames; private float low, high, prev; private short held;
    public ShakalAudioProcessor(int level,long seed){this.level=level;this.seed=seed;}
    @Override protected AudioFormat onConfigure(AudioFormat format) throws UnhandledAudioFormatException {
        if(format.encoding!=C.ENCODING_PCM_16BIT)throw new UnhandledAudioFormatException(format);
        return format;
    }
    @Override protected void onFlush(){frames=0;low=high=prev=0;held=0;}
    @Override public void queueInput(ByteBuffer input){
        int channels=inputAudioFormat.channelCount, rate=inputAudioFormat.sampleRate;
        int count=input.remaining()/(2*channels);ByteBuffer output=replaceOutputBuffer(count*2*channels);
        int l=level==12?10:level;int target=level==12?4000:(l>=7?8000:l>=5?11025:l>=3?16000:22050);
        int hold=Math.max(1,rate/target),bits=level==12?3:Levels.AUDIO_BITS[l];
        double lp=1-Math.exp(-2*Math.PI*(level==12?2000:Levels.LP[l])/rate),hp=Math.exp(-2*Math.PI*300/rate);
        for(int i=0;i<count;i++,frames++){
            float mono=0;for(int c=0;c<channels;c++)mono+=input.getShort()/32768f;mono/=channels;
            low+=(float)(lp*(mono-low));high=(float)(hp*(high+low-prev));prev=low;
            double v=l>=4?high:low;
            if(l>=5)v*=.8+.2*Math.sin(2*Math.PI*(3+l*.15)*frames/rate);
            long window=frames/(rate/10);long hash=mix(window+seed);boolean drop=l>=6 && Math.floorMod(hash,100)<(l-5)*3;
            if(drop)v=0;
            if(level==12)v+=.008*Math.sin(2*Math.PI*900*frames/rate);
            if(l>=9)v=Math.max(-.9,Math.min(.9,v*2));
            if(frames%hold==0){double steps=1<<(bits-1);v=Math.rint(v*steps)/steps;held=(short)Math.max(-32768,Math.min(32767,(int)(v*32767)));}
            for(int c=0;c<channels;c++)output.putShort(held);
        }
        input.position(input.limit());output.flip();
    }
    private static long mix(long v){v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return v^(v>>>31);}
}
