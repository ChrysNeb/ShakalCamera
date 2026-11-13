package space.shakalizator.camera;

/** Small, deterministic luminance/flow estimator. No Android dependencies or full-size frame buffers. */
public final class FrameMotion {
    public static final int WIDTH=32, HEIGHT=24;
    public static final class Sample {
        public final float change, brightness, vertical;
        public final float[] rows;
        public final boolean cut;
        public Sample(float change,float brightness,float vertical,float[] rows,boolean cut){
            this.change=change;this.brightness=brightness;this.vertical=vertical;this.rows=rows;this.cut=cut;
        }
        public static Sample still(){return new Sample(0,.5f,0,new float[4],false);}
    }
    private byte[] previous;
    public void reset(){previous=null;}
    public Sample measure(byte[] pixels){
        if(pixels.length!=WIDTH*HEIGHT)throw new IllegalArgumentException("32x24 luminance required");
        float brightness=0,difference=0;int changed=0;
        for(int i=0;i<pixels.length;i++){
            int v=pixels[i]&255;brightness+=v;
            if(previous!=null){int d=Math.abs(v-(previous[i]&255));difference+=d;if(d>50)changed++;}
        }
        brightness/=pixels.length*255f;
        if(previous==null){previous=pixels.clone();return new Sample(0,brightness,0,new float[4],false);}
        difference/=pixels.length*255f;
        boolean cut=difference>.34f&&changed>pixels.length*.7f;
        float[] rows=new float[4];float vertical=0,totalWeight=0;
        if(!cut&&difference>.004f){
            for(int row=0;row<4;row++){
                float sumX=0,weightX=0;
                for(int column=0;column<4;column++){
                    float zero=cost(pixels,column,row,0,0),best=zero;int bx=0,by=0;
                    for(int dy=-2;dy<=2;dy++)for(int dx=-3;dx<=3;dx++){
                        float error=cost(pixels,column,row,dx,dy)+.35f*(Math.abs(dx)+Math.abs(dy));
                        if(error<best){best=error;bx=dx;by=dy;}
                    }
                    float weight=Math.max(0,zero-best-2);
                    if(weight>0){sumX+=bx*weight;weightX+=weight;vertical+=by*weight;totalWeight+=weight;}
                }
                rows[row]=weightX>0?sumX/(weightX*3):0;
            }
        }
        System.arraycopy(pixels,0,previous,0,pixels.length);
        return new Sample(Math.min(1,difference*5),brightness,totalWeight>0?vertical/(totalWeight*2):0,rows,cut);
    }
    private float cost(byte[] now,int column,int row,int dx,int dy){
        int left=Math.max(3,column*8),right=Math.min(WIDTH-3,column*8+8);
        int top=Math.max(2,row*6),bottom=Math.min(HEIGHT-2,row*6+6);int sum=0,count=0;
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x+=2){
            sum+=Math.abs((now[y*WIDTH+x]&255)-(previous[(y-dy)*WIDTH+x-dx]&255));count++;
        }
        return count==0?255:(float)sum/count;
    }
}
