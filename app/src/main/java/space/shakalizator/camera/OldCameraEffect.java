package space.shakalizator.camera;

import android.content.Context;
import android.opengl.GLES20;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.effect.BaseGlShaderProgram;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;
import java.nio.ByteBuffer;

/** Real frame dropping (PTS preserved), scene-aware jitter, mild ghosting and simulated rolling shutter. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class OldCameraEffect implements GlEffect {
    private final int gridWidth,gridHeight,level;private final long seed;
    public OldCameraEffect(int width,int height,int level,long seed){gridWidth=width;gridHeight=height;this.level=level;this.seed=seed;}
    @Override public GlShaderProgram toGlShaderProgram(Context context,boolean hdr)throws VideoFrameProcessingException{return new Shader(gridWidth,gridHeight,level,seed);}
    static final class Shader extends BaseGlShaderProgram {
        private static final String VERTEX="attribute vec4 aFramePosition;varying vec2 vUv;void main(){gl_Position=aFramePosition;vUv=(aFramePosition.xy+1.)*.5;}";
        private final GlProgram draw,reader;private final CameraCadence cadence;private final FrameMotion motion=new FrameMotion();
        private final ByteBuffer rgba=ByteBuffer.allocateDirect(FrameMotion.WIDTH*FrameMotion.HEIGHT*4);
        private final byte[] luma=new byte[FrameMotion.WIDTH*FrameMotion.HEIGHT];private final int level;
        private FrameMotion.Sample sample=FrameMotion.Sample.still();private int history=-1,analysis=-1,fbo=-1,width,height;private boolean hasHistory;
        Shader(int w,int h,int level,long seed)throws VideoFrameProcessingException{
            super(false,1);this.level=level;cadence=new CameraCadence(level,seed);
            try{
                draw=new GlProgram(VERTEX,"precision highp float;varying vec2 vUv;uniform sampler2D uInput;uniform sampler2D uHistory;uniform vec2 uGrid;uniform vec4 uRows;uniform float uVertical;uniform float uMotion;uniform float uTime;uniform float uGhost;uniform float uPalette;void main(){float y=clamp(vUv.y*3.,0.,2.999);float row=y<1.?mix(uRows.x,uRows.y,y):y<2.?mix(uRows.y,uRows.z,y-1.):mix(uRows.z,uRows.w,y-2.);vec2 rolling=vec2(row*(vUv.y-.5)*.06,uVertical*(vUv.y-.5)*.025)*uMotion;rolling.x+=sin(vUv.y*13.+uTime*9.)*abs(row)*uMotion*.004;vec2 uv=clamp(vUv+rolling,0.,1.);uv=(floor(uv*uGrid)+.5)/uGrid;vec3 c=texture2D(uInput,uv).rgb;if(uPalette>.5)c=floor(c*vec3(3.,1.,1.)+.5)/vec3(3.,1.,1.);vec3 old=texture2D(uHistory,clamp(vUv+rolling*.25,0.,1.)).rgb;gl_FragColor=vec4(mix(c,old,uGhost),1.);}");
                reader=new GlProgram(VERTEX,"precision mediump float;varying vec2 vUv;uniform sampler2D uInput;void main(){gl_FragColor=texture2D(uInput,vUv);}");
                draw.setBufferAttribute("aFramePosition",GlUtil.getNormalizedCoordinateBounds(),4);reader.setBufferAttribute("aFramePosition",GlUtil.getNormalizedCoordinateBounds(),4);
                draw.setFloatsUniform("uGrid",new float[]{w,h});draw.setFloatUniform("uPalette",level==12?1:0);
            }catch(GlUtil.GlException e){throw new VideoFrameProcessingException(e);}
        }
        private int texture(int w,int h){int[] id=new int[1];GLES20.glGenTextures(1,id,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);return id[0];}
        private void analyse(int input)throws GlUtil.GlException{
            int[] priorFbo=new int[1],viewport=new int[4];GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING,priorFbo,0);GLES20.glGetIntegerv(GLES20.GL_VIEWPORT,viewport,0);
            try{
                if(analysis==-1){analysis=texture(FrameMotion.WIDTH,FrameMotion.HEIGHT);int[] id=new int[1];GLES20.glGenFramebuffers(1,id,0);fbo=id[0];GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,analysis,0);if(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)!=GLES20.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Motion framebuffer");}
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);GLES20.glViewport(0,0,FrameMotion.WIDTH,FrameMotion.HEIGHT);reader.use();reader.setSamplerTexIdUniform("uInput",input,0);reader.bindAttributesAndUniforms();GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                rgba.clear();GLES20.glReadPixels(0,0,FrameMotion.WIDTH,FrameMotion.HEIGHT,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,rgba);GlUtil.checkGlError();
                for(int i=0;i<luma.length;i++)luma[i]=(byte)(((rgba.get(i*4)&255)*77+(rgba.get(i*4+1)&255)*150+(rgba.get(i*4+2)&255)*29)>>8);
                sample=motion.measure(luma);if(sample.cut)hasHistory=false;
            }finally{GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,priorFbo[0]);GLES20.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);}
        }
        @Override public void queueInputFrame(GlObjectsProvider provider,GlTextureInfo input,long timeUs){
            try{analyse(input.texId);if(cadence.keep(timeUs,sample))super.queueInputFrame(provider,input,timeUs);else{getInputListener().onInputFrameProcessed(input);getInputListener().onReadyToAcceptInputFrame();}}
            catch(GlUtil.GlException|RuntimeException e){onError(e);}
        }
        @Override public Size configure(int w,int h){width=w;height=h;hasHistory=false;if(history!=-1)GLES20.glDeleteTextures(1,new int[]{history},0);history=texture(w,h);return new Size(w,h);}
        @Override public void drawFrame(int input,long timeUs)throws VideoFrameProcessingException{
            try{
                draw.use();draw.setSamplerTexIdUniform("uInput",input,0);draw.setSamplerTexIdUniform("uHistory",history,1);draw.setFloatsUniform("uRows",sample.rows);draw.setFloatUniform("uVertical",sample.vertical);
                draw.setFloatUniform("uMotion",sample.cut?0:Math.max(0,Math.min(1,(sample.change-.05f)*5)));draw.setFloatUniform("uTime",timeUs/1_000_000f);
                draw.setFloatUniform("uGhost",hasHistory?Math.min(.25f,.11f+(level==12?10:level)*.006f+sample.change*.07f):0);
                draw.bindAttributesAndUniforms();GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,history);GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,0,0,width,height);hasHistory=true;GlUtil.checkGlError();
            }catch(GlUtil.GlException e){throw new VideoFrameProcessingException(e,timeUs);}
        }
        @Override public void flush(){cadence.reset();motion.reset();hasHistory=false;super.flush();}
        @Override public void release()throws VideoFrameProcessingException{
            super.release();for(int t:new int[]{history,analysis})if(t!=-1)GLES20.glDeleteTextures(1,new int[]{t},0);if(fbo!=-1)GLES20.glDeleteFramebuffers(1,new int[]{fbo},0);history=analysis=fbo=-1;
            try{draw.delete();reader.delete();}catch(GlUtil.GlException e){throw new VideoFrameProcessingException(e);}
        }
    }
}
