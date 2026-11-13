package space.shakalizator.camera;

/** Constants ported from shakal-pic and shakal-vid. Android codecs use bitrate, not MPEG4 Q. */
public final class Levels {
    private Levels() {}
    public static final int[] PHOTO_SIDE = {0,800,600,450,320,240,180,140,120,110,100};
    public static final int[] JPEG = {0,70,55,45,35,25,18,12,8,5,3};
    public static final int[] PASSES = {0,1,1,2,2,3,3,4,5,6,8};
    public static final int[] VIDEO_SIDE = {0,360,288,240,200,160,144,120,100,90,72};
    public static final int[] FPS = {0,30,30,25,24,20,15,15,12,10,8};
    public static final int[] BITRATE = {0,400000,250000,160000,110000,80000,60000,45000,32000,24000,16000};
    public static final int[] AUDIO_BITS = {0,16,16,16,16,16,10,8,6,5,4};
    public static final int[] AUDIO_BITRATE = {0,48000,32000,24000,20000,16000,12000,10000,8000,8000,6000};
    public static final int[] LP = {0,8000,7000,6000,5000,4000,3500,3000,3000,2500,2500};
    public static int valid(int n) { if(n!=12 && (n<1 || n>10)) throw new IllegalArgumentException("level"); return n; }
    public static int[] fit(int w,int h,int side) {
        double s=(double)side/Math.max(w,h); return new int[]{Math.max(1,(int)Math.round(w*s)),Math.max(1,(int)Math.round(h*s))};
    }
    public static int[] evenFit(int w,int h,int side) {
        int[] v=fit(w,h,side); for(int i=0;i<2;i++)v[i]=Math.max(2,(v[i]/2)*2); return v;
    }
}
