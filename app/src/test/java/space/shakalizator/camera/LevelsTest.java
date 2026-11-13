package space.shakalizator.camera;
import org.junit.Test;
import static org.junit.Assert.*;
public class LevelsTest {
    @Test public void parametersBecomeStronger(){
        for(int i=2;i<=10;i++){assertTrue(Levels.PHOTO_SIDE[i]<Levels.PHOTO_SIDE[i-1]);assertTrue(Levels.JPEG[i]<Levels.JPEG[i-1]);assertTrue(Levels.PASSES[i]>=Levels.PASSES[i-1]);assertTrue(Levels.BITRATE[i]<Levels.BITRATE[i-1]);assertTrue(Levels.FPS[i]<=Levels.FPS[i-1]);}
    }
    @Test public void fitsKeepRatio(){for(int[] v:new int[][]{{4000,3000},{4032,3024},{3000,4000},{200,200},{1,4000}}){int[] r=Levels.fit(v[0],v[1],2048);assertEquals(2048,Math.max(r[0],r[1]));assertTrue(r[0]>0&&r[1]>0);}}
    @Test public void codecDimensionsEven(){for(int side:Levels.VIDEO_SIDE)if(side>0){int[] d=Levels.evenFit(4032,3024,side);assertEquals(0,d[0]%2);assertEquals(0,d[1]%2);}}
    @Test public void specialModeAccepted(){assertEquals(12,Levels.valid(12));}
    @Test(expected=IllegalArgumentException.class) public void invalidLevelRejected(){Levels.valid(11);}
}
