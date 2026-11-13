package space.shakalizator.camera;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.*;
import androidx.media3.effect.*;
import androidx.media3.transformer.*;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Two actual decode/encode generations using Android MediaCodec. All work stays on device. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class VideoEngine {
    public interface Listener {void progress(int percent,String stage);void done(File file);void error(Exception e);}
    private final Context context;private final Handler handler=new Handler(Looper.getMainLooper());
    private Transformer transformer;private File intermediate,out;private boolean cancelled;private int pass;
    public VideoEngine(Context c){context=c.getApplicationContext();}
    public void start(File input,int level,long seed,File output,Listener listener){
        Levels.valid(level);cancelled=false;out=output;
        intermediate=new File(output.getParentFile(),"generation_"+System.nanoTime()+".mp4");
        try{
            int w=1280,h=720;try(MediaMetadataRetriever r=new MediaMetadataRetriever()){
                r.setDataSource(input.getPath());w=Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));h=Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                String rot=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);if("90".equals(rot)||"270".equals(rot)){int v=w;w=h;h=v;}
            }
            int l=level==12?10:level,side=level==12?48:Levels.VIDEO_SIDE[l];
            if(Math.max(w,h)<side)side=Math.max(16,(int)(Math.max(w,h)*.7));
            int[] grid=Levels.evenFit(w,h,side),first=Levels.evenFit(w,h,Math.max(256,side));
            // Many hardware AVC encoders reject very small pictures. Sampling is done in GL;
            // the coded surface remains >= 256 on the long side and can fall back by codec.
            runPass(input,intermediate,level,seed,first,grid,true,listener,()->{
                int[] result=Levels.evenFit(grid[0],grid[1],480);
                runPass(intermediate,output,level,seed,result,grid,false,listener,()->{intermediate.delete();listener.done(output);});
            });
        }catch(Exception e){cleanup();listener.error(e);}
    }
    private void runPass(File input,File output,int level,long seed,int[] size,int[] grid,boolean first,Listener listener,Runnable next){
        if(cancelled)return;pass=first?0:1;int l=level==12?10:level;
        List<Effect> effects=new ArrayList<>();
        if(first){effects.add(Presentation.createForWidthAndHeight(size[0],size[1],Presentation.LAYOUT_SCALE_TO_FIT));effects.add(new OldCameraEffect(grid[0],grid[1],level,seed));}
        else{effects.add(Presentation.createForWidthAndHeight(size[0],size[1],Presentation.LAYOUT_SCALE_TO_FIT));if(level==12)effects.add(new PixelEffect(grid[0],grid[1],true));else effects.add(new HslAdjustment.Builder().adjustSaturation((l-1)*6f).build());}
        Effects fx=new Effects(first?ImmutableList.of(new ShakalAudioProcessor(level,seed)):ImmutableList.of(),effects);
        MediaItem item=new MediaItem.Builder().setUri(Uri.fromFile(input)).setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setEndPositionMs(60_000).build()).build();
        EditedMediaItem edited=new EditedMediaItem.Builder(item).setEffects(fx).build();
        int bitrate=level==12?180000:Levels.BITRATE[l];
        DefaultEncoderFactory factory=new DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder().setBitrate(bitrate).build())
            .setRequestedAudioEncoderSettings(new AudioEncoderSettings.Builder().setBitrate(Math.max(24000,Levels.AUDIO_BITRATE[l])).build())
            .setEnableFallback(true).build();
        transformer=new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(factory)
            .addListener(new Transformer.Listener(){
                @Override public void onCompleted(Composition composition,ExportResult result){if(cancelled)return;transformer=null;next.run();}
                @Override public void onError(Composition composition,ExportResult result,ExportException exception){if(cancelled)return;transformer=null;cleanup();listener.error(exception);}
            }).build();
        output.delete();listener.progress(first?0:50,first?"Проход 1/2 · собираем артефакты":"Проход 2/2 · добавляем характер");
        try{transformer.start(edited,output.getPath());poll(listener);}catch(Exception e){transformer=null;cleanup();listener.error(e);}
    }
    private void poll(Listener listener){
        if(transformer==null||cancelled)return;ProgressHolder p=new ProgressHolder();
        if(transformer.getProgress(p)==Transformer.PROGRESS_STATE_AVAILABLE)listener.progress(pass*50+p.progress/2,pass==0?"Проход 1/2 · собираем артефакты":"Проход 2/2 · добавляем характер");
        handler.postDelayed(()->poll(listener),400);
    }
    public void cancel(){cancelled=true;handler.removeCallbacksAndMessages(null);if(transformer!=null){transformer.cancel();transformer=null;}cleanup();}
    private void cleanup(){if(intermediate!=null)intermediate.delete();if(out!=null)out.delete();}
}
