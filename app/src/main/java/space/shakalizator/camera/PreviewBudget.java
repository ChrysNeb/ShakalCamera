package space.shakalizator.camera;

/** Live-view pacing only. Exported video has its own artistic VFR schedule. */
public final class PreviewBudget {
    private int fps=30,slow;private double costMs;private long nextNs=-1,lastNs=-1,lastChangeNs;
    public int fps(){return fps;}
    public boolean accept(long timestampNs){
        if(lastNs>timestampNs){nextNs=-1;lastChangeNs=timestampNs;}lastNs=timestampNs;
        long period=1_000_000_000L/fps;
        if(nextNs>=0&&timestampNs+2_000_000L<nextNs)return false;
        nextNs=nextNs<0||timestampNs-nextNs>period*2?timestampNs+period:nextNs+period;return true;
    }
    public void record(long elapsedNs,long timestampNs){
        double ms=elapsedNs/1_000_000d;costMs=costMs==0?ms:costMs*.9+ms*.1;
        if(costMs>42)slow++;else slow=Math.max(0,slow-1);
        if(slow>=10&&timestampNs-lastChangeNs>1_000_000_000L){fps=fps==30?20:15;lastChangeNs=timestampNs;slow=0;nextNs=-1;}
        if(fps<30&&costMs<24&&timestampNs-lastChangeNs>4_000_000_000L){fps=fps==15?20:30;lastChangeNs=timestampNs;nextNs=-1;}
    }
}
