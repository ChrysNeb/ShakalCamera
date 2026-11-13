package space.shakalizator.camera;

import android.content.Context;
import android.opengl.GLES20;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlProgram;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.effect.BaseGlShaderProgram;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;

/** Samples the centre of each low-resolution cell, retaining sharp 12/10 squares. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class PixelEffect implements GlEffect {
    final int width,height;final boolean palette;final float lag;
    public PixelEffect(int width,int height,boolean palette){this(width,height,palette,0);}
    public PixelEffect(int width,int height,boolean palette,float lag){this.width=width;this.height=height;this.palette=palette;this.lag=lag;}
    @Override public GlShaderProgram toGlShaderProgram(Context context,boolean useHdr) throws VideoFrameProcessingException {return new Shader(width,height,palette,lag);}
    static final class Shader extends BaseGlShaderProgram {
        private final GlProgram program;private int previous=-1,width,height;private boolean hasPrevious;private final float lag;
        Shader(int w,int h,boolean palette,float lag) throws VideoFrameProcessingException {
            super(false,1);
            this.lag=lag;
            try {
                program=new GlProgram("attribute vec4 aFramePosition; varying vec2 vUv; void main(){gl_Position=aFramePosition;vUv=(aFramePosition.xy+1.0)*0.5;}",
                    "precision highp float;uniform sampler2D uTexSampler;uniform sampler2D uPrevious;uniform vec2 uGrid;uniform float uPalette;uniform float uLag;uniform float uDrift;varying vec2 vUv;void main(){vec2 uv=(floor(vUv*uGrid)+0.5)/uGrid;vec3 c=texture2D(uTexSampler,uv).rgb;if(uPalette>0.5)c=floor(c*vec3(3.,1.,1.)+0.5)/vec3(3.,1.,1.);vec3 old=texture2D(uPrevious,clamp(uv+vec2(uDrift/uGrid.x,0.),0.,1.)).rgb;c=mix(c,old,uLag);gl_FragColor=vec4(c,1.);}");
                program.setBufferAttribute("aFramePosition",GlUtil.getNormalizedCoordinateBounds(),4);
                program.setFloatsUniform("uGrid",new float[]{w,h});program.setFloatUniform("uPalette",palette?1:0);
            }catch(GlUtil.GlException e){throw new VideoFrameProcessingException(e);}
        }
        @Override public Size configure(int inputWidth,int inputHeight){
            width=inputWidth;height=inputHeight;hasPrevious=false;
            if(previous!=-1)GLES20.glDeleteTextures(1,new int[]{previous},0);
            int[] texture=new int[1];GLES20.glGenTextures(1,texture,0);previous=texture[0];GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,previous);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,width,height,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
            return new Size(inputWidth,inputHeight);
        }
        @Override public void drawFrame(int inputTexId,long presentationTimeUs) throws VideoFrameProcessingException {
            try{program.use();program.setSamplerTexIdUniform("uTexSampler",inputTexId,0);program.setSamplerTexIdUniform("uPrevious",previous,1);program.setFloatUniform("uLag",hasPrevious?lag:0);program.setFloatUniform("uDrift",(float)Math.sin(presentationTimeUs/700000.0)*.8f);program.bindAttributesAndUniforms();GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                if(lag>0){GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,previous);GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,0,0,width,height);hasPrevious=true;}GlUtil.checkGlError();}
            catch(GlUtil.GlException e){throw new VideoFrameProcessingException(e,presentationTimeUs);}
        }
        @Override public void release() throws VideoFrameProcessingException {super.release();if(previous!=-1){GLES20.glDeleteTextures(1,new int[]{previous},0);previous=-1;}try{program.delete();}catch(GlUtil.GlException e){throw new VideoFrameProcessingException(e);}}
    }
}
