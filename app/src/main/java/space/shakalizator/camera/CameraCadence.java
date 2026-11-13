package space.shakalizator.camera;

import java.util.Random;

/** VFR decisions based on source PTS, seeded timing, low light and measured frame changes. */
public final class CameraCadence {
    public enum Phase { NORMAL, DIP, FREEZE }
    private final long seed;private final int level;private Random random;
    private Phase phase;private long phaseEnd,nextFrame,lastKept,lastTime,lastContentEvent;private int dips;
    public CameraCadence(int level,long seed){this.level=Levels.valid(level);this.seed=seed;reset();}
    public void reset(){random=new Random(seed);phase=Phase.NORMAL;phaseEnd=-1;nextFrame=0;lastKept=-1;lastTime=-1;lastContentEvent=-4_000_000;dips=0;}
    public Phase phase(){return phase;}
    public boolean keep(long timeUs,FrameMotion.Sample frame){
        if(timeUs<0)throw new IllegalArgumentException("Negative PTS");
        if(lastTime>timeUs)reset();lastTime=timeUs;
        if(phaseEnd<0){phaseEnd=timeUs+normalDuration();nextFrame=timeUs;}
        // Content can start an extra dip, but cannot repeatedly pin a busy scene in a freeze.
        if(timeUs-lastContentEvent>2_000_000&&timeUs>900_000&&(frame.cut||frame.change>.25f)){
            lastContentEvent=timeUs;
            if(phase==Phase.NORMAL){
                phase=frame.cut&&random.nextFloat()<.4f?Phase.FREEZE:Phase.DIP;
                phaseEnd=timeUs+(phase==Phase.FREEZE?freezeDuration():450_000+random.nextInt(550_001));
            }
        }
        if(timeUs>=phaseEnd){
            if(phase==Phase.NORMAL){phase=Phase.DIP;phaseEnd=timeUs+500_000+random.nextInt(650_001);}
            else if(phase==Phase.DIP&&(++dips%2==0||random.nextFloat()<.6f)){phase=Phase.FREEZE;phaseEnd=timeUs+freezeDuration();}
            else {phase=Phase.NORMAL;phaseEnd=timeUs+normalDuration();}
        }
        if(lastKept<0){lastKept=timeUs;nextFrame=timeUs+interval(frame);return true;}
        if(phase==Phase.FREEZE)return false;
        // Strict 50 ms minimum between output PTS: never more than 20 fresh frames/s.
        if(timeUs<nextFrame||timeUs-lastKept<50_000)return false;
        lastKept=timeUs;nextFrame=timeUs+interval(frame);return true;
    }
    private long normalDuration(){return 1_650_000+random.nextInt(1_750_001);}
    private long freezeDuration(){return 430_000+random.nextInt(140_001);}
    private long interval(FrameMotion.Sample frame){
        int l=level==12?10:level;
        double normal=level==12?8:20-(l-1)*.35;
        double fps=phase==Phase.DIP?(level==12?6:10):normal;
        // Darkness and motion change individual intervals as well as triggering whole phases.
        double strain=1+Math.max(0,.28-frame.brightness)*.5+Math.min(.15,frame.change*.25);
        return Math.max(phase==Phase.DIP?100_000:50_000,(long)((1_000_000/fps)*(.88+random.nextDouble()*.30)*strain));
    }
}
