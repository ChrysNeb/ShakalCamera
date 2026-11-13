package space.shakalizator.camera;

import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import androidx.camera.core.*;
import androidx.core.util.Consumer;
import java.nio.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/** Camera -> external GL texture -> PreviewView surface. Never downloads pixels to CPU. */
public final class GpuPreview implements SurfaceProcessor,AutoCloseable {
    private static final String VERTEX="attribute vec2 aPosition;varying vec2 vUv;void main(){gl_Position=vec4(aPosition,0.,1.);vUv=(aPosition+1.)*.5;}";
    private static final String FRAGMENT="#extension GL_OES_EGL_image_external : require\nprecision mediump float;uniform samplerExternalOES uCamera;uniform mat4 uTransform;uniform vec2 uGrid;uniform float uLevel;uniform float uEnabled;uniform float uPixel;varying vec2 vUv;vec3 sampleAt(vec2 p){return texture2D(uCamera,(uTransform*vec4(clamp(p,0.,1.),0.,1.)).xy).rgb;}void main(){if(uEnabled<.5){gl_FragColor=vec4(sampleAt(vUv),1.);return;}vec2 cell=vUv*uGrid-.5;vec2 base=(floor(cell)+.5)/uGrid;vec3 c;if(uPixel>.5){c=sampleAt((floor(vUv*uGrid)+.5)/uGrid);c=floor(c*vec3(3.,3.,1.)+.5)/vec3(3.,3.,1.);}else{vec2 f=fract(cell);vec2 d=1./uGrid;c=mix(mix(sampleAt(base),sampleAt(base+vec2(d.x,0.)),f.x),mix(sampleAt(base+vec2(0.,d.y)),sampleAt(base+d),f.x),f.y);float gray=dot(c,vec3(.299,.587,.114));c=mix(vec3(gray),c,1.+max(0.,uLevel-4.)*.05);float steps=100.-uLevel*7.;c=floor(c*steps+.5)/steps;}gl_FragColor=vec4(clamp(c,0.,1.),1.);}";
    private final HandlerThread thread=new HandlerThread("ShakalPreviewGL");private final Handler gl;private final Executor executor;
    private final Consumer<Throwable> errors;private final List<Input> inputs=new ArrayList<>();private final Map<SurfaceOutput,EGLSurface> outputs=new LinkedHashMap<>();
    private final PreviewBudget budget=new PreviewBudget();private final float[] sourceMatrix=new float[16],matrix=new float[16];private final FloatBuffer vertices;
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;private EGLContext context=EGL14.EGL_NO_CONTEXT;private EGLSurface parking=EGL14.EGL_NO_SURFACE;private EGLConfig config;private int program;
    private int positionLocation,transformLocation,cameraLocation,gridLocation,levelLocation,enabledLocation,pixelLocation;
    private volatile boolean closed,failed;private boolean initialized;private volatile Settings settings=new Settings(6,true);public final AtomicLong rendered=new AtomicLong();
    public volatile int targetFps=30;public volatile double lastDrawMs;
    private static final class Settings{final int level;final boolean enabled;Settings(int l,boolean e){level=l;enabled=e;}}
    private static final class Input{SurfaceTexture texture;Surface surface;int tex;boolean released,queued;long timestamp=-1;}
    public GpuPreview(Consumer<Throwable> errors){this.errors=errors;vertices=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();vertices.put(new float[]{-1,-1,1,-1,-1,1,1,1}).position(0);thread.start();gl=new Handler(thread.getLooper());executor=r->{if(!gl.post(r))r.run();};gl.post(()->{try{init();}catch(RuntimeException e){fail(e);}});}
    public CameraEffect effect(){return new CameraEffect(CameraEffect.PREVIEW,executor,this,errors){};}
    public void settings(int level,boolean enabled){settings=new Settings(level,enabled);}
    @Override public void onInputSurface(SurfaceRequest request){
        if(closed||failed||!initialized){request.willNotProvideSurface();return;}
        try{
            current(parking);Input in=new Input();int[] texture=new int[1];GLES20.glGenTextures(1,texture,0);in.tex=texture[0];GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,in.tex);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            in.texture=new SurfaceTexture(in.tex);in.texture.setDefaultBufferSize(request.getResolution().getWidth(),request.getResolution().getHeight());in.surface=new Surface(in.texture);inputs.add(in);
            in.texture.setOnFrameAvailableListener(st->{if(in.released||closed||in.queued)return;in.queued=true;gl.post(()->{in.queued=false;if(!in.released&&!closed)draw(in);});},gl);
            request.provideSurface(in.surface,executor,result->{current(parking);in.released=true;in.texture.setOnFrameAvailableListener(null);in.surface.release();in.texture.release();GLES20.glDeleteTextures(1,new int[]{in.tex},0);inputs.remove(in);finishIfReady();});
        }catch(RuntimeException e){request.willNotProvideSurface();fail(e);}
    }
    @Override public void onOutputSurface(SurfaceOutput output){
        if(closed||failed||!initialized){output.close();return;}
        try{Surface surface=output.getSurface(executor,event->remove(event.getSurfaceOutput()));EGLSurface egl=EGL14.eglCreateWindowSurface(display,config,surface,new int[]{EGL14.EGL_NONE},0);if(egl==null||egl==EGL14.EGL_NO_SURFACE)throw new IllegalStateException("Preview EGL surface");outputs.put(output,egl);}
        catch(RuntimeException e){output.close();fail(e);}
    }
    private void draw(Input input){
        try{
            current(parking);input.texture.updateTexImage();long timestamp=input.texture.getTimestamp();if(timestamp==input.timestamp)return;input.timestamp=timestamp;
            if(outputs.isEmpty()||!budget.accept(timestamp))return;long started=System.nanoTime();input.texture.getTransformMatrix(sourceMatrix);Settings s=settings;
            for(Map.Entry<SurfaceOutput,EGLSurface> item:outputs.entrySet()){
                SurfaceOutput output=item.getKey();current(item.getValue());android.util.Size size=output.getSize();GLES20.glViewport(0,0,size.getWidth(),size.getHeight());output.updateTransformMatrix(matrix,sourceMatrix);
                GLES20.glUseProgram(program);vertices.position(0);GLES20.glEnableVertexAttribArray(positionLocation);GLES20.glVertexAttribPointer(positionLocation,2,GLES20.GL_FLOAT,false,0,vertices);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,input.tex);GLES20.glUniform1i(cameraLocation,0);
                GLES20.glUniformMatrix4fv(transformLocation,1,false,matrix,0);
                int n=s.level==12?50:Levels.PHOTO_SIDE[s.level];float scale=n/(float)Math.max(size.getWidth(),size.getHeight());
                GLES20.glUniform2f(gridLocation,Math.max(1,size.getWidth()*scale),Math.max(1,size.getHeight()*scale));GLES20.glUniform1f(levelLocation,s.level);GLES20.glUniform1f(enabledLocation,s.enabled?1:0);GLES20.glUniform1f(pixelLocation,s.level==12?1:0);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);if(!EGL14.eglSwapBuffers(display,item.getValue()))throw new IllegalStateException("Preview EGL swap");
            }
            long elapsed=System.nanoTime()-started;lastDrawMs=elapsed/1_000_000d;budget.record(elapsed,timestamp);targetFps=budget.fps();rendered.incrementAndGet();
        }catch(RuntimeException e){fail(e);}
    }
    private void init(){
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);if(display==EGL14.EGL_NO_DISPLAY)throw new IllegalStateException("Preview display");int[] v=new int[2];if(!EGL14.eglInitialize(display,v,0,v,1))throw new IllegalStateException("Preview EGL init");
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];int[] attributes={EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT|EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE};
        if(!EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0)||count[0]==0)throw new IllegalStateException("Preview config");config=configs[0];context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);if(context==EGL14.EGL_NO_CONTEXT)throw new IllegalStateException("Preview context");
        parking=EGL14.eglCreatePbufferSurface(display,config,new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);current(parking);int vertex=shader(GLES20.GL_VERTEX_SHADER,VERTEX),fragment=shader(GLES20.GL_FRAGMENT_SHADER,FRAGMENT);program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        positionLocation=GLES20.glGetAttribLocation(program,"aPosition");cameraLocation=GLES20.glGetUniformLocation(program,"uCamera");transformLocation=GLES20.glGetUniformLocation(program,"uTransform");gridLocation=GLES20.glGetUniformLocation(program,"uGrid");levelLocation=GLES20.glGetUniformLocation(program,"uLevel");enabledLocation=GLES20.glGetUniformLocation(program,"uEnabled");pixelLocation=GLES20.glGetUniformLocation(program,"uPixel");initialized=true;
    }
    private int shader(int type,String source){int shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String message=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);throw new IllegalStateException(message);}return shader;}
    private void current(EGLSurface surface){if(!EGL14.eglMakeCurrent(display,surface,surface,context))throw new IllegalStateException("Preview EGL current");}
    private void remove(SurfaceOutput output){EGLSurface surface=outputs.remove(output);if(surface!=null){current(parking);EGL14.eglDestroySurface(display,surface);}output.close();}
    private void fail(Throwable error){if(!failed&&!closed){failed=true;errors.accept(error);}}
    @Override public void close(){closed=true;gl.post(()->{for(SurfaceOutput output:new ArrayList<>(outputs.keySet()))remove(output);finishIfReady();});}
    private void finishIfReady(){
        if(!closed||!inputs.isEmpty())return;
        if(display!=EGL14.EGL_NO_DISPLAY){if(context!=EGL14.EGL_NO_CONTEXT&&parking!=EGL14.EGL_NO_SURFACE){current(parking);if(program!=0)GLES20.glDeleteProgram(program);}EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(parking!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,parking);if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);EGL14.eglReleaseThread();display=EGL14.EGL_NO_DISPLAY;}thread.quitSafely();
    }
}
