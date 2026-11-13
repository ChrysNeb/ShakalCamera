package space.shakalizator.camera;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.*;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class MainActivity extends ComponentActivity {
    private static final int BG=0xff0f1115,PANEL=0xff181b22,ORANGE=0xfff5a524,TEXT=0xffe8eaf0,MUTED=0xffa3aabb;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private ProcessCameraProvider provider;private androidx.camera.core.Camera camera;private ImageCapture imageCapture;private VideoCapture<Recorder> videoCapture;
    private Recording recording;private PreviewView preview;private ImageView image;private VideoView video;private FrameLayout viewport;
    private TextView status,label,hero;private ProgressBar progress;private Button shutter,flip,flash,zoom,modePhoto,modeVideo,art,importButton,mute,save,share,again,back,before,cancel;
    private SeekBar slider;private Spinner resampling;private LinearLayout resultActions,cameraActions;private View levelControls;
    private boolean videoMode=false,front=false,torch=false,mic=true,busy=false,resultVideo=false,showingOriginal=false,destroying=false,liveFilter=true;
    private Bitmap liveBitmap;
    private Button liveButton;
    private int level=6,generation=0,resultLevel=6;private String mode="paint";private long seed=1,started;private File source,result;private Bitmap displayed;private Future<?> photoTask;private VideoEngine engine;
    private final ActivityResultLauncher<String[]> permissions=registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),grants->{bindCamera();});
    private final ActivityResultLauncher<String> audioPermission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{mic=granted;updateControls();if(!granted)toast("Видео будет без звука. Микрофон можно включить в настройках.");});
    private final ActivityResultLauncher<String> storagePermission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{if(granted)saveResult();else toast("Для сохранения в галерею нужен доступ к памяти");});
    private final ActivityResultLauncher<String[]> pick=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{if(uri!=null)importMedia(uri);});

    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(state!=null){level=state.getInt("level",6);front=state.getBoolean("front",false);videoMode=state.getBoolean("videoMode",false);mode=state.getString("mode","paint");}
        File dir=new File(getCacheDir(),"exports");dir.mkdirs();File[] old=dir.listFiles();if(old!=null)for(File f:old)if(System.currentTimeMillis()-f.lastModified()>24*60*60*1000L)f.delete();
        buildUi();main.postDelayed(this::liveFrame,800);
        if(has(Manifest.permission.CAMERA))bindCamera();else permissions.launch(new String[]{Manifest.permission.CAMERA});
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){if(busy){cancelWork();}else if(result!=null){returnCamera();}else{setEnabled(false);getOnBackPressedDispatcher().onBackPressed();}}});
    }
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("level",level);b.putBoolean("front",front);b.putBoolean("videoMode",videoMode);b.putString("mode",mode);}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);v.setOrientation(LinearLayout.HORIZONTAL);return v;}
    private GradientDrawable rounded(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));d.setStroke(dp(1),0xff343a46);return d;}
    private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(12);b.setTextColor(TEXT);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(48));b.setPadding(dp(6),0,dp(6),0);b.setBackground(rounded(PANEL));b.setContentDescription(title);b.setOnClickListener(v->action.run());return b;}
    private void add(LinearLayout row,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(v,p);}
    private void accent(Button b,boolean on){b.setBackground(rounded(on?ORANGE:PANEL));b.setTextColor(on?BG:TEXT);}
    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(10),0,dp(10),dp(6));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(10),insets.getSystemWindowInsetTop(),dp(10),Math.max(dp(6),insets.getSystemWindowInsetBottom()));return insets;});
        LinearLayout header=row();TextView logo=text("ШАКАЛ [КАМЕРА]",22,ORANGE);logo.setTypeface(null,Typeface.BOLD);header.addView(logo,new LinearLayout.LayoutParams(0,dp(46),1));Button info=button("?",()->new android.app.AlertDialog.Builder(this).setTitle("Камера с характером")
            .setMessage("Нативная камера: CameraX / Camera2.\n\nФото: уровни и JPEG-проходы из фото-шакализатора; Paint, Nearest и Чистый.\nВидео: два поколения H.264, низкий битрейт, клетки, снижение FPS и телефонный звук. Это нативная адаптация, без битстрим-датамоша FFmpeg.\n\n12/10 — высокое пиксельное искусство. Фото до 2048 px, ролики до 60 секунд.\n\nФильтр в видоискателе — облегчённое превью, обновляется примерно раз в секунду. Окончательная обработка после съёмки. Всё локально. Приложение не имеет доступа к интернету.")
            .setPositiveButton("Прекрасно",null).show());header.addView(info,new LinearLayout.LayoutParams(dp(48),dp(44)));root.addView(header);
        hero=text("Фото, которое обрело душу. Бережно. В телефоне.",12,MUTED);root.addView(hero);
        viewport=new FrameLayout(this);viewport.setBackground(rounded(PANEL));LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,0,1);vp.setMargins(0,dp(8),0,dp(5));root.addView(viewport,vp);
        preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);viewport.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setVisibility(View.GONE);viewport.addView(image,new FrameLayout.LayoutParams(-1,-1));
        video=new VideoView(this);video.setVisibility(View.GONE);FrameLayout.LayoutParams vv=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);viewport.addView(video,vv);MediaController controls=new MediaController(this);controls.setAnchorView(video);video.setMediaController(controls);video.setOnPreparedListener(p->{p.setLooping(true);video.start();});video.setOnErrorListener((p,w,e)->{toast("Не удалось воспроизвести видео на этом устройстве");return true;});
        preview.setOnTouchListener((v,e)->{if(e.getAction()==android.view.MotionEvent.ACTION_UP && camera!=null && !busy && result==null){MeteringPoint point=preview.getMeteringPointFactory().createPoint(e.getX(),e.getY());camera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).setAutoCancelDuration(3,TimeUnit.SECONDS).build());v.performClick();}return true;});
        status=text("Нужен доступ к камере. Файлы остаются в телефоне.",12,MUTED);status.setGravity(Gravity.CENTER);status.setMinHeight(dp(26));root.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(ColorStateList.valueOf(ORANGE));progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(5)));
        LinearLayout levels=row();label=text("🐺 6/10 шакалов",16,TEXT);levels.addView(label,new LinearLayout.LayoutParams(0,dp(42),1));art=button("12/10",()->{if(busy||recording!=null)return;level=level==12?slider.getProgress()+1:12;updateControls();});levels.addView(art,new LinearLayout.LayoutParams(dp(76),dp(42)));root.addView(levels);levelControls=levels;
        slider=new SeekBar(this);slider.setMax(9);slider.setProgress(Math.min(level,10)-1);slider.setProgressTintList(ColorStateList.valueOf(ORANGE));slider.setThumbTintList(ColorStateList.valueOf(ORANGE));root.addView(slider,new LinearLayout.LayoutParams(-1,dp(32)));slider.setContentDescription("Уровень шакальности от одного до десяти");slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){if(user){level=p+1;updateControls();}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        LinearLayout settings=row();TextView rt=text("Ресемплинг  ",12,MUTED);settings.addView(rt);resampling=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Paint","Nearest","Чистый"});resampling.setAdapter(adapter);resampling.setSelection(mode.equals("nearest")?1:mode.equals("clean")?2:0);settings.addView(resampling,new LinearLayout.LayoutParams(0,dp(42),1));resampling.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int i,long id){mode=new String[]{"paint","nearest","clean"}[i];if(v instanceof TextView)((TextView)v).setTextColor(TEXT);}public void onNothingSelected(AdapterView<?> p){}});mute=button("Звук вкл.",()->{if(busy||recording!=null)return;if(!has(Manifest.permission.RECORD_AUDIO)){audioPermission.launch(Manifest.permission.RECORD_AUDIO);}else{mic=!mic;updateControls();}});settings.addView(mute,new LinearLayout.LayoutParams(dp(92),dp(42)));root.addView(settings);
        cameraActions=new LinearLayout(this);cameraActions.setOrientation(LinearLayout.VERTICAL);LinearLayout modes=row();modePhoto=button("ФОТО",()->setMode(false));modeVideo=button("ВИДЕО · 60с",()->setMode(true));importButton=button("Из галереи",()->{if(!busy&&recording==null)pick.launch(new String[]{"image/*","video/*"});});add(modes,modePhoto);add(modes,modeVideo);add(modes,importButton);cameraActions.addView(modes);
        LinearLayout tools=row();flip=button("↻ Камера",()->{if(busy||recording!=null)return;front=!front;torch=false;bindCamera();});flash=button("⚡ Свет",()->{if(camera!=null&&!busy&&recording==null){torch=!torch;camera.getCameraControl().enableTorch(torch);updateControls();}});zoom=button("1× / 2×",()->{if(camera!=null&&!busy&&recording==null){androidx.camera.core.ZoomState z=camera.getCameraInfo().getZoomState().getValue();if(z!=null)camera.getCameraControl().setZoomRatio(z.getZoomRatio()>1.1f?1f:Math.min(2,z.getMaxZoomRatio()));}});add(tools,flip);add(tools,flash);add(tools,zoom);cameraActions.addView(tools);
        liveButton=button("Фильтр в кадре",()->{liveFilter=!liveFilter;accent(liveButton,liveFilter);if(!liveFilter&&result==null){image.setVisibility(View.GONE);image.setImageDrawable(null);if(liveBitmap!=null){liveBitmap.recycle();liveBitmap=null;}}});add(tools,liveButton);accent(liveButton,true);
        shutter=button("●  Снять фото",this::capture);cameraActions.addView(shutter,new LinearLayout.LayoutParams(-1,dp(58)));root.addView(cameraActions);
        resultActions=new LinearLayout(this);resultActions.setOrientation(LinearLayout.VERTICAL);LinearLayout r1=row();save=button("Сохранить",this::saveResult);share=button("Поделиться",this::shareResult);again=button("Ещё шакальнее",()->{if(result!=null&&!busy)process(result,resultVideo);});add(r1,save);add(r1,share);add(r1,again);resultActions.addView(r1);
        LinearLayout r2=row();before=button("Оригинал",()->showResult(!showingOriginal));back=button("← Камера",this::returnCamera);add(r2,before);add(r2,back);resultActions.addView(r2);resultActions.setVisibility(View.GONE);root.addView(resultActions);
        cancel=button("Отмена",this::cancelWork);cancel.setVisibility(View.GONE);root.addView(cancel,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView privacy=text("Всё в телефоне · без интернета и водяных знаков",10,MUTED);privacy.setGravity(Gravity.CENTER);root.addView(privacy);updateControls();
    }
    private boolean has(String p){return ContextCompat.checkSelfPermission(this,p)==PackageManager.PERMISSION_GRANTED;}
    private void setMode(boolean videoMode){if(busy||recording!=null)return;this.videoMode=videoMode;bindCamera();updateControls();}
    private void updateControls(){
        label.setText(level==12?"🐺 12/10 · искусство":("🐺 "+level+"/10 шакалов"));accent(art,level==12);accent(modePhoto,!videoMode);accent(modeVideo,videoMode);accent(shutter,true);accent(flash,torch);
        shutter.setText(recording!=null?"■  Остановить запись":videoMode?"●  Записать видео":"●  Снять фото");
        shutter.setEnabled(!busy && camera!=null);flip.setEnabled(!busy&&recording==null);flash.setEnabled(!busy&&recording==null&&camera!=null&&camera.getCameraInfo().hasFlashUnit());
        modePhoto.setEnabled(!busy&&recording==null);modeVideo.setEnabled(!busy&&recording==null);importButton.setEnabled(!busy&&recording==null);zoom.setEnabled(!busy&&recording==null);slider.setEnabled(!busy&&recording==null);art.setEnabled(!busy&&recording==null);resampling.setEnabled(!busy&&recording==null&&!videoMode);mute.setEnabled(!busy&&recording==null&&videoMode);mute.setText(mic&&has(Manifest.permission.RECORD_AUDIO)?"Звук вкл.":"Без звука");
        for(Button b:new Button[]{save,share,again,back,before})b.setEnabled(!busy);
    }
    private void bindCamera(){
        if(destroying||busy||result!=null)return;
        if(!has(Manifest.permission.CAMERA)){status.setText("Разрешите камеру в настройках приложения или выберите файл.");preview.setOnClickListener(v->permissions.launch(new String[]{Manifest.permission.CAMERA}));return;}
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);future.addListener(()->{
            if(destroying||busy||result!=null)return;
            try{provider=future.get();provider.unbindAll();CameraSelector selector=front?CameraSelector.DEFAULT_FRONT_CAMERA:CameraSelector.DEFAULT_BACK_CAMERA;
                if(!provider.hasCamera(selector)){front=!front;selector=front?CameraSelector.DEFAULT_FRONT_CAMERA:CameraSelector.DEFAULT_BACK_CAMERA;}
                Preview p=new Preview.Builder().build();p.setSurfaceProvider(preview.getSurfaceProvider());
                if(videoMode){Recorder recorder=new Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD,FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))).build();videoCapture=VideoCapture.withOutput(recorder);camera=provider.bindToLifecycle(this,selector,p,videoCapture);imageCapture=null;}
                else{imageCapture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setJpegQuality(94).build();camera=provider.bindToLifecycle(this,selector,p,imageCapture);videoCapture=null;}
                status.setText("Касание = фокус · окончательный фильтр после съёмки");updateControls();
            }catch(Exception e){camera=null;status.setText("Камера недоступна. Можно выбрать фото или видео из галереи.");updateControls();}
        },ContextCompat.getMainExecutor(this));
    }
    private File temp(String ext){return new File(new File(getCacheDir(),"exports"),"shakal_"+System.nanoTime()+ext);}
    private void capture(){
        if(busy)return;if(recording!=null){recording.stop();return;}if(camera==null){permissions.launch(new String[]{Manifest.permission.CAMERA});return;}
        if(videoMode){try{recordVideo();}catch(SecurityException e){recording=null;mic=false;status.setText("Разрешите микрофон или снимайте без звука");updateControls();}catch(IllegalStateException e){recording=null;status.setText("Камера ещё готовится. Попробуйте снова.");updateControls();}return;}if(imageCapture==null)return;
        File f=temp(".jpg");int token=++generation;setBusy(true,"Снимаем…");imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(f).build(),ContextCompat.getMainExecutor(this),new ImageCapture.OnImageSavedCallback(){
            public void onImageSaved(ImageCapture.OutputFileResults r){if(token!=generation||destroying){f.delete();return;}setBusy(false,"");process(f,false);}
            public void onError(ImageCaptureException e){f.delete();if(token==generation)setBusy(false,"Не получилось снять фото. Попробуйте ещё раз.");}
        });
    }
    private void recordVideo(){
        if(videoCapture==null)return;if(mic&&!has(Manifest.permission.RECORD_AUDIO)){audioPermission.launch(Manifest.permission.RECORD_AUDIO);return;}
        File f=temp(".mp4");FileOutputOptions options=new FileOutputOptions.Builder(f).setDurationLimitMillis(60_000).build();PendingRecording pending=videoCapture.getOutput().prepareRecording(this,options);if(mic&&ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)pending=pending.withAudioEnabled();
        recording=pending.start(ContextCompat.getMainExecutor(this),event->{
            if(event instanceof VideoRecordEvent.Status){long seconds=event.getRecordingStats().getRecordedDurationNanos()/1_000_000_000;status.setText("● "+String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60)+" / 01:00");}
            if(event instanceof VideoRecordEvent.Finalize){VideoRecordEvent.Finalize end=(VideoRecordEvent.Finalize)event;recording=null;updateControls();
                if(destroying){f.delete();return;}if(end.hasError()&&end.getError()!=VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED){f.delete();status.setText("Запись не завершена. Попробуйте ещё раз.");}else process(f,true);
            }
        });updateControls();
    }
    private void importMedia(Uri uri){
        String mime=getContentResolver().getType(uri);boolean isVideo=mime!=null&&mime.startsWith("video/");File f=temp(isVideo?".mp4":".jpg");int token=++generation;setBusy(true,"Открываем файл…");
        photoTask=worker.submit(()->{try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(f)){
            if(in==null)throw new IOException("Файл недоступен");byte[] buf=new byte[65536];int n;long total=0;while((n=in.read(buf))!=-1){PhotoEngine.check();total+=n;if(total>200L*1024*1024)throw new IOException("Выберите файл до 200 МБ");out.write(buf,0,n);}
            main.post(()->{if(token!=generation||destroying){f.delete();return;}setBusy(false,"");videoMode=isVideo;process(f,isVideo);});
        }catch(Exception e){f.delete();main.post(()->{if(token==generation)setBusy(false,"Не удалось открыть файл: "+e.getMessage());});}});
    }
    private void process(File input,boolean isVideo){
        if(destroying||busy)return;showingOriginal=false;video.stopPlayback();video.setVisibility(View.GONE);image.setVisibility(View.GONE);image.setImageDrawable(null);if(liveBitmap!=null){liveBitmap.recycle();liveBitmap=null;}if(provider!=null)provider.unbindAll();camera=null;
        if(source==null||result==null){source=input;seed=System.nanoTime();}
        resultVideo=isVideo;resultLevel=level;int processingLevel=level;String sampling=mode;int token=++generation;started=SystemClock.elapsedRealtime();setBusy(true,isVideo?"Готовим видео · максимум 60 секунд":"Бережно добавляем шакалов…");
        File output=temp(isVideo?".mp4":level==12?".png":".jpg");
        if(isVideo){engine=new VideoEngine(this);engine.start(input,processingLevel,seed,output,new VideoEngine.Listener(){public void progress(int n,String stage){if(token==generation)report(n,stage);}public void done(File f){if(token==generation&&!destroying){result=f;setBusy(false,"Видео обрело характер · "+formatSize(f.length()));showResult(false);}else f.delete();}public void error(Exception e){if(token==generation)fail("Этот видеокодек не поддерживается на устройстве. Попробуйте другой ролик.");}});}
        else photoTask=worker.submit(()->{
            Bitmap original=null,processed=null;try{
                original=PhotoEngine.load(input);processed=PhotoEngine.process(original,processingLevel,sampling,seed,n->main.post(()->{if(token==generation)report(n,"Добавляем благородное мыло");}));
                try(OutputStream out=new FileOutputStream(output)){if(!processed.compress(processingLevel==12?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG,processingLevel==12?100:Levels.JPEG[processingLevel],out))throw new IOException("Сохранение");}
                main.post(()->{if(token==generation&&!destroying){result=output;setBusy(false,"Фото обрело душу · "+formatSize(output.length()));showResult(false);}else output.delete();});
            }catch(Exception|OutOfMemoryError e){output.delete();main.post(()->{if(token==generation)fail("Не удалось обработать фото. Попробуйте JPEG поменьше.");});}
            finally{if(original!=null)original.recycle();if(processed!=null)processed.recycle();}
        });
    }
    private void report(int n,String s){progress.setProgress(n);long elapsed=SystemClock.elapsedRealtime()-started;String eta=n>2?" · ещё ~"+Math.max(1,(elapsed*(100-n)/n)/1000)+" с":"";status.setText(s+" · "+n+"%"+eta);}
    private void setBusy(boolean value,String message){busy=value;status.setText(message);progress.setVisibility(value?View.VISIBLE:View.GONE);progress.setProgress(0);cancel.setVisibility(value?View.VISIBLE:View.GONE);shutter.setVisibility(value?View.GONE:View.VISIBLE);updateControls();}
    private void fail(String message){setBusy(false,message);if(result!=null)showResult(false);else bindCamera();}
    private void cancelWork(){if(!busy)return;++generation;if(photoTask!=null)photoTask.cancel(true);if(engine!=null)engine.cancel();setBusy(false,"Шакалы сделали паузу");if(result!=null)showResult(false);else bindCamera();}
    private void showResult(boolean original){
        if(result==null)return;showingOriginal=original;File f=original&&source!=null?source:result;cameraActions.setVisibility(View.GONE);resultActions.setVisibility(View.VISIBLE);before.setText(original?"Результат":"Оригинал");video.stopPlayback();if(displayed!=null){image.setImageDrawable(null);displayed.recycle();displayed=null;}
        if(resultVideo){image.setVisibility(View.GONE);video.setVisibility(View.VISIBLE);video.setVideoPath(f.getPath());video.start();}
        else{video.setVisibility(View.GONE);image.setVisibility(View.VISIBLE);try{displayed=PhotoEngine.load(f);image.setImageBitmap(displayed);}catch(Exception|OutOfMemoryError e){toast("Не удалось показать превью");}}
        hero.setText(original?"ОРИГИНАЛ":(resultLevel==12?"12/10 · Высокое искусство":resultLevel+"/10 · Шедевр пересылок"));updateControls();
    }
    private void returnCamera(){
        if(busy)return;video.stopPlayback();video.setVisibility(View.GONE);image.setVisibility(View.GONE);image.setImageDrawable(null);if(displayed!=null){displayed.recycle();displayed=null;}
        source=null;result=null;showingOriginal=false;cameraActions.setVisibility(View.VISIBLE);resultActions.setVisibility(View.GONE);hero.setText("Фото, которое обрело душу. Бережно. В телефоне.");bindCamera();
    }
    private void saveResult(){
        if(result==null||busy)return;if(Build.VERSION.SDK_INT<29&&!has(Manifest.permission.WRITE_EXTERNAL_STORAGE)){storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);return;}
        File f=result;boolean isVideo=resultVideo;String ext=isVideo?"mp4":f.getName().endsWith("png")?"png":"jpg";setBusy(true,"Сохраняем в галерею…");
        photoTask=worker.submit(()->{Uri uri=null;try{
            ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,"shakal_"+resultLevel+"_"+System.currentTimeMillis()+"."+ext);values.put(MediaStore.MediaColumns.MIME_TYPE,isVideo?"video/mp4":"image/"+(ext.equals("jpg")?"jpeg":ext));
            if(Build.VERSION.SDK_INT>=29){values.put(MediaStore.MediaColumns.RELATIVE_PATH,(isVideo?"Movies":"Pictures")+"/ShakalCamera");values.put(MediaStore.MediaColumns.IS_PENDING,1);}
            uri=getContentResolver().insert(isVideo?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("Галерея недоступна");
            try(InputStream in=new FileInputStream(f);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Не удалось записать");byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){PhotoEngine.check();out.write(b,0,n);}}
            if(Build.VERSION.SDK_INT>=29){ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(uri,ready,null,null);}
            main.post(()->{if(!destroying)setBusy(false,"Сохранено в галерею · ShakalCamera");});
        }catch(Exception e){if(uri!=null)getContentResolver().delete(uri,null,null);main.post(()->{if(!destroying)setBusy(false,"Не удалось сохранить. Проверьте свободное место.");});}});
    }
    private void shareResult(){if(result==null||busy)return;video.pause();Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",result);Intent i=new Intent(Intent.ACTION_SEND);i.setType(resultVideo?"video/mp4":result.getName().endsWith("png")?"image/png":"image/jpeg");i.putExtra(Intent.EXTRA_STREAM,uri);i.setClipData(ClipData.newRawUri("Шакальное искусство",uri));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Поделиться искусством"));}
    private String formatSize(long n){return String.format(Locale.ROOT,"%.1f МБ",n/1048576.0);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void liveFrame(){
        if(destroying)return;
        if(!liveFilter||busy||result!=null||camera==null||!getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)){main.postDelayed(this::liveFrame,700);return;}
        Bitmap raw=preview.getBitmap();if(raw==null){main.postDelayed(this::liveFrame,700);return;}
        int[] wh=Levels.fit(raw.getWidth(),raw.getHeight(),360);Bitmap small=PhotoEngine.scale(raw,wh[0],wh[1],true);raw.recycle();int l=level;String sampling=videoMode?"nearest":mode;int token=generation;
        try{worker.submit(()->{Bitmap frame=null;try{frame=PhotoEngine.process(small,l,sampling,seed,n->{});}catch(Exception|OutOfMemoryError ignored){}finally{small.recycle();}Bitmap output=frame;main.post(()->{
            if(output!=null){if(!destroying&&liveFilter&&!busy&&result==null&&token==generation){image.setImageBitmap(output);image.setVisibility(View.VISIBLE);if(liveBitmap!=null)liveBitmap.recycle();liveBitmap=output;}else output.recycle();}
            if(!destroying)main.postDelayed(this::liveFrame,600);
        });});}catch(RejectedExecutionException e){small.recycle();}
    }
    @Override protected void onStop(){super.onStop();if(recording!=null)recording.stop();video.pause();}
    @Override protected void onDestroy(){destroying=true;++generation;if(recording!=null)recording.close();if(engine!=null)engine.cancel();if(photoTask!=null)photoTask.cancel(true);worker.shutdownNow();main.removeCallbacksAndMessages(null);video.stopPlayback();image.setImageDrawable(null);if(displayed!=null)displayed.recycle();if(liveBitmap!=null)liveBitmap.recycle();super.onDestroy();}
}
