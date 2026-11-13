package space.shakalizator.camera;

import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.util.Random;

public final class PhotoEngine {
    public interface Progress { void update(int percent); }
    private PhotoEngine() {}
    public static Bitmap load(File file) throws IOException {
        BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getPath(),o);
        if(o.outWidth<=0)throw new IOException("Не удалось открыть фото");
        o.inSampleSize=1; while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>4096)o.inSampleSize*=2;
        o.inJustDecodeBounds=false; o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap b=BitmapFactory.decodeFile(file.getPath(),o); if(b==null)throw new IOException("Не удалось открыть фото");
        ExifInterface exif=new ExifInterface(file);
        Matrix m=new Matrix(); if(exif.isFlipped())m.postScale(-1,1); m.postRotate(exif.getRotationDegrees());
        if(!m.isIdentity()){Bitmap r=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(r!=b)b.recycle();b=r;}
        if(Math.max(b.getWidth(),b.getHeight())>2048){int[] wh=Levels.fit(b.getWidth(),b.getHeight(),2048);Bitmap r=scale(b,wh[0],wh[1],true);b.recycle();b=r;}
        return b;
    }
    static void check() throws InterruptedIOException { if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Отменено"); }
    static Bitmap scale(Bitmap b,int w,int h,boolean smooth) {
        Bitmap r=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(r);c.drawColor(Color.WHITE);
        Paint p=new Paint();p.setFilterBitmap(smooth);c.drawBitmap(b,null,new Rect(0,0,w,h),p);return r;
    }
    static Bitmap jpeg(Bitmap b,int q) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();if(!b.compress(Bitmap.CompressFormat.JPEG,q,out))throw new IOException("JPEG");
        byte[] bytes=out.toByteArray();Bitmap r=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        if(r==null)throw new IOException("JPEG decode");return r;
    }
    static Bitmap downscale(Bitmap b,int w,int h,String mode) throws IOException {
        check();
        if(mode.equals("nearest"))return scale(b,w,h,false);
        if(mode.equals("clean")) {
            Bitmap cur=b;
            while(cur.getWidth()/2>w && cur.getHeight()/2>h){check();Bitmap next=scale(cur,cur.getWidth()/2,cur.getHeight()/2,true);if(cur!=b)cur.recycle();cur=next;}
            Bitmap r=scale(cur,w,h,true);if(cur!=b)cur.recycle();return r;
        }
        int[] wh=Levels.fit(b.getWidth(),b.getHeight(),Math.min(10000,(int)Math.sqrt(4_000_000d*Math.max(b.getWidth(),b.getHeight())/Math.min(b.getWidth(),b.getHeight()))));
        Bitmap large=scale(b,wh[0],wh[1],true);try{return scale(large,w,h,true);}finally{large.recycle();}
    }
    public static Bitmap process(Bitmap source,int level,String mode,long seed,Progress progress) throws IOException {
        Levels.valid(level);check();int side=level==12?50:Levels.PHOTO_SIDE[level];
        int longer=Math.max(source.getWidth(),source.getHeight());if(level!=12 && longer<side)side=Math.max(2,(int)(longer*.8));
        int[] wh=Levels.fit(source.getWidth(),source.getHeight(),side);Bitmap cur=downscale(source,wh[0],wh[1],mode);
        try {
            progress.update(12);
            if(level==12){
                int[] mid=Levels.fit(source.getWidth(),source.getHeight(),100);Bitmap next=scale(cur,mid[0],mid[1],false);cur.recycle();cur=next;
                int[] px=new int[cur.getWidth()*cur.getHeight()];cur.getPixels(px,0,cur.getWidth(),0,0,cur.getWidth(),cur.getHeight());
                for(int i=0;i<px.length;i++){int c=px[i];px[i]=Color.rgb(((Color.red(c)*3+127)/255)*85,((Color.green(c)*3+127)/255)*85,((Color.blue(c)+127)/255)*255);}
                cur.setPixels(px,0,cur.getWidth(),0,0,cur.getWidth(),cur.getHeight());next=jpeg(cur,10);cur.recycle();cur=next;
            } else {
                if(level>=5){Bitmap next=Bitmap.createBitmap(cur.getWidth(),cur.getHeight(),Bitmap.Config.ARGB_8888);Paint p=new Paint();ColorMatrix matrix=new ColorMatrix();matrix.setSaturation(1+.05f*(level-4));p.setColorFilter(new ColorMatrixColorFilter(matrix));new Canvas(next).drawBitmap(cur,0,0,p);cur.recycle();cur=next;}
                for(int i=0;i<Levels.PASSES[level];i++){check();Bitmap next=jpeg(cur,Levels.JPEG[level]);cur.recycle();cur=next;progress.update(15+50*(i+1)/Levels.PASSES[level]);}
            }
            check();Bitmap full=scale(cur,source.getWidth(),source.getHeight(),level!=12);cur.recycle();cur=full;
            if(level!=12) {
                if(level>=5) sharpen(cur,level,seed);
                check();Bitmap next=jpeg(cur,Levels.JPEG[level]);cur.recycle();cur=next;
            }
            check();progress.update(100);Bitmap result=cur;cur=null;return result;
        } finally {if(cur!=null)cur.recycle();}
    }
    static void sharpen(Bitmap b,int level,long seed) throws IOException {
        int w=b.getWidth(),h=b.getHeight();int[] src=new int[w*h],row=new int[w];b.getPixels(src,0,w,0,0,w,h);
        Random random=new Random(seed);float a=.08f+.015f*(level-5);
        for(int y=0;y<h;y++){check();for(int x=0;x<w;x++){
            int center=src[y*w+x],l=src[y*w+Math.max(0,x-1)],r=src[y*w+Math.min(w-1,x+1)],t=src[Math.max(0,y-1)*w+x],d=src[Math.min(h-1,y+1)*w+x];int value=0xff000000;
            for(int shift=0;shift<=16;shift+=8){int v=(center>>shift)&255;float edge=4*v-((l>>shift)&255)-((r>>shift)&255)-((t>>shift)&255)-((d>>shift)&255);int noise=level>=9?random.nextInt(7)-3:0;int c=Math.max(0,Math.min(255,Math.round(v+a*edge)+noise));value|=c<<shift;}row[x]=value;
        }b.setPixels(row,0,w,0,y,w,1);}
    }
}
